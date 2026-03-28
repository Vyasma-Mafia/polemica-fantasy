package io.github.mralex1810.fantasy.scoring

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.mafia.vyasma.polemica.library.model.game.PolemicaGame
import com.github.mafia.vyasma.polemica.library.model.game.PolemicaPlayer
import io.github.mralex1810.fantasy.entity.FantasyTeamCard
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
) : ScoringService {

    @Transactional
    override fun calculateScores(seriesId: Long) {
        if (!seriesRepository.existsById(seriesId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Series $seriesId not found")
        }
        val games = seriesGameRepository.findAllBySeries_Id(seriesId)
            .filter { it.gameDataCache != null }

        val teams = fantasyTeamRepository.findAllWithCardsForScoring(seriesId)

        for (team in teams) {
            var teamTotal = 0.0
            for (card in team.cards) {
                val score = scoreCardForSeries(games, card)
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
    ): Double {
        val userCard = fantasyCard.userCard!!
        val template = userCard.cardTemplate!!
        val polemicaUserId = template.fantasyPlayer!!.polemicaUserId
        val achievements = template.achievements

        var total = 0.0
        for (sg in games) {
            val node = sg.gameDataCache ?: continue
            val polemicaGame = objectMapper.treeToValue(node, PolemicaGame::class.java)
            val player = findPlayer(polemicaGame, polemicaUserId) ?: continue

            var gamePoints = player.award ?: 0.0
            for (a in achievements) {
                val det = achievementRegistry.detector(a.achievementType)
                if (det?.detect(polemicaGame, player) == true) {
                    gamePoints += a.bonusPoints
                }
            }
            total += gamePoints
        }
        return total
    }

    private fun findPlayer(game: PolemicaGame, polemicaUserId: Long): PolemicaPlayer? =
        game.players.orEmpty().find { it.player?.id == polemicaUserId }
}
