package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.entity.TelegramUser

/** displayName, firstName, username, or telegramId — same as TMA `formatUserDisplayName`. */
fun TelegramUser.publicDisplayName(): String =
    displayName?.trim()?.takeIf { it.isNotEmpty() }
        ?: firstName?.trim()?.takeIf { it.isNotEmpty() }
        ?: username?.trim()?.takeIf { it.isNotEmpty() }
        ?: telegramId.toString()
