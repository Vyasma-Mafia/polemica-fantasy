package io.github.mralex1810.fantasy.event

import io.github.mralex1810.fantasy.entity.NotificationCategory
import io.github.mralex1810.fantasy.service.NotificationDeliveryService
import io.github.mralex1810.fantasy.telegram.NotificationButtonFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class MarketplaceSanctionNotificationListener(
    private val notificationDeliveryService: NotificationDeliveryService,
    private val notificationButtonFactory: NotificationButtonFactory,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    fun onSanctionApplied(event: MarketplaceSanctionAppliedEvent) {
        val cardLabel = "«${event.playerName}» (${event.rarity})"
        val sellerText = buildString {
            append("⚠️ Сделка по карте $cardLabel за ${event.price} ₣ признана нерыночной.\n")
            append("Причина: ${event.reason}.\n")
            append("Штраф: −${event.sellerFine} ₣.\n")
            append("Баланс: ${event.sellerNewBalance} ₣.")
        }
        notificationDeliveryService.deliver(
            telegramChatId = event.sellerTelegramChatId,
            category = NotificationCategory.MARKETPLACE_SANCTION_APPLIED,
            text = sellerText,
            replyMarkup = notificationButtonFactory.openTransactionButton(event.listingId),
        )

        val buyerText = if (event.buyerFine > 0) {
            buildString {
                append("⚠️ Сделка по карте $cardLabel за ${event.price} ₣ признана нерыночной.\n")
                append("Причина: ${event.reason}.\n")
                append("Штраф: −${event.buyerFine} ₣.\n")
                append("Баланс: ${event.buyerNewBalance} ₣.")
            }
        } else {
            buildString {
                append("ℹ️ Сделка по карте $cardLabel за ${event.price} ₣ признана нерыночной.\n")
                append("Причина: ${event.reason}.\n")
                append("К вам штраф не применён.")
            }
        }
        notificationDeliveryService.deliver(
            telegramChatId = event.buyerTelegramChatId,
            category = NotificationCategory.MARKETPLACE_SANCTION_APPLIED,
            text = buyerText,
            replyMarkup = notificationButtonFactory.openTransactionButton(event.listingId),
        )

        for (complainant in event.complainants) {
            val text = buildString {
                append("✅ По вашей жалобе на сделку $cardLabel за ${event.price} ₣ принято решение.\n")
                append("Сделка признана нерыночной.\n")
                append("Награда: +${complainant.reward} ₣.\n")
                append("Баланс: ${complainant.newBalance} ₣.")
            }
            notificationDeliveryService.deliver(
                telegramChatId = complainant.telegramChatId,
                category = NotificationCategory.MARKETPLACE_COMPLAINT_RESOLVED,
                text = text,
                replyMarkup = notificationButtonFactory.openTransactionButton(event.listingId),
            )
        }
    }
}
