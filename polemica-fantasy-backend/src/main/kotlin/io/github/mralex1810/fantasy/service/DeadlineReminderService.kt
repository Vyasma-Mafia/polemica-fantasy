package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.entity.DeadlineReminder
import io.github.mralex1810.fantasy.entity.NotificationCategory
import io.github.mralex1810.fantasy.entity.Series
import io.github.mralex1810.fantasy.entity.SeriesStatus
import io.github.mralex1810.fantasy.repository.DeadlineReminderRepository
import io.github.mralex1810.fantasy.repository.TelegramUserRepository
import io.github.mralex1810.fantasy.telegram.NotificationButtonFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val REMINDER_ZONE_ID: ZoneId = ZoneId.of("Europe/Moscow")
private val REMINDER_LOCALE: Locale = Locale.forLanguageTag("ru")
private val REMINDER_DEADLINE_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", REMINDER_LOCALE)

@Service
class DeadlineReminderService(
    private val deadlineReminderRepository: DeadlineReminderRepository,
    private val notificationDeliveryService: NotificationDeliveryService,
    private val notificationButtonFactory: NotificationButtonFactory,
    private val telegramUserRepository: TelegramUserRepository,
) {
    @Transactional
    fun sendReminder(reminderId: Long) {
        val reminder = deadlineReminderRepository.findById(reminderId).orElse(null) ?: return
        if (reminder.sent) {
            return
        }

        val series = reminder.series ?: run {
            markSentWithoutSending(reminder)
            return
        }
        if (series.status == SeriesStatus.FINISHED || series.status == SeriesStatus.SCORING) {
            markSentWithoutSending(reminder)
            return
        }

        val seriesId = series.id ?: run {
            markSentWithoutSending(reminder)
            return
        }
        val tournamentId = series.tournament?.id ?: run {
            markSentWithoutSending(reminder)
            return
        }

        val recipients = telegramUserRepository.findDeadlineReminderRecipients(
            seriesId = seriesId,
            tournamentId = tournamentId,
        )
        if (recipients.isEmpty()) {
            markSentWithoutSending(reminder)
            return
        }

        val text = buildReminderMessage(series)
        val replyMarkup = notificationButtonFactory.submitTeamButton(seriesId)
        var sentCount = 0
        for (chatId in recipients) {
            val delivered = notificationDeliveryService.deliver(
                telegramChatId = chatId,
                category = NotificationCategory.TEAM_DEADLINE_REMINDER,
                text = text,
                replyMarkup = replyMarkup,
            )
            if (delivered) {
                sentCount++
            }
        }

        reminder.sent = true
        reminder.sentAt = Instant.now()
        reminder.recipientCount = sentCount
        deadlineReminderRepository.save(reminder)
    }

    private fun markSentWithoutSending(reminder: DeadlineReminder) {
        reminder.sent = true
        reminder.sentAt = Instant.now()
        reminder.recipientCount = 0
        deadlineReminderRepository.save(reminder)
    }

    private fun buildReminderMessage(series: Series): String = buildString {
        append("\u23F0 Через час истекает дедлайн подачи команды!\n\n")
        append("Серия: ${series.tournament!!.name} — ${series.name}\n")
        val formattedDeadline = REMINDER_DEADLINE_FORMATTER
            .withZone(REMINDER_ZONE_ID)
            .format(series.teamDeadline)
        append("Дедлайн: $formattedDeadline МСК\n\n")
        append("У вас ещё нет команды в этой серии.")
    }
}
