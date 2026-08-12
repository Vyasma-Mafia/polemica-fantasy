package io.github.mralex1810.fantasy.scoring

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.mafia.vyasma.polemica.library.client.GamePointsService
import com.github.mafia.vyasma.polemica.library.model.game.PolemicaGame
import com.github.mafia.vyasma.polemica.library.model.game.PolemicaPlayer
import io.github.mralex1810.fantasy.entity.FantasyTeamCard
import io.github.mralex1810.fantasy.entity.FantasyTeamCardGamePerk
import io.github.mralex1810.fantasy.entity.FantasyTeamCardGameScore
import io.github.mralex1810.fantasy.entity.Series
import io.github.mralex1810.fantasy.entity.SeriesGame
import io.github.mralex1810.fantasy.observability.FantasyMetrics
import io.github.mralex1810.fantasy.observability.FantasyMetrics.OperationResult
import io.github.mralex1810.fantasy.repository.FantasyTeamRepository
import io.github.mralex1810.fantasy.repository.FantasyPlayerAliasRepository
import io.github.mralex1810.fantasy.repository.SeriesGameRepository
import io.github.mralex1810.fantasy.repository.SeriesPlayerRepository
import io.github.mralex1810.fantasy.repository.SeriesRepository
import io.github.mralex1810.fantasy.service.SeriesScoringContextFingerprintService
import io.github.mralex1810.fantasy.service.SeriesGameSelectorFingerprintService
import io.github.mralex1810.fantasy.scoring.perk.PerkDetectorRegistry
import io.github.mralex1810.fantasy.scoring.perk.ScoringContext
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.server.ResponseStatusException
import java.security.MessageDigest
import java.time.Instant

