package io.github.mralex1810.fantasy.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("app.rating")
data class AppRatingProperties(
    /** Telegram user ids excluded from the global rating (e.g. admins). */
    val excludedTelegramIds: Set<Long> = emptySet(),
) {
    fun isExcludedFromRating(telegramId: Long): Boolean = telegramId in excludedTelegramIds
}
