package io.github.mralex1810.fantasy.scoring

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.mafia.vyasma.polemica.library.client.GamePointsService
import com.github.mafia.vyasma.polemica.library.model.game.PolemicaGame
import com.github.mafia.vyasma.polemica.library.model.game.PolemicaPlayer
import io.github.mralex1810.fantasy.entity.FantasyTeamCard
import io.github.mralex1810.fantasy.entity.FantasyTeamCardGameAchievement
import io.github.mralex1810.fantasy.entity.FantasyTeamCardGameScore
import io.github.mralex1810.fantasy.repository.FantasyTeamRepository
import io.github.mralex1810.fantasy.repository.SeriesGameRepository
import io.github.mralex1810.fantasy.repository.SeriesRepository
import io.github.mralex1810.fantasy.scoring.achievement.AchievementDetectorRegistry
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class DefaultScoringService(
    private val seriesRepository: SeriesRepository,
    private val seriesGameRepository: SeriesGameRepository,
    private val fantasyTeamRepository: FantasyTeamRepository,
    private val achievementRegistry: AchievementDetectorRegistry,
    private val objectMapper: ObjectMapper,
    private val gamePointsService: GamePointsService,
) : ScoringService {

    @Transactional
    override fun calculateScores(seriesId: Long) {
        if (!seriesRepository.existsById(seriesId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Series $seriesId not found")
        }
        val games = seriesGameRepository.findAllBySeries_Id(seriesId)
            .filter { it.gameDataCache != null }

        val pointsByTablePositionByGameId = games.map { it.polemicaGameId }.distinct().associateWith { gameId ->
            gamePointsService.fetchPlayerStats(gameId).associate { it.position to it.points }
        }

        val teams = fantasyTeamRepository.findAllWithCardsForScoring(seriesId)

        for (team in teams) {
            var teamTotal = 0.0
            for (card in team.cards) {
                card.gameScores.clear()
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

    private fun scoreCardForSeries(
        games: List<io.github.mralex1810.fantasy.entity.SeriesGame>,
        fantasyCard: FantasyTeamCard,
        pointsByTablePositionByGameId: Map<Long, Map<Int, Double>>,
    ): Double {
        val userCard = fantasyCard.userCard!!
        val template = userCard.cardTemplate!!
        val polemicaUserId = template.fantasyPlayer!!.polemicaUserId
        val templateAchievements = template.achievements
        val rarityModifier = template.rarity.scoreModifier

        var total = 0.0
        for (sg in games) {
            val node = sg.gameDataCache ?: continue
            val polemicaGame = objectMapper.treeToValue(node, PolemicaGame::class.java)
            val player = findPlayer(polemicaGame, polemicaUserId) ?: continue

            val basePoints = pointsByTablePositionByGameId[sg.polemicaGameId]
                ?.get(player.position.value)
                ?: 0.0
            val bonusByAchievementId = LinkedHashMap<String, Double>()

            for (cta in templateAchievements) {
                val ach = cta.achievement ?: continue
                if (!isRoleApplicable(ach, player)) continue
                val det = achievementRegistry.detector(ach.id) ?: continue
                val raw = det.matchCount(polemicaGame, player)
                val applied = appliedOccurrences(raw, ach.occurrenceType)
                if (applied <= 0) continue
                val effectiveBonus = cta.bonusPoints ?: ach.bonusPoints
                val contribution = effectiveBonus * applied
                bonusByAchievementId.merge(ach.id, contribution) { a, b -> a + b }
            }

            val achievementBonus = bonusByAchievementId.values.sum()
            val gameTotal = cardGameTotalScore(basePoints, achievementBonus, rarityModifier)
            total += gameTotal

            val gameScoreRow = FantasyTeamCardGameScore().apply {
                fantasyTeamCard = fantasyCard
                seriesGame = sg
                this.basePoints = basePoints
                this.achievementBonus = achievementBonus
                this.rarityModifier = rarityModifier
                totalScore = gameTotal
            }
            for ((achievementId, bonusPoints) in bonusByAchievementId) {
                val achEntity = templateAchievements
                    .mapNotNull { it.achievement }
                    .firstOrNull { it.id == achievementId }
                    ?: continue
                gameScoreRow.achievements.add(
                    FantasyTeamCardGameAchievement().apply {
                        gameScore = gameScoreRow
                        achievement = achEntity
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
