package io.github.mralex1810.fantasy.scheduler

import io.github.mralex1810.fantasy.repository.DeadlineReminderRepository
import io.github.mralex1810.fantasy.observability.FantasyMetrics
import io.github.mralex1810.fantasy.observability.FantasyMetrics.SchedulerJob
import io.github.mralex1810.fantasy.service.DeadlineReminderService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class DeadlineReminderScheduler(
    private val deadlineReminderRepository: DeadlineReminderRepository,
    private val deadlineReminderService: DeadlineReminderService,
    private val fantasyMetrics: FantasyMetrics,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedRate = 60_000)
    fun processReminders() {
        val sample = fantasyMetrics.start()
        var scheduledItems = 0
        var successfulItems = 0
        var failedItems = 0
        var fatalFailure = false
        try {
            val pending = deadlineReminderRepository.findAllByRemindAtBeforeAndSentIsFalse(Instant.now())
            val pendingWithIds = pending.mapNotNull { reminder -> reminder.id?.let { it to reminder } }
            scheduledItems = pendingWithIds.size
            for ((reminderId, _) in pendingWithIds) {
                try {
                    deadlineReminderService.sendReminder(reminderId)
                    successfulItems++
                } catch (e: Exception) {
                    failedItems++
                    log.error("Failed to process deadline reminder id={}", reminderId, e)
                }
            }
        } catch (e: Exception) {
            fatalFailure = true
            throw e
        } finally {
            fantasyMetrics.recordSchedulerRun(
                sample = sample,
                job = SchedulerJob.DEADLINE_REMINDER,
                scheduledItems = scheduledItems,
                successfulItems = successfulItems,
                failedItems = failedItems,
                fatalFailure = fatalFailure,
            )
        }
    }
}
