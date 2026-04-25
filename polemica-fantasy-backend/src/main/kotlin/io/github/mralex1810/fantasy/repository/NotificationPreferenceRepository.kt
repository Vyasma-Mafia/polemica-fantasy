package io.github.mralex1810.fantasy.repository

import io.github.mralex1810.fantasy.entity.NotificationCategory
import io.github.mralex1810.fantasy.entity.NotificationPreference
import org.springframework.data.jpa.repository.JpaRepository

interface NotificationPreferenceRepository : JpaRepository<NotificationPreference, Long> {
    fun findByTelegramUser_IdAndCategory(telegramUserId: Long, category: NotificationCategory): NotificationPreference?

    fun findAllByTelegramUser_Id(telegramUserId: Long): List<NotificationPreference>

    fun deleteAllByTelegramUser_Id(telegramUserId: Long)
}
