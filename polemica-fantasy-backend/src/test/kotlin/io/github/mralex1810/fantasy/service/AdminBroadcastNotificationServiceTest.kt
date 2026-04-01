package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.config.TelegramProperties
import io.github.mralex1810.fantasy.repository.TelegramUserRepository
import io.github.mralex1810.fantasy.telegram.TelegramBroadcastAsyncSender
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.web.server.ResponseStatusException

class AdminBroadcastNotificationServiceTest {

    private lateinit var telegramProperties: TelegramProperties
    private lateinit var telegramUserRepository: TelegramUserRepository
    private lateinit var telegramBroadcastAsyncSender: TelegramBroadcastAsyncSender
    private lateinit var service: AdminBroadcastNotificationService

    @BeforeEach
    fun setup() {
        telegramProperties = TelegramProperties()
        telegramProperties.token = "bot-token"
        telegramProperties.notifications.enabled = true
        telegramUserRepository = mock(TelegramUserRepository::class.java)
        telegramBroadcastAsyncSender = mock(TelegramBroadcastAsyncSender::class.java)
        service = AdminBroadcastNotificationService(
            telegramProperties,
            telegramUserRepository,
            telegramBroadcastAsyncSender,
        )
    }

    @Test
    fun `queueBroadcast throws when notifications disabled`() {
        telegramProperties.notifications.enabled = false
        assertThrows(ResponseStatusException::class.java) {
            service.queueBroadcast("hello")
        }
    }

    @Test
    fun `queueBroadcast throws when token blank`() {
        telegramProperties.token = "   "
        assertThrows(ResponseStatusException::class.java) {
            service.queueBroadcast("hello")
        }
    }

    @Test
    fun `queueBroadcast returns zero when no users`() {
        `when`(telegramUserRepository.findAllTelegramIds()).thenReturn(emptyList())
        assertEquals(0L, service.queueBroadcast("hello"))
    }

    @Test
    fun `queueBroadcast returns count when users exist`() {
        `when`(telegramUserRepository.findAllTelegramIds()).thenReturn(listOf(10L, 20L))
        assertEquals(2L, service.queueBroadcast("msg"))
    }
}
