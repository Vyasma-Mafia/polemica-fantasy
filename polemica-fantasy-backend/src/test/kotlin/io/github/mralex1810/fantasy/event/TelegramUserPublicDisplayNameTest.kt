package io.github.mralex1810.fantasy.event

import io.github.mralex1810.fantasy.entity.TelegramUser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TelegramUserPublicDisplayNameTest {

    @Test
    fun `prefers displayName then firstName then username then telegramId`() {
        assertEquals(
            "Nick",
            TelegramUser(telegramId = 99L, displayName = "Nick").publicDisplayNameForNotifications(),
        )
        assertEquals(
            "Ivan",
            TelegramUser(telegramId = 99L, firstName = "Ivan").publicDisplayNameForNotifications(),
        )
        assertEquals(
            "user",
            TelegramUser(telegramId = 99L, username = "user").publicDisplayNameForNotifications(),
        )
        assertEquals(
            "42",
            TelegramUser(telegramId = 42L).publicDisplayNameForNotifications(),
        )
    }
}
