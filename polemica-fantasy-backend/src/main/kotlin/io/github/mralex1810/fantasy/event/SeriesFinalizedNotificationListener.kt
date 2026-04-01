package io.github.mralex1810.fantasy.event

import io.github.mralex1810.fantasy.config.TelegramProperties
import io.github.mralex1810.fantasy.telegram.TelegramBotApiClient
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class SeriesFinalizedNotificationListener(
    private val telegramProperties: TelegramProperties,
    private val telegramBotApiClient: TelegramBotApiClient,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    fun onSeriesFinalized(event: SeriesFinalizedNotificationEvent) {
        val token = telegramProperties.token
        if (!telegramProperties.notifications.enabled || token.isBlank()) {
            log.debug("Series finalization Telegram notifications skipped (enabled={}, token blank={})", telegramProperties.notifications.enabled, token.isBlank())
            return
        }
        for (recipient in event.recipients) {
            val text = buildSeriesFinalizedTelegramMessage(event.tournamentName, event.seriesName, recipient)
            try {
                telegramBotApiClient.sendMessage(token, recipient.telegramId, text)
            } catch (e: Exception) {
                log.warn(
                    "Failed to send series finalization Telegram message to chatId={} place={}/{}",
                    recipient.telegramId,
                    recipient.place,
                    recipient.total,
                    e,
                )
            }
        }
    }
}
