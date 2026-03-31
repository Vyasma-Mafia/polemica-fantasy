package io.github.mralex1810.fantasy.schedule

import io.github.mralex1810.fantasy.entity.SeriesStatus
import io.github.mralex1810.fantasy.repository.SeriesRepository
import io.github.mralex1810.fantasy.service.SeriesService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class ActiveSeriesSyncScheduler(
    private val seriesRepository: SeriesRepository,
    private val seriesService: SeriesService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Pulls games from Polemica and recalculates fantasy scores for series that are live
     * ([SeriesStatus.ACTIVE]) or in scoring ([SeriesStatus.SCORING]), and not yet finalized.
     */
    @Scheduled(cron = "0 0/10 * * * *")
    fun syncGamesAndCalculateScores() {
        val seriesList = seriesRepository.findAllByStatusInAndFinalizedIsFalse(
            listOf(SeriesStatus.ACTIVE, SeriesStatus.SCORING),
        )
        for (s in seriesList) {
            val id = s.id ?: continue
            try {
                seriesService.syncGames(id)
                seriesService.calculateScores(id)
            } catch (e: Exception) {
                log.warn("Scheduled sync/calculate failed for series {}: {}", id, e.message, e)
            }
        }
    }
}
