package io.github.mralex1810.fantasy.schedule

import io.github.mralex1810.fantasy.entity.SeriesStatus
import io.github.mralex1810.fantasy.observability.FantasyMetrics
import io.github.mralex1810.fantasy.observability.FantasyMetrics.SchedulerJob
import io.github.mralex1810.fantasy.repository.SeriesRepository
import io.github.mralex1810.fantasy.service.SeriesService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class ActiveSeriesSyncScheduler(
    private val seriesRepository: SeriesRepository,
    private val seriesService: SeriesService,
    private val fantasyMetrics: FantasyMetrics,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Pulls games from Polemica and recalculates fantasy scores for series that are live
     * ([SeriesStatus.ACTIVE]) or in scoring ([SeriesStatus.SCORING]), and not yet finalized.
     */
    @Scheduled(cron = "0 0/10 * * * *")
    fun syncGamesAndCalculateScores() {
        val sample = fantasyMetrics.start()
        var scheduledItems = 0
        var successfulItems = 0
        var failedItems = 0
        var fatalFailure = false
        try {
            val seriesList = seriesRepository.findAllByStatusInAndFinalizedIsFalse(
                listOf(SeriesStatus.ACTIVE, SeriesStatus.SCORING),
            )
            fantasyMetrics.recordActiveSeriesCounts(seriesList.groupingBy { it.status }.eachCount())
            val seriesWithIds = seriesList.mapNotNull { series -> series.id?.let { it to series } }
            scheduledItems = seriesWithIds.size
            for ((id, _) in seriesWithIds) {
                try {
                    seriesService.syncGames(id)
                    seriesService.calculateScores(id)
                    successfulItems++
                } catch (e: Exception) {
                    failedItems++
                    log.warn("Scheduled sync/calculate failed for series {}: {}", id, e.message, e)
                }
            }
        } catch (e: Exception) {
            fatalFailure = true
            throw e
        } finally {
            fantasyMetrics.recordSchedulerRun(
                sample = sample,
                job = SchedulerJob.ACTIVE_SERIES_SYNC,
                scheduledItems = scheduledItems,
                successfulItems = successfulItems,
                failedItems = failedItems,
                fatalFailure = fatalFailure,
            )
        }
    }
}
