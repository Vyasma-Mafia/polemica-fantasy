package io.github.mralex1810.fantasy.event

import io.github.mralex1810.fantasy.entity.NotificationCategory
import io.github.mralex1810.fantasy.service.NotificationDeliveryService
import io.github.mralex1810.fantasy.telegram.NotificationButtonFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class SeriesFinalizedNotificationListener(
    private val notificationDeliveryService: NotificationDeliveryService,
    private val notificationButtonFactory: NotificationButtonFactory,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    fun onSeriesFinalized(event: SeriesFinalizedNotificationEvent) {
        for (recipient in event.recipients) {
            val text = buildSeriesFinalizedTelegramMessage(
                event.tournamentName,
                event.seriesName,
                recipient,
            )
            notificationDeliveryService.deliver(
                telegramChatId = recipient.telegramId,
                category = NotificationCategory.SERIES_FINALIZED,
                text = text,
                replyMarkup = notificationButtonFactory.openSeriesLeaderboardButton(event.seriesId),
            )
        }
    }
}
