package io.github.mralex1810.fantasy.event

import io.github.mralex1810.fantasy.entity.NotificationCategory
import io.github.mralex1810.fantasy.service.NotificationDeliveryService
import io.github.mralex1810.fantasy.service.UserService
import io.github.mralex1810.fantasy.telegram.NotificationButtonFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class MarketplaceSaleNotificationListener(
    private val notificationDeliveryService: NotificationDeliveryService,
    private val notificationButtonFactory: NotificationButtonFactory,
    private val userService: UserService,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    fun onMarketplaceSale(event: MarketplaceSaleNotificationEvent) {
        val newBalance = userService.getBalance(event.sellerInternalUserId)
        val text = buildString {
            append("Карта «${event.playerName}» (${event.rarity}) куплена за ${event.price} ₣.\n")
            append("Вы получили ${event.sellerReceived} ₣ (комиссия ${event.commission} ₣).\n")
            append("Баланс: $newBalance ₣.")
        }
        notificationDeliveryService.deliver(
            telegramChatId = event.sellerTelegramChatId,
            category = NotificationCategory.MARKETPLACE_SALE,
            text = text,
            replyMarkup = notificationButtonFactory.openCardsButton(),
        )
    }
}
