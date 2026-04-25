package io.github.mralex1810.fantasy.scheduler

import io.github.mralex1810.fantasy.repository.DeadlineReminderRepository
import io.github.mralex1810.fantasy.service.DeadlineReminderService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class DeadlineReminderScheduler(
    private val deadlineReminderRepository: DeadlineReminderRepository,
    private val deadlineReminderService: DeadlineReminderService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedRate = 60_000)
    fun processReminders() {
        val pending = deadlineReminderRepository.findAllByRemindAtBeforeAndSentIsFalse(Instant.now())
        for (reminder in pending) {
            val reminderId = reminder.id ?: continue
            try {
                deadlineReminderService.sendReminder(reminderId)
            } catch (e: Exception) {
                log.error("Failed to process deadline reminder id={}", reminderId, e)
            }
        }
    }
}
