package io.github.mralex1810.fantasy.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "telegram.bot")
data class TelegramProperties(
    var token: String = "",
    var notifications: Notifications = Notifications(),
) {
    data class Notifications(
        /** When false, series finalization does not send Bot API messages (token may still be set for TMA). */
        var enabled: Boolean = true,
    )
}
