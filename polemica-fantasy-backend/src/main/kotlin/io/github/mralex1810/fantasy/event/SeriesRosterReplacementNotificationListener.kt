package io.github.mralex1810.fantasy.event

import io.github.mralex1810.fantasy.entity.NotificationCategory
import io.github.mralex1810.fantasy.service.NotificationDeliveryService
import io.github.mralex1810.fantasy.telegram.NotificationButtonFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class SeriesRosterReplacementNotificationListener(
    private val notificationDeliveryService: NotificationDeliveryService,
    private val notificationButtonFactory: NotificationButtonFactory,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    fun onSeriesRosterReplacement(event: SeriesRosterReplacementNotificationEvent) {
        for (recipient in event.recipients) {
            notificationDeliveryService.deliver(
                telegramChatId = recipient.telegramChatId,
                category = NotificationCategory.SERIES_ROSTER_CHANGE,
                text = recipient.messageText,
                replyMarkup = notificationButtonFactory.openCardsButton(),
            )
        }
    }
}
