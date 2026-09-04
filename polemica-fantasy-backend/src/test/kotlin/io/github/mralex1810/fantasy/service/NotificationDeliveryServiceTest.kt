package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.config.TelegramProperties
import io.github.mralex1810.fantasy.entity.NotificationCategory
import io.github.mralex1810.fantasy.entity.TelegramUser
import io.github.mralex1810.fantasy.observability.FantasyMetrics
import io.github.mralex1810.fantasy.repository.NotificationPreferenceRepository
import io.github.mralex1810.fantasy.repository.TelegramUserRepository
import io.github.mralex1810.fantasy.telegram.TelegramBotApiClient
import io.github.mralex1810.fantasy.telegram.TelegramSendResult
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class NotificationDeliveryServiceTest {
    private lateinit var telegramBotApiClient: TelegramBotApiClient
    private lateinit var telegramProperties: TelegramProperties
    private lateinit var telegramUserRepository: TelegramUserRepository
    private lateinit var notificationPreferenceRepository: NotificationPreferenceRepository
    private lateinit var registry: SimpleMeterRegistry
    private lateinit var service: NotificationDeliveryService

    @BeforeEach
    fun setup() {
        telegramBotApiClient = mock(TelegramBotApiClient::class.java)
        telegramProperties = TelegramProperties().apply {
            token = "token"
            notifications.enabled = true
        }
        telegramUserRepository = mock(TelegramUserRepository::class.java)
        notificationPreferenceRepository = mock(NotificationPreferenceRepository::class.java)
        registry = SimpleMeterRegistry()
        service = NotificationDeliveryService(
            telegramBotApiClient = telegramBotApiClient,
            telegramProperties = telegramProperties,
            telegramUserRepository = telegramUserRepository,
            notificationPreferenceRepository = notificationPreferenceRepository,
            fantasyMetrics = FantasyMetrics(registry),
        )
    }

    @Test
    fun `automated agent is never sent a telegram notification`() {
        val user = TelegramUser(id = 11L, telegramId = 101L, isAutomatedAgent = true)
        whenever(telegramUserRepository.findByTelegramId(101L)).thenReturn(user)

        val report = service.deliverToMany(
            recipients = listOf(101L),
            category = NotificationCategory.ADMIN_BROADCAST,
            textProvider = { "hidden experiment" },
        )

        assertEquals(0, report.sent)
        assertEquals(1, report.skippedAutomatedAgent)
        verifyNoInteractions(telegramBotApiClient, notificationPreferenceRepository)
        assertEquals(
            1.0,
            registry.get("fantasy.notification.deliveries")
                .tags("category", "admin_broadcast", "outcome", "automated_agent_skipped")
                .counter().count(),
        )
    }

    @Test
    fun `rate limit followed by successful retry records one sent outcome`() {
        val user = TelegramUser(id = 10L, telegramId = 100L)
        whenever(telegramUserRepository.findByTelegramId(100L)).thenReturn(user)
        whenever(notificationPreferenceRepository.findByTelegramUser_IdAndCategory(10L, NotificationCategory.SERIES_FINALIZED))
            .thenReturn(null)
        whenever(
            telegramBotApiClient.sendMessageSafe(
                botToken = eq("token"),
                chatId = eq(100L),
                text = eq("done"),
                parseMode = isNull(),
                replyMarkup = isNull(),
            ),
        ).thenReturn(
            TelegramSendResult.RateLimited(retryAfterSeconds = 0),
            TelegramSendResult.Success,
        )

        val report = service.deliverToMany(
            recipients = listOf(100L),
            category = NotificationCategory.SERIES_FINALIZED,
            textProvider = { "done" },
        )

        assertEquals(1, report.sent)
        assertEquals(0, report.failed)
        assertEquals(
            1.0,
            registry.get("fantasy.notification.deliveries")
                .tags("category", "series_finalized", "outcome", "sent")
                .counter().count(),
        )
        assertEquals(1, registry.find("fantasy.notification.deliveries").counters().size)
    }

    @Test
    fun `global disable records every recipient without calling telegram`() {
        telegramProperties.notifications.enabled = false

        service.deliverToMany(
            recipients = listOf(100L, 200L, 300L),
            category = NotificationCategory.ADMIN_BROADCAST,
            textProvider = { "notice" },
        )

        assertEquals(
            3.0,
            registry.get("fantasy.notification.deliveries")
                .tags("category", "admin_broadcast", "outcome", "globally_disabled")
                .counter().count(),
        )
    }

    @Test
    fun `permanently unavailable recipient is suppressed without recording an error`() {
        val user = TelegramUser(id = 10L, telegramId = 100L)
        whenever(telegramUserRepository.findByTelegramId(100L)).thenReturn(user)
        whenever(notificationPreferenceRepository.findByTelegramUser_IdAndCategory(10L, NotificationCategory.ONBOARDING_TIPS))
            .thenReturn(null)
        whenever(
            telegramBotApiClient.sendMessageSafe(
                botToken = eq("token"),
                chatId = eq(100L),
                text = eq("tip"),
                parseMode = isNull(),
                replyMarkup = isNull(),
            ),
        ).thenReturn(TelegramSendResult.BotBlocked("Bad Request: chat not found"))

        val report = service.deliverToMany(
            recipients = listOf(100L),
            category = NotificationCategory.ONBOARDING_TIPS,
            textProvider = { "tip" },
        )

        assertEquals(0, report.failed)
        assertEquals(1, report.skippedBlocked)
        assertEquals(true, user.botBlocked)
        verify(telegramUserRepository).save(user)
        assertEquals(
            1.0,
            registry.get("fantasy.notification.deliveries")
                .tags("category", "onboarding_tips", "outcome", "bot_blocked")
                .counter().count(),
        )
        assertEquals(0, registry.find("fantasy.notification.deliveries")
            .tags("category", "onboarding_tips", "outcome", "error")
            .counters().size)
    }

    @Test
    fun `preparation failure records error and does not abort remaining recipients`() {
        whenever(telegramUserRepository.findByTelegramId(100L))
            .thenReturn(TelegramUser(id = 10L, telegramId = 100L))
        whenever(telegramUserRepository.findByTelegramId(200L))
            .thenReturn(TelegramUser(id = 20L, telegramId = 200L))
        whenever(
            telegramBotApiClient.sendMessageSafe(
                botToken = eq("token"),
                chatId = eq(200L),
                text = eq("ok"),
                parseMode = isNull(),
                replyMarkup = isNull(),
            ),
        ).thenReturn(TelegramSendResult.Success)

        val report = service.deliverToMany(
            recipients = listOf(100L, 200L),
            category = NotificationCategory.ADMIN_BROADCAST,
            textProvider = { chatId ->
                if (chatId == 100L) error("template failed") else "ok"
            },
        )

        assertEquals(1, report.failed)
        assertEquals(1, report.sent)
        assertEquals(
            1.0,
            registry.get("fantasy.notification.deliveries")
                .tags("category", "admin_broadcast", "outcome", "error")
                .counter().count(),
        )
        assertEquals(
            1.0,
            registry.get("fantasy.notification.deliveries")
                .tags("category", "admin_broadcast", "outcome", "sent")
                .counter().count(),
        )
    }
}
