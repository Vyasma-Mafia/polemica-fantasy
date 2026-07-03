package io.github.mralex1810.fantasy.event

import io.github.mralex1810.fantasy.entity.NotificationCategory
import io.github.mralex1810.fantasy.service.NotificationDeliveryService
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class AdminFantikiGrantNotificationListener(
    private val notificationDeliveryService: NotificationDeliveryService,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    fun onAdminFantikiGrant(event: AdminFantikiGrantNotificationEvent) {
        notificationDeliveryService.deliver(
            telegramChatId = event.telegramUserId,
            category = NotificationCategory.ADMIN_BROADCAST,
            text = buildAdminFantikiGrantTelegramMessage(event),
        )
    }
}

fun buildAdminFantikiGrantTelegramMessage(event: AdminFantikiGrantNotificationEvent): String = buildString {
    append("Сообщение от администрации\n")
    append("Начислено: ${event.amount} ₣.\n")
    append("Баланс: ${event.balanceAfter} ₣.\n")
    append("Причина: ${event.adminReason}")
}
