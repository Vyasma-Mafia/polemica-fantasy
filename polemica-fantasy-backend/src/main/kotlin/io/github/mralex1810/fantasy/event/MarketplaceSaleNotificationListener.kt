package io.github.mralex1810.fantasy.event

import io.github.mralex1810.fantasy.config.TelegramProperties
import io.github.mralex1810.fantasy.service.UserService
import io.github.mralex1810.fantasy.telegram.TelegramBotApiClient
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class MarketplaceSaleNotificationListener(
    private val telegramProperties: TelegramProperties,
    private val telegramBotApiClient: TelegramBotApiClient,
    private val userService: UserService,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    fun onMarketplaceSale(event: MarketplaceSaleNotificationEvent) {
        val token = telegramProperties.token
        if (!telegramProperties.notifications.enabled || token.isBlank()) {
            log.debug(
                "Marketplace sale Telegram notification skipped (enabled={}, token blank={})",
                telegramProperties.notifications.enabled,
                token.isBlank(),
            )
            return
        }
        val newBalance = userService.getBalance(event.sellerInternalUserId)
        val text = buildString {
            append("Карта «${event.playerName}» (${event.rarity}) куплена за ${event.price} ₣.\n")
            append("Вы получили ${event.sellerReceived} ₣ (комиссия ${event.commission} ₣).\n")
            append("Баланс: $newBalance ₣.")
        }
        try {
            telegramBotApiClient.sendMessage(token, event.sellerTelegramChatId, text)
        } catch (e: Exception) {
            log.warn(
                "Failed to send marketplace sale Telegram message to chatId={} listing context sellerInternal={}",
                event.sellerTelegramChatId,
                event.sellerInternalUserId,
                e,
            )
        }
    }
}
