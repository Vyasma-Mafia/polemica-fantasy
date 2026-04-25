package io.github.mralex1810.fantasy.event

import io.github.mralex1810.fantasy.entity.NotificationCategory
import io.github.mralex1810.fantasy.repository.TelegramUserRepository
import io.github.mralex1810.fantasy.repository.TournamentSubscriptionRepository
import io.github.mralex1810.fantasy.service.NotificationDeliveryService
import io.github.mralex1810.fantasy.telegram.NotificationButtonFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val MOSCOW_ZONE_ID: ZoneId = ZoneId.of("Europe/Moscow")
private val RU_LOCALE: Locale = Locale.forLanguageTag("ru")
private val SINGLE_SERIES_DEADLINE_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm 'МСК'", RU_LOCALE)
private val BATCH_SERIES_DEADLINE_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMMM, HH:mm", RU_LOCALE)

@Component
class SeriesStartNotificationListener(
    private val notificationDeliveryService: NotificationDeliveryService,
    private val notificationButtonFactory: NotificationButtonFactory,
    private val telegramUserRepository: TelegramUserRepository,
    private val tournamentSubscriptionRepository: TournamentSubscriptionRepository,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    fun onSeriesBatchStarted(event: SeriesBatchStartedEvent) {
        if (event.startedSeries.isEmpty()) {
            return
        }
        val tournamentIds = event.startedSeries.map { it.tournamentId }.toSet()
        val recipients = telegramUserRepository.findAllEligibleForSeriesStart(tournamentIds)
        if (recipients.isEmpty()) {
            return
        }
        val recipientIds = recipients.mapNotNull { it.id }
        val subscriptionRows = if (recipientIds.isEmpty()) {
            emptyList()
        } else {
            tournamentSubscriptionRepository.findUserTournamentPairsByTelegramUserIds(recipientIds)
        }
        val subscriptionsByUserId = mutableMapOf<Long, MutableSet<Long>>()
        for (row in subscriptionRows) {
            val userId = (row[0] as Number).toLong()
            val tournamentId = (row[1] as Number).toLong()
            subscriptionsByUserId.getOrPut(userId) { mutableSetOf() }.add(tournamentId)
        }

        for (recipient in recipients) {
            val userId = recipient.id ?: continue
            val tournamentSubscriptions = subscriptionsByUserId[userId]
            val relevantSeries = if (tournamentSubscriptions.isNullOrEmpty()) {
                event.startedSeries
            } else {
                event.startedSeries.filter { it.tournamentId in tournamentSubscriptions }
            }
            if (relevantSeries.isEmpty()) {
                continue
            }
            val text = buildSeriesStartNotificationText(relevantSeries)
            val replyMarkup = if (relevantSeries.size == 1) {
                notificationButtonFactory.submitTeamButton(relevantSeries.first().seriesId)
            } else {
                null
            }
            notificationDeliveryService.deliver(
                telegramChatId = recipient.telegramId,
                category = NotificationCategory.SERIES_START,
                text = text,
                replyMarkup = replyMarkup,
            )
        }
    }

    private fun buildSeriesStartNotificationText(startedSeries: List<StartedSeriesInfo>): String {
        if (startedSeries.size == 1) {
            val series = startedSeries.first()
            return buildString {
                append("🏁 Серия «${seriesDisplayName(series)}» началась!\n")
                append("Дедлайн подачи команды: ${formatSingleSeriesDeadline(series)}.")
            }
        }
        val bullets = startedSeries.joinToString("\n") { series ->
            "• ${seriesDisplayName(series)} (дедлайн: ${formatBatchSeriesDeadline(series)})"
        }
        return "🏁 Начались новые серии:\n\n$bullets\n\nПодавайте составы до дедлайна!"
    }

    private fun seriesDisplayName(series: StartedSeriesInfo): String = "${series.tournamentName} — ${series.seriesName}"

    private fun formatSingleSeriesDeadline(series: StartedSeriesInfo): String =
        series.teamDeadline
            ?.atZone(MOSCOW_ZONE_ID)
            ?.format(SINGLE_SERIES_DEADLINE_FORMATTER)
            ?: "не указан"

    private fun formatBatchSeriesDeadline(series: StartedSeriesInfo): String =
        series.teamDeadline
            ?.atZone(MOSCOW_ZONE_ID)
            ?.format(BATCH_SERIES_DEADLINE_FORMATTER)
            ?: "не указан"
}
