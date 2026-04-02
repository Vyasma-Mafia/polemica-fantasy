package io.github.mralex1810.fantasy.event

import io.github.mralex1810.fantasy.entity.TelegramUser

/** Same resolution as webapp `formatUserDisplayName`: displayName, firstName, username, telegramId. */
internal fun TelegramUser.publicDisplayNameForNotifications(): String =
    displayName?.trim()?.takeIf { it.isNotEmpty() }
        ?: firstName?.trim()?.takeIf { it.isNotEmpty() }
        ?: username?.trim()?.takeIf { it.isNotEmpty() }
        ?: telegramId.toString()
