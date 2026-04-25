package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.entity.DeadlineReminder
import io.github.mralex1810.fantasy.entity.NotificationCategory
import io.github.mralex1810.fantasy.entity.Series
import io.github.mralex1810.fantasy.entity.SeriesStatus
import io.github.mralex1810.fantasy.entity.Tournament
import io.github.mralex1810.fantasy.repository.DeadlineReminderRepository
import io.github.mralex1810.fantasy.repository.TelegramUserRepository
import io.github.mralex1810.fantasy.telegram.InlineKeyboardButton
import io.github.mralex1810.fantasy.telegram.InlineKeyboardMarkup
import io.github.mralex1810.fantasy.telegram.NotificationButtonFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import java.time.Instant
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class DeadlineReminderServiceTest {
    @Mock
    private lateinit var deadlineReminderRepository: DeadlineReminderRepository

    @Mock
    private lateinit var notificationDeliveryService: NotificationDeliveryService

    @Mock
    private lateinit var notificationButtonFactory: NotificationButtonFactory

    @Mock
    private lateinit var telegramUserRepository: TelegramUserRepository

    @InjectMocks
    private lateinit var service: DeadlineReminderService

    @Test
    fun `marks reminder as sent without delivery for finished series`() {
        val reminder = buildReminder(seriesStatus = SeriesStatus.FINISHED)
        `when`(deadlineReminderRepository.findById(5L)).thenReturn(Optional.of(reminder))

        service.sendReminder(5L)

        assertEquals(true, reminder.sent)
        assertEquals(0, reminder.recipientCount)
        assertNotNull(reminder.sentAt)
        verify(deadlineReminderRepository).save(reminder)
        verifyNoInteractions(notificationDeliveryService, telegramUserRepository, notificationButtonFactory)
    }

    @Test
    fun `marks reminder as sent when no recipients`() {
        val reminder = buildReminder(seriesStatus = SeriesStatus.ACTIVE)
        `when`(deadlineReminderRepository.findById(5L)).thenReturn(Optional.of(reminder))
        `when`(telegramUserRepository.findDeadlineReminderRecipients(seriesId = 11L, tournamentId = 22L))
            .thenReturn(emptyList())

        service.sendReminder(5L)

        assertEquals(true, reminder.sent)
        assertEquals(0, reminder.recipientCount)
        assertNotNull(reminder.sentAt)
        verify(deadlineReminderRepository).save(reminder)
        verifyNoInteractions(notificationDeliveryService, notificationButtonFactory)
    }

    @Test
    fun `delivers reminder and stores delivered count`() {
        val reminder = buildReminder(seriesStatus = SeriesStatus.ACTIVE)
        val expectedText = """
            ⏰ Через час истекает дедлайн подачи команды!
            
            Серия: Кубок Полемики — Серия 5
            Дедлайн: 30 апреля 2026, 20:00 МСК
            
            У вас ещё нет команды в этой серии.
        """.trimIndent()
        val replyMarkup = InlineKeyboardMarkup(
            inlineKeyboard = listOf(
                listOf(
                    InlineKeyboardButton.WebApp(
                        text = "📝 Подать команду",
                        url = "https://fantasy.example/series/11/team",
                    ),
                ),
            ),
        )
        `when`(deadlineReminderRepository.findById(5L)).thenReturn(Optional.of(reminder))
        `when`(telegramUserRepository.findDeadlineReminderRecipients(seriesId = 11L, tournamentId = 22L))
            .thenReturn(listOf(101L, 202L, 303L))
        `when`(notificationButtonFactory.submitTeamButton(11L)).thenReturn(replyMarkup)
        `when`(
            notificationDeliveryService.deliver(
                telegramChatId = 101L,
                category = NotificationCategory.TEAM_DEADLINE_REMINDER,
                text = expectedText,
                parseMode = null,
                replyMarkup = replyMarkup,
            ),
        ).thenReturn(true)
        `when`(
            notificationDeliveryService.deliver(
                telegramChatId = 202L,
                category = NotificationCategory.TEAM_DEADLINE_REMINDER,
                text = expectedText,
                parseMode = null,
                replyMarkup = replyMarkup,
            ),
        ).thenReturn(false)
        `when`(
            notificationDeliveryService.deliver(
                telegramChatId = 303L,
                category = NotificationCategory.TEAM_DEADLINE_REMINDER,
                text = expectedText,
                parseMode = null,
                replyMarkup = replyMarkup,
            ),
        ).thenReturn(true)

        service.sendReminder(5L)

        assertEquals(true, reminder.sent)
        assertEquals(2, reminder.recipientCount)
        assertNotNull(reminder.sentAt)
        verify(deadlineReminderRepository).save(reminder)
    }

    private fun buildReminder(seriesStatus: SeriesStatus): DeadlineReminder {
        val tournament = Tournament(name = "Кубок Полемики").apply { id = 22L }
        val series = Series(
            tournament = tournament,
            name = "Серия 5",
            status = seriesStatus,
            startsAt = Instant.parse("2026-04-30T16:00:00Z"),
            teamDeadline = Instant.parse("2026-04-30T17:00:00Z"),
        ).apply {
            id = 11L
        }
        return DeadlineReminder(
            id = 5L,
            series = series,
            remindAt = Instant.parse("2026-04-30T16:00:00Z"),
            sent = false,
        )
    }
}
