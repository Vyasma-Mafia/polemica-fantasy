package io.github.mralex1810.fantasy.service

import com.github.mafia.vyasma.polemica.library.client.GamePointsService
import com.github.mafia.vyasma.polemica.library.model.game.PolemicaGame
import io.github.mralex1810.fantasy.dto.admin.request.AchievementStatisticsRequest
import io.github.mralex1810.fantasy.dto.admin.response.AchievementAnomalySettingsDto
import io.github.mralex1810.fantasy.dto.admin.response.AchievementFrequencyRowDto
import io.github.mralex1810.fantasy.dto.admin.response.AchievementPlayerAnomalyDto
import io.github.mralex1810.fantasy.dto.admin.response.AchievementStatisticsReportDto
import io.github.mralex1810.fantasy.dto.admin.response.MatchLoadFailureDto
import io.github.mralex1810.fantasy.entity.Achievement
import io.github.mralex1810.fantasy.entity.FantasyPlayer
import io.github.mralex1810.fantasy.polemica.PolemicaIntegrationService
import io.github.mralex1810.fantasy.repository.AchievementRepository
import io.github.mralex1810.fantasy.repository.FantasyPlayerRepository
import io.github.mralex1810.fantasy.scoring.achievement.AchievementDetectorRegistry
import io.github.mralex1810.fantasy.scoring.achievement.ScoringContext
import io.github.mralex1810.fantasy.scoring.appliedOccurrences
import io.github.mralex1810.fantasy.scoring.isFinishedForScoring
import io.github.mralex1810.fantasy.scoring.isRoleApplicable
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class AchievementStatisticsService(
    private val fantasyPlayerRepository: FantasyPlayerRepository,
    private val achievementRepository: AchievementRepository,
    private val achievementRegistry: AchievementDetectorRegistry,
    private val polemicaIntegrationService: PolemicaIntegrationService,
    private val gamePointsService: GamePointsService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Collects per-achievement frequencies by loading each fantasy player's first profile page (100 games),
     * deduplicating match ids, then running the same [AchievementDetector] + role checks as scoring.
     */
    fun collectReport(request: AchievementStatisticsRequest? = null): AchievementStatisticsReportDto {
        val anomalySettings = request.toAnomalySettings()
        val generatedAt = Instant.now()
        val fantasyPlayers = fantasyPlayerRepository.findAll()
        val fantasyPlayersByPolemicaUserId = fantasyPlayers.associateBy { it.polemicaUserId }
        val uniqueMatchIds = LinkedHashSet<Long>()
        var profileFetchFailures = 0

        for (userId in fantasyPlayersByPolemicaUserId.keys) {
            try {
                val rows = polemicaIntegrationService.fetchProfileGamesFirstPageForStatistics(userId)
                for (row in rows) {
                    uniqueMatchIds.add(row.id)
                }
            } catch (e: Exception) {
                profileFetchFailures++
                log.warn("getProfileGames failed for polemicaUserId={}: {}", userId, e.message)
            }
        }

        val achievements = achievementRepository.findAllWithApplicableRoles()
        val aggregates = achievements.associate { it.id to MutableAchievementAggregate() }
        val playerAggregatesByPolemicaUserId = fantasyPlayers
            .mapNotNull { fp -> fp.id?.let { fp.polemicaUserId to MutablePlayerAggregate(fp, achievements) } }
            .toMap()
        val loadFailures = mutableListOf<MatchLoadFailureDto>()
        var gamesLoaded = 0
        var totalPlayerSlots = 0L

        for (matchId in uniqueMatchIds) {
            val game = try {
                polemicaIntegrationService.loadMatch(matchId)
            } catch (e: Exception) {
                loadFailures.add(MatchLoadFailureDto(matchId, e.message))
                log.warn("getMatch failed for matchId={}: {}", matchId, e.message)
                continue
            }
            gamesLoaded++
            if (!game.isFinishedForScoring()) {
                continue
            }
            val pointsByPosition =
                try {
                    gamePointsService.fetchPlayerStats(matchId).associate { it.position to it.points }
                } catch (e: Exception) {
                    log.warn("fetchPlayerStats failed for matchId={}: {}", matchId, e.message)
                    null
                }
            totalPlayerSlots += accumulateForGame(
                game,
                achievements,
                aggregates,
                playerAggregatesByPolemicaUserId,
                pointsByPosition,
            )
        }

        val catalogIds = achievements.map { it.id }.sorted()
        val byAchievement = catalogIds.map { id ->
            val a = aggregates[id] ?: MutableAchievementAggregate()
            AchievementFrequencyRowDto(
                achievementId = id,
                applicableSlots = a.applicableSlots,
                sumRawMatchCount = a.sumRawMatchCount,
                slotsWithPositiveRaw = a.slotsWithPositiveRaw,
                sumAppliedOccurrences = a.sumAppliedOccurrences,
            )
        }
        val anomalies = buildAnomalies(
            anomalySettings,
            achievements,
            aggregates,
            totalPlayerSlots,
            playerAggregatesByPolemicaUserId.values,
        )

        return AchievementStatisticsReportDto(
            generatedAt = generatedAt,
            fantasyPlayerCount = fantasyPlayersByPolemicaUserId.size,
            profileFetchFailures = profileFetchFailures,
            uniqueMatchIdsFromProfiles = uniqueMatchIds.size,
            uniqueGamesLoaded = gamesLoaded,
            totalPlayerSlots = totalPlayerSlots,
            gamesFailedToLoad = loadFailures.size,
            loadFailures = loadFailures.take(MAX_FAILURES_IN_RESPONSE),
            achievementIdsInCatalog = catalogIds,
            byAchievement = byAchievement,
            anomalySettings = anomalySettings,
            anomalies = anomalies,
        )
    }

    private fun accumulateForGame(
        game: PolemicaGame,
        achievements: List<Achievement>,
        aggregates: Map<String, MutableAchievementAggregate>,
        playerAggregatesByPolemicaUserId: Map<Long, MutablePlayerAggregate>,
        pointsByPosition: Map<Int, Double>?,
    ): Long {
        val players = game.players.orEmpty()
        for (player in players) {
            val playerAggregate = player.player?.id?.let { playerAggregatesByPolemicaUserId[it] }
            if (playerAggregate != null) {
                playerAggregate.games++
            }
            val basePoints =
                when (pointsByPosition) {
                    null -> Double.NaN
                    else -> pointsByPosition[player.position.value] ?: 0.0
                }
            val scoringContext = ScoringContext(basePoints = basePoints)
            for (ach in achievements) {
                val det = achievementRegistry.detector(ach.id) ?: continue
                if (!isRoleApplicable(ach, player)) continue
                val agg = aggregates.getValue(ach.id)
                val raw = det.matchCount(game, player, scoringContext)
                val applied = appliedOccurrences(raw, ach.occurrenceType)
                agg.add(raw, applied)
                playerAggregate?.byAchievement?.getValue(ach.id)?.add(raw, applied)
            }
        }
        return players.size.toLong()
    }

    private fun buildAnomalies(
        settings: AchievementAnomalySettingsDto,
        achievements: List<Achievement>,
        globalAggregates: Map<String, MutableAchievementAggregate>,
        totalPlayerSlots: Long,
        playerAggregates: Collection<MutablePlayerAggregate>,
    ): List<AchievementPlayerAnomalyDto> {
        val achievementsById = achievements.associateBy { it.id }
        val rows = mutableListOf<AchievementPlayerAnomalyDto>()

        for (playerAggregate in playerAggregates) {
            if (playerAggregate.games < settings.minPlayerGames) continue
            val fantasyPlayer = playerAggregate.fantasyPlayer
            val fantasyPlayerId = fantasyPlayer.id ?: continue

            for ((achievementId, playerAchievementAggregate) in playerAggregate.byAchievement) {
                if (playerAchievementAggregate.applicableSlots < settings.minApplicableSlots) continue

                val achievement = achievementsById[achievementId] ?: continue
                val globalAggregate = globalAggregates[achievementId] ?: continue
                val globalOccurrencesPerGame = rate(globalAggregate.sumAppliedOccurrences, totalPlayerSlots)
                val playerOccurrencesPerGame = rate(
                    playerAchievementAggregate.sumAppliedOccurrences,
                    playerAggregate.games,
                )
                val smoothedPlayerOccurrencesPerGame =
                    smoothedRate(
                        playerAchievementAggregate.sumAppliedOccurrences,
                        playerAggregate.games,
                        globalOccurrencesPerGame,
                        settings.priorGames,
                    )
                val excessOccurrencesPerGame = smoothedPlayerOccurrencesPerGame - globalOccurrencesPerGame
                if (excessOccurrencesPerGame <= 0.0) continue

                val baselineBonusPerGame = globalOccurrencesPerGame * achievement.bonusPoints
                val expectedBonusPerGame = smoothedPlayerOccurrencesPerGame * achievement.bonusPoints
                rows.add(
                    AchievementPlayerAnomalyDto(
                        fantasyPlayerId = fantasyPlayerId,
                        polemicaUserId = fantasyPlayer.polemicaUserId,
                        nickname = fantasyPlayer.nickname,
                        achievementId = achievement.id,
                        achievementName = achievement.name,
                        playerGames = playerAggregate.games,
                        applicableSlots = playerAchievementAggregate.applicableSlots,
                        sumAppliedOccurrences = playerAchievementAggregate.sumAppliedOccurrences,
                        globalOccurrencesPerGame = globalOccurrencesPerGame,
                        playerOccurrencesPerGame = playerOccurrencesPerGame,
                        smoothedPlayerOccurrencesPerGame = smoothedPlayerOccurrencesPerGame,
                        playerOccurrencesPerApplicableSlot = nullableRate(
                            playerAchievementAggregate.sumAppliedOccurrences,
                            playerAchievementAggregate.applicableSlots,
                        ),
                        globalOccurrencesPerApplicableSlot = nullableRate(
                            globalAggregate.sumAppliedOccurrences,
                            globalAggregate.applicableSlots,
                        ),
                        lift = when {
                            globalOccurrencesPerGame > 0.0 -> smoothedPlayerOccurrencesPerGame /
                                globalOccurrencesPerGame
                            else -> null
                        },
                        baselineBonusPerGame = baselineBonusPerGame,
                        expectedBonusPerGame = expectedBonusPerGame,
                        excessBonusPerGame = expectedBonusPerGame - baselineBonusPerGame,
                    ),
                )
            }
        }

        return rows
            .sortedWith(
                compareByDescending<AchievementPlayerAnomalyDto> { it.excessBonusPerGame }
                    .thenByDescending { it.lift ?: 0.0 }
                    .thenBy { it.achievementId }
                    .thenBy { it.nickname },
            )
            .take(settings.maxAnomalies)
    }

    private fun AchievementStatisticsRequest?.toAnomalySettings(): AchievementAnomalySettingsDto =
        AchievementAnomalySettingsDto(
            minPlayerGames = this?.minPlayerGames?.coerceAtLeast(1) ?: DEFAULT_MIN_PLAYER_GAMES,
            minApplicableSlots = this?.minApplicableSlots?.coerceAtLeast(0) ?: DEFAULT_MIN_APPLICABLE_SLOTS,
            priorGames = this?.priorGames?.takeIf { it.isFiniteNumber() && it >= 0.0 } ?: DEFAULT_PRIOR_GAMES,
            maxAnomalies = this?.maxAnomalies?.coerceIn(1, MAX_ANOMALIES_IN_RESPONSE)
                ?: DEFAULT_MAX_ANOMALIES,
        )

    private fun Double.isFiniteNumber(): Boolean =
        !isNaN() && this != Double.POSITIVE_INFINITY && this != Double.NEGATIVE_INFINITY

    private fun rate(numerator: Long, denominator: Long): Double =
        when {
            denominator > 0 -> numerator.toDouble() / denominator.toDouble()
            else -> 0.0
        }

    private fun nullableRate(numerator: Long, denominator: Long): Double? =
        when {
            denominator > 0 -> numerator.toDouble() / denominator.toDouble()
            else -> null
        }

    private fun smoothedRate(
        numerator: Long,
        denominator: Long,
        baselineRate: Double,
        priorDenominator: Double,
    ): Double =
        when {
            denominator <= 0 && priorDenominator <= 0.0 -> 0.0
            else -> (numerator.toDouble() + baselineRate * priorDenominator) /
                (denominator.toDouble() + priorDenominator)
        }

    private class MutableAchievementAggregate {
        var applicableSlots: Long = 0
        var sumRawMatchCount: Long = 0
        var slotsWithPositiveRaw: Long = 0
        var sumAppliedOccurrences: Long = 0

        fun add(raw: Int, applied: Int) {
            applicableSlots++
            sumRawMatchCount += raw.toLong()
            if (raw > 0) slotsWithPositiveRaw++
            sumAppliedOccurrences += applied.toLong()
        }
    }

    private class MutablePlayerAggregate(
        val fantasyPlayer: FantasyPlayer,
        achievements: List<Achievement>,
    ) {
        var games: Long = 0
        val byAchievement: Map<String, MutableAchievementAggregate> =
            achievements.associate { it.id to MutableAchievementAggregate() }
    }

    private companion object {
        private const val MAX_FAILURES_IN_RESPONSE = 100
        private const val DEFAULT_MIN_PLAYER_GAMES = 20
        private const val DEFAULT_MIN_APPLICABLE_SLOTS = 5
        private const val DEFAULT_PRIOR_GAMES = 30.0
        private const val DEFAULT_MAX_ANOMALIES = 100
        private const val MAX_ANOMALIES_IN_RESPONSE = 500
    }
}
