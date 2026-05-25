package io.github.mralex1810.fantasy.repository

import io.github.mralex1810.fantasy.entity.UserProfileCustomization
import org.springframework.data.jpa.repository.JpaRepository

interface UserProfileCustomizationRepository : JpaRepository<UserProfileCustomization, Long> {
    fun findByTelegramUser_Id(telegramUserId: Long): UserProfileCustomization?

    fun findAllByTelegramUserIdIn(telegramUserIds: Collection<Long>): List<UserProfileCustomization>
}
