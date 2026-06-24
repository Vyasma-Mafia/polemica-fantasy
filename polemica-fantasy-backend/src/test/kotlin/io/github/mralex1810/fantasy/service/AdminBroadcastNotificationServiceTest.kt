package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.config.TelegramProperties
import io.github.mralex1810.fantasy.entity.NotificationCategory
import io.github.mralex1810.fantasy.entity.TelegramUser
import io.github.mralex1810.fantasy.repository.TelegramUserRepository
import io.github.mralex1810.fantasy.telegram.TelegramBroadcastAsyncSender
import io.github.mralex1810.fantasy.telegram.TelegramBotApiClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.whenever
import org.springframework.web.server.ResponseStatusException

class AdminBroadcastNotificationServiceTest {

    private lateinit var telegramProperties: TelegramProperties
    private lateinit var telegramUserRepository: TelegramUserRepository
    private lateinit var telegramBroadcastAsyncSender: TelegramBroadcastAsyncSender
    private lateinit var notificationDeliveryService: NotificationDeliveryService
    private lateinit var service: AdminBroadcastNotificationService

    @BeforeEach
    fun setup() {
        telegramProperties = TelegramProperties()
        telegramProperties.token = "bot-token"
        telegramProperties.notifications.enabled = true
        telegramUserRepository = mock(TelegramUserRepository::class.java)
        telegramBroadcastAsyncSender = mock(TelegramBroadcastAsyncSender::class.java)
        notificationDeliveryService = mock(NotificationDeliveryService::class.java)
        service = AdminBroadcastNotificationService(
            telegramProperties,
            telegramUserRepository,
            telegramBroadcastAsyncSender,
            notificationDeliveryService,
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

    @Test
    fun `sendDirect throws when user does not exist`() {
        `when`(telegramUserRepository.findByTelegramId(123L)).thenReturn(null)

        assertThrows(ResponseStatusException::class.java) {
            service.sendDirect(123L, "hello")
        }
    }

    @Test
    fun `sendDirect returns delivery result`() {
        `when`(telegramUserRepository.findByTelegramId(123L)).thenReturn(
            TelegramUser(id = 1L, telegramId = 123L),
        )
        whenever(
            notificationDeliveryService.deliverToMany(
                recipients = eq(listOf(123L)),
                category = eq(NotificationCategory.ADMIN_BROADCAST),
                textProvider = any(),
                parseMode = eq(TelegramBotApiClient.PARSE_MODE_MARKDOWN_V2),
                replyMarkup = isNull(),
            ),
        ).thenReturn(DeliveryReport(sent = 1))

        val result = service.sendDirect(123L, "hello")

        assertEquals(123L, result.telegramUserId)
        assertTrue(result.sent)
        assertFalse(result.skippedBlocked)
        assertFalse(result.skippedPreference)
        assertFalse(result.failed)
    }
}