@Service
class DefaultScoringService(
    private val seriesRepository: SeriesRepository,
    private val seriesGameRepository: SeriesGameRepository,
    private val fantasyTeamRepository: FantasyTeamRepository,
    private val seriesPlayerRepository: SeriesPlayerRepository,
    private val fantasyPlayerAliasRepository: FantasyPlayerAliasRepository,
    private val perkRegistry: PerkDetectorRegistry,
    private val objectMapper: ObjectMapper,
    private val gamePointsService: GamePointsService,
    private val fantasyMetrics: FantasyMetrics,
    private val scoringContextFingerprintService: SeriesScoringContextFingerprintService,
    private val selectorFingerprintService: SeriesGameSelectorFingerprintService,
    platformTransactionManager: PlatformTransactionManager,
) : ScoringService {

    private val transactionTemplate = TransactionTemplate(platformTransactionManager)

    /**
     * Fetches points from Polemica public HTTP API outside a DB transaction; persistence and scoring run in a short transaction.
     */
    override fun calculateScores(seriesId: Long) {
        val sample = fantasyMetrics.start()
        try {
            val series = seriesRepository.findById(seriesId).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Series $seriesId not found")
            }
            rejectFinalized(series)
            val selectorChecksum = selectorFingerprintService.fingerprint(seriesId)
            if (series.lastSyncedSelectorChecksum != selectorChecksum) {
                throw ResponseStatusException(HttpStatus.CONFLICT, "Series game selector changed since the last successful sync")
            }
            val rows = seriesGameRepository.findAllBySeries_Id(seriesId)
            val gamesForPoints = gamesFinishedForScoring(rows)
            val scoringContextChecksum = scoringContextFingerprintService.fingerprint(seriesId)
            val scoringInputs = loadScoringInputs(gamesForPoints, scoringContextChecksum)
            val invalid = scoringInputs.values.firstOrNull { it.status != "COMPLETE" }
            if (invalid != null) {
                transactionTemplate.executeWithoutResult { persistIncompleteMetadata(seriesId, scoringInputs) }
                throw ResponseStatusException(HttpStatus.CONFLICT, invalid.error ?: "Public game points are incomplete")
            }
            val pointsByTablePositionByGameId = scoringInputs.mapValues { it.value.points }

            transactionTemplate.executeWithoutResult {
                applyScoresInTransaction(seriesId, pointsByTablePositionByGameId, scoringInputs, scoringContextChecksum, selectorChecksum)
            }
            fantasyMetrics.recordScoring(sample, OperationResult.SUCCESS, gamesForPoints.size)
        } catch (e: Exception) {
            fantasyMetrics.recordScoring(sample, OperationResult.ERROR)
            throw e
        }
    }

    private fun applyScoresInTransaction(
        seriesId: Long,
        pointsByTablePositionByGameId: Map<Long, Map<Int, Double>>,
        scoringInputs: Map<Long, ScoringInput>,
        scoringContextChecksum: String,
        selectorChecksum: String,
    ) {
        val series = seriesRepository.findByIdForUpdate(seriesId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Series $seriesId not found")
        rejectFinalized(series)
        if (series.lastSyncedSelectorChecksum != selectorChecksum || selectorFingerprintService.fingerprint(seriesId) != selectorChecksum) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Series game selector changed while scoring")
        }
        if (scoringContextFingerprintService.fingerprint(seriesId) != scoringContextChecksum) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Series roster or fantasy teams changed while scoring")
        }
        val games = gamesFinishedForScoring(seriesGameRepository.findAllBySeries_Id(seriesId))
        for (game in games) {
            val input = scoringInputs[game.polemicaGameId]
                ?: throw ResponseStatusException(HttpStatus.CONFLICT, "Scoring input is missing for game ${game.polemicaGameId}")
            val cacheChecksum = sha256(game.gameDataCache!!.toString())
            if (cacheChecksum != input.cacheChecksum) {
                throw ResponseStatusException(HttpStatus.CONFLICT, "Game cache changed while scoring")
            }
        }
        val teams = fantasyTeamRepository.findAllWithCardsForScoring(seriesId)
        val replacementByFantasyPlayerId = replacementByFantasyPlayerId(seriesId)

        for (team in teams) {
            var teamTotal = 0.0
            for (card in team.cards) {
                card.gameScores.clear()
                fantasyTeamRepository.flush()
                val score = scoreCardForSeries(
                    games = games,
                    fantasyCard = card,
                    pointsByTablePositionByGameId = pointsByTablePositionByGameId,
                    replacementByFantasyPlayerId = replacementByFantasyPlayerId,
                )
                card.score = score
                teamTotal += score
            }
            team.totalScore = teamTotal
        }

        for (g in games) {
            g.scored = true
            val input = scoringInputs.getValue(g.polemicaGameId)
            g.pointsStatus = "COMPLETE"
            g.scoringInputChecksum = input.inputChecksum
            g.scoringContextChecksum = scoringContextChecksum
            g.scoredAt = Instant.now()
            g.scoringError = null
        }
        series.lastScoredSelectorChecksum = selectorChecksum
    }

    private fun loadScoringInputs(games: List<SeriesGame>, scoringContextChecksum: String): Map<Long, ScoringInput> = games.associate { row ->
        val cache = row.gameDataCache!!
        val game = objectMapper.treeToValue(cache, PolemicaGame::class.java)
        val expectedPositions = game.players.orEmpty().map { it.position.value }.toSet()
        val loaded = runCatching { gamePointsService.fetchPlayerStats(row.polemicaGameId) }
        if (loaded.isFailure) {
            row.polemicaGameId to ScoringInput(
                cacheChecksum = sha256(cache.toString()),
                status = "LOAD_FAILED",
                error = "Failed to load public points: ${loaded.exceptionOrNull()?.message.orEmpty()}".take(512),
            )
        } else {
            val stats = loaded.getOrThrow()
            val duplicates = stats.groupingBy { it.position }.eachCount().filterValues { it > 1 }.keys
            val points = stats.associate { it.position to it.points }
            val missing = expectedPositions - points.keys
            val extra = points.keys - expectedPositions
            if (expectedPositions.isEmpty() || missing.isNotEmpty() || extra.isNotEmpty() || duplicates.isNotEmpty()) {
                row.polemicaGameId to ScoringInput(
                    cacheChecksum = sha256(cache.toString()),
                    points = points,
                    status = "PARTIAL",
                    error = when {
                        expectedPositions.isEmpty() -> "Cached game has no players"
                        duplicates.isNotEmpty() -> "Public points duplicate table positions ${duplicates.sorted().joinToString(",")}"
                        missing.isNotEmpty() -> "Public points miss table positions ${missing.sorted().joinToString(",")}"
                        else -> "Public points contain extra table positions ${extra.sorted().joinToString(",")}"
                    },
                )
            } else {
                val cacheChecksum = sha256(cache.toString())
                val inputChecksum = sha256(
                    "$cacheChecksum|$scoringContextChecksum|" + points.toSortedMap().entries.joinToString("|") { "${it.key}:${it.value}" },
                )
                row.polemicaGameId to ScoringInput(
                    cacheChecksum = cacheChecksum,
                    points = points,
                    status = "COMPLETE",
                    inputChecksum = inputChecksum,
                )
            }
        }
    }

    private fun persistIncompleteMetadata(seriesId: Long, inputs: Map<Long, ScoringInput>) {
        val series = seriesRepository.findByIdForUpdate(seriesId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Series $seriesId not found")
        rejectFinalized(series)
        seriesGameRepository.findAllBySeries_Id(seriesId).forEach { row ->
            val input = inputs[row.polemicaGameId] ?: return@forEach
            row.scored = false
            row.pointsStatus = input.status
            row.scoringInputChecksum = null
            row.scoringContextChecksum = null
            row.scoredAt = null
            row.scoringError = input.error?.take(512)
        }
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun rejectFinalized(series: Series) {
        if (series.finalized) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Cannot calculate scores for finalized series")
        }
    }

    private fun gamesFinishedForScoring(rows: List<SeriesGame>): List<SeriesGame> =
        rows
            .filter { it.gameDataCache != null }
            .filter { sg ->
                val node = sg.gameDataCache!!
                val polemicaGame = objectMapper.treeToValue(node, PolemicaGame::class.java)
                polemicaGame.isFinishedForScoring()
            }

    private fun scoreCardForSeries(
        games: List<io.github.mralex1810.fantasy.entity.SeriesGame>,
        fantasyCard: FantasyTeamCard,
        pointsByTablePositionByGameId: Map<Long, Map<Int, Double>>,
        replacementByFantasyPlayerId: Map<Long, Long>,
    ): Double {
        val userCard = fantasyCard.userCard!!
        val template = userCard.cardTemplate!!
        val fantasyPlayer = template.fantasyPlayer!!
        val fantasyPlayerId = fantasyPlayer.id
        val polemicaUserIds = fantasyPlayerId
            ?.let { fantasyPlayerAliasRepository.findPolemicaUserIdsByFantasyPlayerId(it) }
            ?.ifEmpty { listOf(fantasyPlayer.polemicaUserId) }
            ?: listOf(fantasyPlayer.polemicaUserId)
        val replacementPolemicaUserId = fantasyPlayerId?.let { replacementByFantasyPlayerId[it] }
        val templatePerks = template.perks
        val rarityModifier = template.rarity.scoreModifier

        var total = 0.0
        for (sg in games) {
            val node = sg.gameDataCache ?: continue
            val polemicaGame = objectMapper.treeToValue(node, PolemicaGame::class.java)
            val scoredPlayer = findScoringPlayer(polemicaGame, polemicaUserIds, replacementPolemicaUserId) ?: continue
            val player = scoredPlayer.player

            val basePoints = pointsByTablePositionByGameId[sg.polemicaGameId]
                ?.get(player.position.value)
                ?: 0.0
            val scoringContext = ScoringContext(basePoints = basePoints)
            val bonusByPerkId = LinkedHashMap<String, Double>()

            for (cta in templatePerks) {
                val perk = cta.perk ?: continue
                if (!isRoleApplicable(perk, player)) continue
                val det = perkRegistry.detector(perk.id) ?: continue
                val raw = det.matchCount(polemicaGame, player, scoringContext)
                val applied = appliedOccurrences(raw, perk.occurrenceType)
                if (applied <= 0) continue
                val effectiveBonus = cta.bonusPoints ?: perk.bonusPoints
                val contribution = effectiveBonus * applied
                bonusByPerkId.merge(perk.id, contribution) { a, b -> a + b }
            }

            val perkBonus = bonusByPerkId.values.sum()
            val gameTotal = cardGameTotalScore(basePoints, perkBonus, rarityModifier)
            total += gameTotal

            val gameScoreRow = FantasyTeamCardGameScore().apply {
                fantasyTeamCard = fantasyCard
                seriesGame = sg
                this.basePoints = basePoints
                this.perkBonus = perkBonus
                this.rarityModifier = rarityModifier
                totalScore = gameTotal
                scoredPolemicaUserId = player.player?.id
                scoredPlayerName = player.player?.username?.takeIf { it.isNotBlank() } ?: player.username
                scoredViaReplacement = scoredPlayer.viaReplacement
            }
            for ((perkId, bonusPoints) in bonusByPerkId) {
                val achEntity = templatePerks
                    .mapNotNull { it.perk }
                    .firstOrNull { it.id == perkId }
                    ?: continue
                gameScoreRow.perks.add(
                    FantasyTeamCardGamePerk().apply {
                        gameScore = gameScoreRow
                        perk = achEntity
                        this.bonusPoints = bonusPoints
                    },
                )
            }
            fantasyCard.gameScores.add(gameScoreRow)
        }
        return total
    }

    private fun replacementByFantasyPlayerId(seriesId: Long): Map<Long, Long> =
        seriesPlayerRepository.findAllBySeries_IdWithTournamentPlayers(seriesId)
            .mapNotNull { sp ->
                val fantasyPlayerId = sp.tournamentPlayer!!.fantasyPlayer!!.id ?: return@mapNotNull null
                sp.replacementPolemicaUserId?.let { fantasyPlayerId to it }
            }
            .toMap()

    private fun findScoringPlayer(
        game: PolemicaGame,
        mainPolemicaUserIds: List<Long>,
        replacementPolemicaUserId: Long?,
    ): ScoringPlayer? {
        val mainPlayer = findPlayer(game, mainPolemicaUserIds)
        if (mainPlayer != null) {
            return ScoringPlayer(player = mainPlayer, viaReplacement = false)
        }
        if (replacementPolemicaUserId == null) {
            return null
        }
        val replacementPlayer = findPlayer(game, listOf(replacementPolemicaUserId)) ?: return null
        return ScoringPlayer(player = replacementPlayer, viaReplacement = true)
    }

    private fun findPlayer(game: PolemicaGame, polemicaUserIds: Collection<Long>): PolemicaPlayer? =
        game.players.orEmpty().find { it.player?.id in polemicaUserIds }

    private data class ScoringPlayer(
        val player: PolemicaPlayer,
        val viaReplacement: Boolean,
    )

    private data class ScoringInput(
        val cacheChecksum: String,
        val points: Map<Int, Double> = emptyMap(),
        val status: String,
        val inputChecksum: String = "",
        val error: String? = null,
    )
}
