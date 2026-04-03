package io.github.mralex1810.fantasy.telegram

import io.github.mralex1810.fantasy.config.TelegramProperties
import org.springframework.stereotype.Component

@Component
class TelegramSupportBotIdentity(
    private val telegramProperties: TelegramProperties,
    private val telegramBotApiClient: TelegramBotApiClient,
) {

    @Volatile
    private var cachedBotUserId: Long? = null

    /**
     * Telegram `User.id` of this bot (from getMe), cached.
     */
    fun botUserId(): Long {
        cachedBotUserId?.let { return it }
        synchronized(this) {
            cachedBotUserId?.let { return it }
            val token = telegramProperties.token.trim()
            require(token.isNotEmpty()) { "TELEGRAM_BOT_TOKEN is empty" }
            val id = telegramBotApiClient.getMe(token)
            cachedBotUserId = id
            return id
        }
    }
}
