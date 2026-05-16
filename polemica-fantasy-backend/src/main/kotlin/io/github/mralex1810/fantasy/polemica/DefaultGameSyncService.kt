package io.github.mralex1810.fantasy.polemica

import com.fasterxml.jackson.databind.JsonNode
import com.github.mafia.vyasma.polemica.library.model.game.PolemicaGame
import io.github.mralex1810.fantasy.config.PolemicaProperties
import io.github.mralex1810.fantasy.entity.Series
import io.github.mralex1810.fantasy.entity.SeriesGame
import io.github.mralex1810.fantasy.entity.TournamentKind
import io.github.mralex1810.fantasy.repository.SeriesGameRepository
import io.github.mralex1810.fantasy.repository.SeriesPlayerRepository
import io.github.mralex1810.fantasy.repository.SeriesRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.time.ZoneId

@Service
class DefaultGameSyncService(
    private val polemicaProperties: PolemicaProperties,
    private val integration: PolemicaIntegrationService,
    private val seriesRepository: SeriesRepository,
    private val seriesPlayerRepository: SeriesPlayerRepository,
    private val seriesGameRepository: SeriesGameRepository,
    platformTransactionManager: PlatformTransactionManager,
) : GameSyncService {

    private val transactionTemplate = TransactionTemplate(platformTransactionManager)

    /**
     * Polemica HTTP calls run outside a DB transaction; only persistence is wrapped in a short transaction.
     */
    override fun syncGames(seriesId: Long) {
        if (polemicaProperties.username.isBlank() || polemicaProperties.password.isBlank()) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Polemica API credentials are not configured (POLEMICA_USERNAME / POLEMICA_PASSWORD)",
            )
        }
        val series = seriesRepository.findById(seriesId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Series $seriesId not found")
        }
        val tournament = series.tournament
            ?: throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Series has no tournament")
        val prepared = when (tournament.kind) {
            TournamentKind.STANDALONE -> fetchStandalonePrepared(seriesId, series)
            TournamentKind.POLEMICA_COMPETITION -> {
                val cid = tournament.polemicaCompetitionId
                    ?: throw ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Tournament polemica_competition_id is missing for POLEMICA_COMPETITION",
                    )
                fetchCompetitionPrepared(seriesId, series, cid)
            }
        }
        if (prepared.isEmpty()) return
        transactionTemplate.executeWithoutResult {
            persistPreparedGames(seriesId, prepared)
        }
    }

    private fun fetchStandalonePrepared(seriesId: Long, series: Series): List<PreparedSeriesGame> {
        val prefix = series.namePrefix?.trim().orEmpty()
        val startedOnFilter = series.gameStartedOn
        if (prefix.isEmpty()) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Series name_prefix is required for STANDALONE tournaments",
            )
        }
        val players = seriesPlayerRepository.findAllBySeries_Id(seriesId)
        if (players.isEmpty()) return emptyList()

        val idSets = players.map { sp ->
            val uid = sp.tournamentPlayer!!.fantasyPlayer!!.polemicaUserId
            integration.fetchRecentProfileRowsForSync(uid).map { it.id }.toSet()
        }
        val freq = mutableMapOf<Long, Int>()
        for (set in idSets) {
            for (id in set) {
                freq[id] = (freq[id] ?: 0) + 1
            }
        }
        val threshold = minOf(STANDALONE_MIN_PLAYERS_IN_PROFILE_OVERLAP, idSets.size)
        val matchIds = freq.filter { it.value >= threshold }.keys.sorted()

        val loaded = mutableMapOf<Long, PolemicaGame>()
        val result = mutableListOf<PreparedSeriesGame>()
        for (mid in matchIds) {
            val game = loaded.getOrPut(mid) { integration.loadMatch(mid) }
            val name = game.name?.trim() ?: ""
            if (!name.startsWith(prefix)) continue
            if (startedOnFilter != null && game.started.toLocalDate() != startedOnFilter) continue
            val gid = game.id ?: continue
            result.add(
                PreparedSeriesGame(
                    polemicaGameId = gid,
                    resolvedName = resolvedStoredGameName(game, name),
                    gameDataJson = integration.toJsonNode(game),
                    playedAt = game.started.atZone(ZoneId.systemDefault()).toInstant(),
                ),
            )
        }
        return result
    }

    private fun fetchCompetitionPrepared(
        seriesId: Long,
        series: Series,
        competitionId: Long,
    ): List<PreparedSeriesGame> {
        val from = series.gameNumFrom
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Series game_num_from is required for POLEMICA_COMPETITION")
        val to = series.gameNumTo
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Series game_num_to is required for POLEMICA_COMPETITION")
        if (from > to) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "game_num_from must be <= game_num_to")
        }
        val phaseFilter = series.gamePhase
        val refs = integration.listCompetitionGameReferences(competitionId)
        val result = mutableListOf<PreparedSeriesGame>()
        for (ref in refs) {
            if (ref.num < from || ref.num > to) continue
            if (!matchesSeriesPhaseFilter(phaseFilter, ref.phase.toInt())) continue
            val game = integration.loadGameFromCompetition(competitionId, ref.id, ref.version)
            if (!matchesSeriesPhaseFilter(phaseFilter, game.phase)) continue
            val name = game.name?.trim() ?: ""
            val gid = game.id ?: continue
            result.add(
                PreparedSeriesGame(
                    polemicaGameId = gid,
                    resolvedName = resolvedStoredGameName(game, name),
                    gameDataJson = integration.toJsonNode(game),
                    playedAt = game.started.atZone(ZoneId.systemDefault()).toInstant(),
                ),
            )
        }
        return result
    }

    private fun persistPreparedGames(seriesId: Long, prepared: List<PreparedSeriesGame>) {
        val series = seriesRepository.findById(seriesId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Series $seriesId not found")
        }
        for (p in prepared) {
            val existing = seriesGameRepository.findBySeries_IdAndPolemicaGameId(seriesId, p.polemicaGameId)
            if (existing != null) {
                existing.gameName = p.resolvedName
                existing.gameDataCache = p.gameDataJson
                existing.playedAt = p.playedAt
                existing.scored = false
                seriesGameRepository.save(existing)
            } else {
                seriesGameRepository.save(
                    SeriesGame(
                        series = series,
                        polemicaGameId = p.polemicaGameId,
                        gameName = p.resolvedName,
                        gameDataCache = p.gameDataJson,
                        scored = false,
                        playedAt = p.playedAt,
                    ),
                )
            }
        }
    }

    private data class PreparedSeriesGame(
        val polemicaGameId: Long,
        val resolvedName: String,
        val gameDataJson: JsonNode,
        val playedAt: Instant,
    )

    private fun resolvedStoredGameName(game: PolemicaGame, name: String): String {
        val trimmed = name.trim()
        if (trimmed.isNotEmpty()) return trimmed
        val n = game.num
        return if (n != null) "Игра $n" else "Игра #${game.id}"
    }

    private fun matchesSeriesPhaseFilter(seriesPhaseFilter: Int?, gamePhase: Int?): Boolean =
        seriesPhaseFilter == null || gamePhase == seriesPhaseFilter

    private companion object {
        /**
         * STANDALONE sync: a profile match id is a candidate if it appears in at least this many
         * players' recent profile pages (capped by roster size), then [Series.namePrefix] filters.
         */
        private const val STANDALONE_MIN_PLAYERS_IN_PROFILE_OVERLAP = 8
    }
}
