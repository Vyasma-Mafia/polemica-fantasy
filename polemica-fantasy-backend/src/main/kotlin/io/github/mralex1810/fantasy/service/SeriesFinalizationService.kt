package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.dto.user.response.SeriesFinalizationResultDto
import io.github.mralex1810.fantasy.entity.FantikiTransactionReason
import io.github.mralex1810.fantasy.event.AchievementProgressEvent
import io.github.mralex1810.fantasy.event.AchievementProgressEventType
import io.github.mralex1810.fantasy.event.LeagueResult
import io.github.mralex1810.fantasy.event.SeriesFinalizedNotificationEvent
import io.github.mralex1810.fantasy.event.SeriesFinalizedRecipient
import io.github.mralex1810.fantasy.event.publicDisplayNameForNotifications
import io.github.mralex1810.fantasy.entity.SeriesStatus
import io.github.mralex1810.fantasy.repository.FantasyTeamRepository
import io.github.mralex1810.fantasy.repository.SeriesLeagueRepository
import io.github.mralex1810.fantasy.repository.SeriesRepository
import io.github.mralex1810.fantasy.repository.UserCardRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

@Service
class SeriesFinalizationService(
    private val seriesRepository: SeriesRepository,
    private val seriesLeagueRepository: SeriesLeagueRepository,
    private val fantasyTeamRepository: FantasyTeamRepository,
    private val userCardRepository: UserCardRepository,
    private val userService: UserService,
    private val economyConfigService: EconomyConfigService,
    private val leagueService: LeagueService,
    private val seriesCompletionService: SeriesCompletionService,
    private val applicationEventPublisher: ApplicationEventPublisher,
) {

    @Transactional
    fun finalizeSeries(seriesId: Long, expectedReadinessChecksum: String? = null): SeriesFinalizationResultDto {
        return finalizeSeries(seriesId, expectedReadinessChecksum, adminInitiated = false)
    }

    @Transactional
    fun finalizeFromAdmin(seriesId: Long): SeriesFinalizationResultDto {
        return finalizeSeries(seriesId, expectedReadinessChecksum = null, adminInitiated = true)
    }

    private fun finalizeSeries(
        seriesId: Long,
        expectedReadinessChecksum: String?,
        adminInitiated: Boolean,
    ): SeriesFinalizationResultDto {
        val series = seriesRepository.findByIdForUpdate(seriesId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Series $seriesId not found")
        if (series.finalized) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Series already finalized")
        }
        val completion = if (adminInitiated) {
            seriesCompletionService.evaluateForAdminFinalization(seriesId)
        } else {
            seriesCompletionService.evaluateForFinalization(seriesId)
        }
        if (!completion.ready) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Series is not ready to finalize: ${completion.reason}")
        }
        if (expectedReadinessChecksum != null && expectedReadinessChecksum != completion.checksum) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Series readiness changed before finalization")
        }
        val tournamentName = series.tournament!!.name
        val seriesName = series.name
        val teamsWithCards = fantasyTeamRepository.findAllWithCardsForScoring(seriesId)
        val cardLeagueSetById = LinkedHashMap<Long, MutableSet<Long>>()
        val cardById = LinkedHashMap<Long, io.github.mralex1810.fantasy.entity.UserCard>()
        for (team in teamsWithCards) {
            val leagueId = team.seriesLeague?.id ?: continue
            for (slot in team.cards) {
                val userCard = slot.userCard ?: continue
                val userCardId = userCard.id ?: continue
                cardById[userCardId] = userCard
                cardLeagueSetById.computeIfAbsent(userCardId) { LinkedHashSet() }.add(leagueId)
            }
        }
        val lockedCardsById =
            if (cardLeagueSetById.isEmpty()) {
                emptyMap()
            } else {
                userCardRepository.findAllByIdInForUpdate(cardLeagueSetById.keys)
                    .associateBy { it.id!! }
            }
        val overcommitted = cardLeagueSetById.mapNotNull { (userCardId, leagues) ->
            val userCard = lockedCardsById[userCardId] ?: cardById[userCardId] ?: return@mapNotNull null
            val decrementBy = leagues.size
            if (userCard.usesRemaining < decrementBy) {
                "$userCardId needs $decrementBy uses but has ${userCard.usesRemaining}"
            } else {
                null
            }
        }
        if (overcommitted.isNotEmpty()) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Cannot finalize series: cards overcommitted: ${overcommitted.joinToString("; ")}",
            )
        }
        val leagueIds = teamsWithCards.mapNotNull { it.seriesLeague?.id }.distinct()
        val leagueResultsByTelegramId = LinkedHashMap<Long, MutableList<LeagueResult>>()
        val totalRewardByTelegramId = LinkedHashMap<Long, Long>()
        val internalIdByTelegramId = LinkedHashMap<Long, Long>()
        var rewardsDistributed = 0

        for (seriesLeagueId in leagueIds) {
            val seriesLeague = seriesLeagueRepository.findById(seriesLeagueId).orElse(null) ?: continue
            val leaderboard = fantasyTeamRepository.findLeaderboardForSeriesLeague(seriesLeagueId)
            val totalParticipants = leaderboard.size
            if (totalParticipants == 0) continue
            val winnerPublicName = leaderboard.firstOrNull()?.telegramUser?.publicDisplayNameForNotifications()
            val rewardScalePercent = leagueService.getEffectiveRewardScale(seriesLeague)
            val leagueName = seriesLeague.league!!.name

            leaderboard.forEachIndexed { index, ft ->
                val position = index + 1
                val baseReward = economyConfigService.getSeriesReward(position, totalParticipants)
                val scaledByRoster = scaleSeriesRewardByRosterSize(baseReward, ft.cards.size)
                val reward = scaledByRoster * rewardScalePercent / 100L
                val user = ft.telegramUser!!
                val userInternalId = user.id!!
                if (reward > 0) {
                    userService.addBalance(userInternalId, reward, FantikiTransactionReason.SERIES_REWARD)
                    rewardsDistributed++
                }
                internalIdByTelegramId[user.telegramId] = userInternalId
                totalRewardByTelegramId.merge(user.telegramId, reward) { a, b -> a + b }
                leagueResultsByTelegramId
                    .computeIfAbsent(user.telegramId) { mutableListOf() }
                    .add(
                        LeagueResult(
                            leagueName = leagueName,
                            winnerPublicName = winnerPublicName,
                            place = position,
                            total = totalParticipants,
                            reward = reward,
                        ),
                    )
            }
        }

        var cardsDecremented = 0
        for ((userCardId, leagues) in cardLeagueSetById) {
            val userCard = lockedCardsById[userCardId] ?: cardById[userCardId] ?: continue
            val decrementBy = leagues.size
            userCard.usesRemaining -= decrementBy
            cardsDecremented += decrementBy
        }
        if (lockedCardsById.isNotEmpty()) {
            userCardRepository.saveAll(lockedCardsById.values)
        }
        series.status = SeriesStatus.FINISHED
        series.finalized = true
        series.finalizedAt = Instant.now()
        seriesRepository.save(series)

        val notificationRecipients = leagueResultsByTelegramId.entries.map { (telegramId, leagueResults) ->
            val balanceAfter = userService.getBalance(internalIdByTelegramId[telegramId]!!)
            SeriesFinalizedRecipient(
                telegramId = telegramId,
                leagueResults = leagueResults.sortedBy { it.leagueName },
                totalReward = totalRewardByTelegramId[telegramId] ?: 0L,
                balanceAfter = balanceAfter,
            )
        }
        applicationEventPublisher.publishEvent(
            SeriesFinalizedNotificationEvent(
                seriesId = seriesId,
                tournamentName = tournamentName,
                seriesName = seriesName,
                recipients = notificationRecipients,
                rewardedUsers = totalRewardByTelegramId.count { (_, totalReward) -> totalReward > 0L },
                cardUsesDecremented = cardsDecremented,
            ),
        )
        applicationEventPublisher.publishEvent(
            AchievementProgressEvent(
                type = AchievementProgressEventType.SERIES_FINALIZED,
                internalTelegramUserIds = internalIdByTelegramId.values.toSet(),
            ),
        )
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
