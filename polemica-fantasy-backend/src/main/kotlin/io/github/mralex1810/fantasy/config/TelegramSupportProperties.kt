package io.github.mralex1810.fantasy.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "telegram.support")
data class TelegramSupportProperties(
    /** When false, webhook accepts POST but does not process updates. */
    var enabled: Boolean = false,
    /** Supergroup id (e.g. -100…) with forum topics enabled. */
    var forumChatId: Long = 0L,
    /** Must match Telegram setWebhook secret_token; header X-Telegram-Bot-Api-Secret-Token. */
    var webhookSecret: String = "",
)
