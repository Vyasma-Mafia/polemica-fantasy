package io.github.mralex1810.fantasy.event

import io.github.mralex1810.fantasy.config.TelegramProperties
import io.github.mralex1810.fantasy.telegram.TelegramBotApiClient
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class SeriesRosterReplacementNotificationListener(
    private val telegramProperties: TelegramProperties,
    private val telegramBotApiClient: TelegramBotApiClient,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    fun onSeriesRosterReplacement(event: SeriesRosterReplacementNotificationEvent) {
        val token = telegramProperties.token
        if (!telegramProperties.notifications.enabled || token.isBlank()) {
            log.debug(
                "Series roster replacement Telegram notifications skipped (enabled={}, token blank={})",
                telegramProperties.notifications.enabled,
                token.isBlank(),
            )
            return
        }
        for (recipient in event.recipients) {
            try {
                telegramBotApiClient.sendMessage(token, recipient.telegramChatId, recipient.messageText)
            } catch (e: Exception) {
                log.warn(
                    "Failed to send series roster replacement Telegram message to chatId={}",
                    recipient.telegramChatId,
                    e,
                )
            }
        }
    }
}
