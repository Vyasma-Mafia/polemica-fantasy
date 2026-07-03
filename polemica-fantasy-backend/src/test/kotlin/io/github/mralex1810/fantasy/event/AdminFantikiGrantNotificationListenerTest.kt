package io.github.mralex1810.fantasy.event

import io.github.mralex1810.fantasy.entity.NotificationCategory
import io.github.mralex1810.fantasy.service.NotificationDeliveryService
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull

class AdminFantikiGrantNotificationListenerTest {

    private val notificationDeliveryService = mock(NotificationDeliveryService::class.java)
    private val listener = AdminFantikiGrantNotificationListener(notificationDeliveryService)

    @Test
    fun `delivers admin broadcast with amount balance and reason`() {
        listener.onAdminFantikiGrant(
            AdminFantikiGrantNotificationEvent(
                telegramUserId = 123L,
                amount = 250L,
                balanceAfter = 1250L,
                adminReason = "Manual promo",
            ),
        )

        verify(notificationDeliveryService).deliver(
            telegramChatId = eq(123L),
            category = eq(NotificationCategory.ADMIN_BROADCAST),
            text = eq(
                """
                Сообщение от администрации
                Начислено: 250 ₣.
                Баланс: 1250 ₣.
                Причина: Manual promo
                """.trimIndent(),
            ),
            parseMode = isNull(),
            replyMarkup = isNull(),
        )
    }
}
