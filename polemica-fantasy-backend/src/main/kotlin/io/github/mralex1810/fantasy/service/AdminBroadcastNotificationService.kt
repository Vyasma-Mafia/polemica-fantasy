package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.config.TelegramProperties
import io.github.mralex1810.fantasy.repository.TelegramUserRepository
import io.github.mralex1810.fantasy.telegram.TelegramBroadcastAsyncSender
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class AdminBroadcastNotificationService(
    private val telegramProperties: TelegramProperties,
    private val telegramUserRepository: TelegramUserRepository,
    private val telegramBroadcastAsyncSender: TelegramBroadcastAsyncSender,
) {

    @Transactional(readOnly = true)
    fun queueBroadcast(text: String): Long {
        val token = telegramProperties.token.trim()
        if (!telegramProperties.notifications.enabled || token.isBlank()) {
            throw ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Telegram notifications are disabled or bot token is not configured",
            )
        }
        val ids = telegramUserRepository.findAllTelegramIds()
        if (ids.isEmpty()) {
            return 0L
        }
        telegramBroadcastAsyncSender.sendToAllChats(token, ids, text)
        return ids.size.toLong()
    }
}
