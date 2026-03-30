package io.github.mralex1810.fantasy.polemica

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
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.ZoneId

@Service
class DefaultGameSyncService(
    private val polemicaProperties: PolemicaProperties,
    private val integration: PolemicaIntegrationService,
    private val seriesRepository: SeriesRepository,
    private val seriesPlayerRepository: SeriesPlayerRepository,
    private val seriesGameRepository: SeriesGameRepository,
) : GameSyncService {

    @Transactional
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
        when (tournament.kind) {
            TournamentKind.STANDALONE -> syncStandalone(seriesId, series)
            TournamentKind.POLEMICA_COMPETITION -> {
                val cid = tournament.polemicaCompetitionId
                    ?: throw ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Tournament polemica_competition_id is missing for POLEMICA_COMPETITION",
                    )
                syncCompetition(seriesId, series, cid)
            }
        }
    }

    private fun syncStandalone(seriesId: Long, series: Series) {
        val prefix = series.namePrefix?.trim().orEmpty()
        if (prefix.isEmpty()) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Series name_prefix is required for STANDALONE tournaments",
            )
        }
        val players = seriesPlayerRepository.findAllBySeries_Id(seriesId)
        if (players.isEmpty()) return

        val idSets = players.map { sp ->
            val uid = sp.tournamentPlayer!!.fantasyPlayer!!.polemicaUserId
            integration.fetchRecentProfileRowsForSync(uid).map { it.id }.toSet()
        }
        val matchIds = LinkedHashSet(idSets.first())
        for (i in 1 until idSets.size) {
            matchIds.retainAll(idSets[i])
        }

        val loaded = mutableMapOf<Long, PolemicaGame>()
        for (mid in matchIds) {
            val game = loaded.getOrPut(mid) { integration.loadMatch(mid) }
            val name = game.name?.trim() ?: ""
            if (!name.startsWith(prefix)) continue
            upsertSeriesGame(series, seriesId, game, name)
        }
    }

    private fun syncCompetition(
        seriesId: Long,
        series: Series,
        competitionId: Long,
    ) {
        val from = series.gameNumFrom
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Series game_num_from is required for POLEMICA_COMPETITION")
        val to = series.gameNumTo
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Series game_num_to is required for POLEMICA_COMPETITION")
        if (from > to) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "game_num_from must be <= game_num_to")
        }
        val refs = integration.listCompetitionGameReferences(competitionId)
        for (ref in refs) {
            if (ref.num < from || ref.num > to) continue
            val game = integration.loadGameFromCompetition(competitionId, ref.id, ref.version)
            val name = game.name?.trim() ?: ""
            upsertSeriesGame(series, seriesId, game, name)
        }
    }

    private fun upsertSeriesGame(
        series: Series,
        seriesId: Long,
        game: PolemicaGame,
        name: String,
    ) {
        val gid = game.id ?: return
        val json = integration.toJsonNode(game)
        val playedAt = game.started.atZone(ZoneId.systemDefault()).toInstant()

        val existing = seriesGameRepository.findBySeries_IdAndPolemicaGameId(seriesId, gid)
        if (existing != null) {
            existing.gameName = name.ifEmpty { "(no name)" }
            existing.gameDataCache = json
            existing.playedAt = playedAt
            existing.scored = false
            seriesGameRepository.save(existing)
        } else {
            seriesGameRepository.save(
                SeriesGame(
                    series = series,
                    polemicaGameId = gid,
                    gameName = name.ifEmpty { "(no name)" },
                    gameDataCache = json,
                    scored = false,
                    playedAt = playedAt,
                ),
            )
        }
    }
}
