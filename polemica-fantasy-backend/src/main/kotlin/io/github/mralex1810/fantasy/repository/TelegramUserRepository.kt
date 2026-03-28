package io.github.mralex1810.fantasy.repository

import io.github.mralex1810.fantasy.entity.TelegramUser
import org.springframework.data.jpa.repository.JpaRepository

interface TelegramUserRepository : JpaRepository<TelegramUser, Long> {
    fun findByTelegramId(telegramId: Long): TelegramUser?
}
