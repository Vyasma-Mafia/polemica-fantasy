package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.config.TelegramProperties
import io.github.mralex1810.fantasy.dto.admin.response.DirectMessageResultResponse
import io.github.mralex1810.fantasy.entity.NotificationCategory
import io.github.mralex1810.fantasy.repository.TelegramUserRepository
import io.github.mralex1810.fantasy.telegram.TelegramBroadcastAsyncSender
import io.github.mralex1810.fantasy.telegram.TelegramBotApiClient
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class AdminBroadcastNotificationService(
    private val telegramProperties: TelegramProperties,
    private val telegramUserRepository: TelegramUserRepository,
    private val telegramBroadcastAsyncSender: TelegramBroadcastAsyncSender,
    private val notificationDeliveryService: NotificationDeliveryService,
) {

    @Transactional(readOnly = true)
    fun queueBroadcast(text: String): Long {
        requireTelegramConfigured()
        val ids = telegramUserRepository.findAllTelegramIds()
        if (ids.isEmpty()) {
            return 0L
        }
        telegramBroadcastAsyncSender.sendToAllChats(text)
        return ids.size.toLong()
    }

    fun sendDirect(telegramUserId: Long, text: String): DirectMessageResultResponse {
        requireTelegramConfigured()
        telegramUserRepository.findByTelegramId(telegramUserId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Telegram user $telegramUserId not found")

        val report = notificationDeliveryService.deliverToMany(
            recipients = listOf(telegramUserId),
            category = NotificationCategory.ADMIN_BROADCAST,
            textProvider = { text },
            parseMode = TelegramBotApiClient.PARSE_MODE_MARKDOWN_V2,
        )

        return DirectMessageResultResponse(
            telegramUserId = telegramUserId,
            sent = report.sent > 0,
            skippedBlocked = report.skippedBlocked > 0,
            skippedPreference = report.skippedPreference > 0,
            failed = report.failed > 0,
        )
    }

    private fun requireTelegramConfigured() {
        val token = telegramProperties.token.trim()
        if (!telegramProperties.notifications.enabled || token.isBlank()) {
            throw ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Telegram notifications are disabled or bot token is not configured",
            )
        }
    }
}
