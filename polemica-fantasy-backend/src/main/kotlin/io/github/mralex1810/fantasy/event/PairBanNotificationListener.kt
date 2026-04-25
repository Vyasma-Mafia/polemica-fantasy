package io.github.mralex1810.fantasy.event

import io.github.mralex1810.fantasy.entity.NotificationCategory
import io.github.mralex1810.fantasy.service.NotificationDeliveryService
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class PairBanNotificationListener(
    private val notificationDeliveryService: NotificationDeliveryService,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    fun onPairBan(event: PairBanNotificationEvent) {
        val cardsText =
            if (event.cardsConfiscated.isEmpty()) {
                "—"
            } else {
                event.cardsConfiscated.joinToString("\n")
            }
        val text = buildString {
            append("По отношению к вашему аккаунту применена санкция за нарушение правил (перелив). ")
            append("Доступ к маркетплейсу не блокируется, ваши текущие лоты остаются активными.\n\n")
            append("Причина: ${event.reason}\n\n")
            append("Конфискованы фантики: ${event.fantikiConfiscated} ₣\n")
            append("Конфискованы карты:\n$cardsText\n\n")
            append("Текущий баланс: ${event.newBalance} ₣\n\n")
            append("По вопросам обращайтесь в поддержку.")
        }
        notificationDeliveryService.deliver(
            telegramChatId = event.telegramChatId,
            category = NotificationCategory.PAIR_BAN,
            text = text,
        )
    }
}
