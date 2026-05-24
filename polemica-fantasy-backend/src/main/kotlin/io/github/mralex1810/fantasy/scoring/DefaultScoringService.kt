package io.github.mralex1810.fantasy.scoring

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.mafia.vyasma.polemica.library.client.GamePointsService
import com.github.mafia.vyasma.polemica.library.model.game.PolemicaGame
import com.github.mafia.vyasma.polemica.library.model.game.PolemicaPlayer
import io.github.mralex1810.fantasy.entity.FantasyTeamCard
import io.github.mralex1810.fantasy.entity.FantasyTeamCardGamePerk
import io.github.mralex1810.fantasy.entity.FantasyTeamCardGameScore
import io.github.mralex1810.fantasy.entity.SeriesGame
import io.github.mralex1810.fantasy.repository.FantasyTeamRepository
import io.github.mralex1810.fantasy.repository.SeriesGameRepository
import io.github.mralex1810.fantasy.repository.SeriesRepository
import io.github.mralex1810.fantasy.scoring.perk.PerkDetectorRegistry
import io.github.mralex1810.fantasy.scoring.perk.ScoringContext
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.server.ResponseStatusException

@Service
class DefaultScoringService(
    private val seriesRepository: SeriesRepository,
    private val seriesGameRepository: SeriesGameRepository,
    private val fantasyTeamRepository: FantasyTeamRepository,
    private val perkRegistry: PerkDetectorRegistry,
    private val objectMapper: ObjectMapper,
    private val gamePointsService: GamePointsService,
    platformTransactionManager: PlatformTransactionManager,
) : ScoringService {

    private val transactionTemplate = TransactionTemplate(platformTransactionManager)

    /**
     * Fetches points from Polemica public HTTP API outside a DB transaction; persistence and scoring run in a short transaction.
     */
    override fun calculateScores(seriesId: Long) {
        if (!seriesRepository.existsById(seriesId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Series $seriesId not found")
        }
        val rows = seriesGameRepository.findAllBySeries_Id(seriesId)
        val gamesForPoints = gamesFinishedForScoring(rows)
        val pointsByTablePositionByGameId = gamesForPoints
            .map { it.polemicaGameId }
            .distinct()
            .associateWith { gameId ->
                gamePointsService.fetchPlayerStats(gameId).associate { it.position to it.points }
            }

        transactionTemplate.executeWithoutResult {
            applyScoresInTransaction(seriesId, pointsByTablePositionByGameId)
        }
    }

    private fun applyScoresInTransaction(
        seriesId: Long,
        pointsByTablePositionByGameId: Map<Long, Map<Int, Double>>,
    ) {
        val games = gamesFinishedForScoring(seriesGameRepository.findAllBySeries_Id(seriesId))
        val teams = fantasyTeamRepository.findAllWithCardsForScoring(seriesId)

        for (team in teams) {
            var teamTotal = 0.0
            for (card in team.cards) {
                card.gameScores.clear()
                fantasyTeamRepository.flush()
                val score = scoreCardForSeries(games, card, pointsByTablePositionByGameId)
                card.score = score
                teamTotal += score
            }
            team.totalScore = teamTotal
        }

        for (g in games) {
            g.scored = true
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
    ): Double {
        val userCard = fantasyCard.userCard!!
        val template = userCard.cardTemplate!!
        val polemicaUserId = template.fantasyPlayer!!.polemicaUserId
        val templatePerks = template.perks
        val rarityModifier = template.rarity.scoreModifier

        var total = 0.0
        for (sg in games) {
            val node = sg.gameDataCache ?: continue
            val polemicaGame = objectMapper.treeToValue(node, PolemicaGame::class.java)
            val player = findPlayer(polemicaGame, polemicaUserId) ?: continue

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

    private fun findPlayer(game: PolemicaGame, polemicaUserId: Long): PolemicaPlayer? =
        game.players.orEmpty().find { it.player?.id == polemicaUserId }
}
