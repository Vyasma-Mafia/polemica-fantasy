package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.dto.user.response.SeriesFinalizationResultDto
import io.github.mralex1810.fantasy.entity.FantikiTransactionReason
import io.github.mralex1810.fantasy.repository.FantasyTeamRepository
import io.github.mralex1810.fantasy.repository.SeriesRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class SeriesFinalizationService(
    private val seriesRepository: SeriesRepository,
    private val fantasyTeamRepository: FantasyTeamRepository,
    private val economyConfigService: EconomyConfigService,
    private val userService: UserService,
) {

    @Transactional
    fun finalizeSeries(seriesId: Long): SeriesFinalizationResultDto {
        val series = seriesRepository.findById(seriesId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Series $seriesId not found")
        }
        if (series.finalized) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Series already finalized")
        }
        val teamsWithCards = fantasyTeamRepository.findAllWithCardsForScoring(seriesId)
        var cardsDecremented = 0
        for (ft in teamsWithCards) {
            for (ftc in ft.cards) {
                val uc = ftc.userCard!!
                val before = uc.usesRemaining
                uc.usesRemaining = maxOf(0, before - 1)
                cardsDecremented++
            }
        }
        val leaderboard = fantasyTeamRepository.findLeaderboardForSeries(seriesId)
        val n = leaderboard.size
        var rewardsDistributed = 0
        leaderboard.forEachIndexed { index, ft ->
            val position = index + 1
            val baseReward = economyConfigService.getSeriesReward(position, n)
            val reward = scaleSeriesRewardByRosterSize(baseReward, ft.cards.size)
            if (reward > 0) {
                val u = ft.telegramUser!!
                userService.addBalance(u.id!!, reward, FantikiTransactionReason.SERIES_REWARD)
                rewardsDistributed++
            }
        }
        series.finalized = true
        seriesRepository.save(series)
        return SeriesFinalizationResultDto(
            rewardsDistributed = rewardsDistributed,
            cardsDecremented = cardsDecremented,
        )
    }

    /**
     * Full roster (3 cards) gets the configured reward; 1 or 2 cards get ⌈1/3⌉ or ⌈2/3⌉ of that amount (integer fantiki).
     */
    internal fun scaleSeriesRewardByRosterSize(baseReward: Long, cardCount: Int): Long {
        if (baseReward <= 0L || cardCount <= 0) {
            return 0L
        }
        val c = cardCount.coerceIn(1, 3)
        return when (c) {
            3 -> baseReward
            2 -> (2L * baseReward + 2L) / 3L
            else -> (baseReward + 2L) / 3L
        }
    }
}
