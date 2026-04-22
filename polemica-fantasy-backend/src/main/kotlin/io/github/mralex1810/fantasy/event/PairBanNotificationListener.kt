package io.github.mralex1810.fantasy.event

import io.github.mralex1810.fantasy.config.TelegramProperties
import io.github.mralex1810.fantasy.telegram.TelegramBotApiClient
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class PairBanNotificationListener(
    private val telegramProperties: TelegramProperties,
    private val telegramBotApiClient: TelegramBotApiClient,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    fun onPairBan(event: PairBanNotificationEvent) {
        val token = telegramProperties.token
        if (!telegramProperties.notifications.enabled || token.isBlank()) {
            log.debug(
                "Pair ban Telegram notification skipped (enabled={}, token blank={})",
                telegramProperties.notifications.enabled,
                token.isBlank(),
            )
            return
        }
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
        try {
            telegramBotApiClient.sendMessage(token, event.telegramChatId, text)
        } catch (e: Exception) {
            log.warn(
                "Failed to send pair ban Telegram message to chatId={}",
                event.telegramChatId,
                e,
            )
        }
    }
}
