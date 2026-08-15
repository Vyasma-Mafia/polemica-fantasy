package io.github.mralex1810.fantasy.event

import io.github.mralex1810.fantasy.entity.NotificationCategory
import io.github.mralex1810.fantasy.entity.TelegramUser
import io.github.mralex1810.fantasy.repository.TelegramUserRepository
import io.github.mralex1810.fantasy.repository.TournamentSubscriptionRepository
import io.github.mralex1810.fantasy.service.NotificationDeliveryService
import io.github.mralex1810.fantasy.telegram.NotificationButtonFactory
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.time.Instant

class SeriesStartNotificationListenerTest {
    private lateinit var notificationDeliveryService: NotificationDeliveryService
    private lateinit var notificationButtonFactory: NotificationButtonFactory
    private lateinit var telegramUserRepository: TelegramUserRepository
    private lateinit var tournamentSubscriptionRepository: TournamentSubscriptionRepository
    private lateinit var listener: SeriesStartNotificationListener

    @BeforeEach
    fun setUp() {
        notificationDeliveryService = mock()
        notificationButtonFactory = mock()
        telegramUserRepository = mock()
        tournamentSubscriptionRepository = mock()
        listener = SeriesStartNotificationListener(
            notificationDeliveryService,
            notificationButtonFactory,
            telegramUserRepository,
            tournamentSubscriptionRepository,
        )
    }

    @Test
    fun `does not notify when all started series deadlines have passed`() {
        listener.onSeriesBatchStarted(
            SeriesBatchStartedEvent(
                startedSeries = listOf(startedSeriesInfo(1L, 10L, Instant.parse("2020-01-01T00:00:00Z"))),
            ),
        )

        verifyNoInteractions(
            telegramUserRepository,
            tournamentSubscriptionRepository,
            notificationButtonFactory,
            notificationDeliveryService,
        )
    }

    @Test
    fun `mixed batch notification contains only series with open deadline`() {
        val recipient = TelegramUser(id = 7L, telegramId = 700L)
        whenever(telegramUserRepository.findAllEligibleForSeriesStart(setOf(20L))).thenReturn(listOf(recipient))
        whenever(tournamentSubscriptionRepository.findUserTournamentPairsByTelegramUserIds(listOf(7L)))
            .thenReturn(emptyList())

        listener.onSeriesBatchStarted(
            SeriesBatchStartedEvent(
                startedSeries = listOf(
                    startedSeriesInfo(1L, 10L, Instant.parse("2020-01-01T00:00:00Z")),
                    startedSeriesInfo(2L, 20L, Instant.parse("2100-01-01T00:00:00Z")),
                ),
            ),
        )

        verify(telegramUserRepository).findAllEligibleForSeriesStart(setOf(20L))
        verify(notificationButtonFactory).submitTeamButton(2L)
        verify(notificationButtonFactory, never()).submitTeamButton(1L)
        verify(notificationDeliveryService).deliver(
            telegramChatId = eq(700L),
            category = eq(NotificationCategory.SERIES_START),
            text = any(),
            parseMode = isNull(),
            replyMarkup = isNull(),
        )
    }

    private fun startedSeriesInfo(seriesId: Long, tournamentId: Long, deadline: Instant) = StartedSeriesInfo(
        seriesId = seriesId,
        seriesName = "Series $seriesId",
        tournamentId = tournamentId,
        tournamentName = "Tournament $tournamentId",
        teamDeadline = deadline,
    )
}
