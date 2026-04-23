package io.github.mralex1810.fantasy.service

import com.github.mafia.vyasma.polemica.library.client.GamePointsService
import com.github.mafia.vyasma.polemica.library.model.game.PolemicaGame
import io.github.mralex1810.fantasy.dto.admin.response.AchievementFrequencyRowDto
import io.github.mralex1810.fantasy.dto.admin.response.AchievementStatisticsReportDto
import io.github.mralex1810.fantasy.dto.admin.response.MatchLoadFailureDto
import io.github.mralex1810.fantasy.entity.Achievement
import io.github.mralex1810.fantasy.polemica.PolemicaIntegrationService
import io.github.mralex1810.fantasy.repository.AchievementRepository
import io.github.mralex1810.fantasy.repository.FantasyPlayerRepository
import io.github.mralex1810.fantasy.scoring.achievement.AchievementDetectorRegistry
import io.github.mralex1810.fantasy.scoring.achievement.ScoringContext
import io.github.mralex1810.fantasy.scoring.appliedOccurrences
import io.github.mralex1810.fantasy.scoring.isRoleApplicable
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.LinkedHashMap

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
    fun collectReport(): AchievementStatisticsReportDto {
        val generatedAt = Instant.now()
        val polemicaUserIds = fantasyPlayerRepository.findAllPolemicaUserIds()
        val uniqueMatchIds = LinkedHashSet<Long>()
        var profileFetchFailures = 0

        for (userId in polemicaUserIds) {
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
        val loadFailures = mutableListOf<MatchLoadFailureDto>()
        var gamesLoaded = 0

        for (matchId in uniqueMatchIds) {
            val game = try {
                polemicaIntegrationService.loadMatch(matchId)
            } catch (e: Exception) {
                loadFailures.add(MatchLoadFailureDto(matchId, e.message))
                log.warn("getMatch failed for matchId={}: {}", matchId, e.message)
                continue
            }
            gamesLoaded++
            val pointsByPosition =
                try {
                    gamePointsService.fetchPlayerStats(matchId).associate { it.position to it.points }
                } catch (e: Exception) {
                    log.warn("fetchPlayerStats failed for matchId={}: {}", matchId, e.message)
                    null
                }
            accumulateForGame(game, achievements, aggregates, pointsByPosition)
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

        return AchievementStatisticsReportDto(
            generatedAt = generatedAt,
            fantasyPlayerCount = polemicaUserIds.size,
            profileFetchFailures = profileFetchFailures,
            uniqueMatchIdsFromProfiles = uniqueMatchIds.size,
            uniqueGamesLoaded = gamesLoaded,
            gamesFailedToLoad = loadFailures.size,
            loadFailures = loadFailures.take(MAX_FAILURES_IN_RESPONSE),
            achievementIdsInCatalog = catalogIds,
            byAchievement = byAchievement,
        )
    }

    private fun accumulateForGame(
        game: PolemicaGame,
        achievements: List<Achievement>,
        aggregates: Map<String, MutableAchievementAggregate>,
        pointsByPosition: Map<Int, Double>?,
    ) {
        val players = game.players.orEmpty()
        for (player in players) {
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
                agg.applicableSlots++
                agg.sumRawMatchCount += raw.toLong()
                if (raw > 0) agg.slotsWithPositiveRaw++
                agg.sumAppliedOccurrences += applied.toLong()
            }
        }
    }

    private class MutableAchievementAggregate {
        var applicableSlots: Long = 0
        var sumRawMatchCount: Long = 0
        var slotsWithPositiveRaw: Long = 0
        var sumAppliedOccurrences: Long = 0
    }

    private companion object {
        private const val MAX_FAILURES_IN_RESPONSE = 100
    }
}
