package io.github.mralex1810.fantasy.repository

import io.github.mralex1810.fantasy.entity.DeadlineReminder
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

interface DeadlineReminderRepository : JpaRepository<DeadlineReminder, Long> {
    fun findBySeries_Id(seriesId: Long): DeadlineReminder?

    fun findAllByRemindAtBeforeAndSentIsFalse(now: Instant): List<DeadlineReminder>
}
