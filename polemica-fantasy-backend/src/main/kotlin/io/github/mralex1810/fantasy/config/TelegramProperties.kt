package io.github.mralex1810.fantasy.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "telegram.bot")
data class TelegramProperties(
    var token: String = "",
)
