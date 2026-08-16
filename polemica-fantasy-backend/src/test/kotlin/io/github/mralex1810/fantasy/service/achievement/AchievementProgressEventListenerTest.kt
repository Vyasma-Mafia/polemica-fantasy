package io.github.mralex1810.fantasy.service.achievement

import io.github.mralex1810.fantasy.event.AchievementProgressEvent
import io.github.mralex1810.fantasy.event.AchievementProgressEventType
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.never
import org.mockito.Mockito.verify

class AchievementProgressEventListenerTest {
    private val progressService = mock(AchievementProgressService::class.java)
    private val listener = AchievementProgressEventListener(progressService)
    private val seriesFinalizationListener = SeriesFinalizationAchievementProgressEventListener(progressService)
    private val periodicRatingListener = PeriodicRatingAchievementProgressEventListener(progressService)

    @Test
    fun `period finalization targets rating conditions for every participant`() {
        periodicRatingListener.onPeriodicRatingFinalized(
            AchievementProgressEvent(
                type = AchievementProgressEventType.PERIODIC_RATING_FINALIZED,
                internalTelegramUserIds = setOf(11L, 22L, 33L),
            ),
        )

        val expectedConditions = setOf(
            "PERIODIC_RATING_PERIODS",
            "PERIODIC_RATING_TOP10",
            "PERIODIC_RATING_PODIUMS",
            "PERIODIC_RATING_WINS",
        )
        listOf(11L, 22L, 33L).forEach { userId ->
            verify(progressService).recomputeEnabledForUser(userId, expectedConditions)
            verify(progressService, never()).recomputeEnabledForUser(userId)
        }

        listener.onAchievementProgress(
            AchievementProgressEvent(
                type = AchievementProgressEventType.PERIODIC_RATING_FINALIZED,
                internalTelegramUserIds = setOf(11L, 22L, 33L),
            ),
        )
        listOf(11L, 22L, 33L).forEach { userId ->
            verify(progressService, never()).recomputeEnabledForUser(userId)
        }
    }

    @Test
    fun `other events keep full catalog recompute`() {
        listener.onAchievementProgress(
            AchievementProgressEvent(
                type = AchievementProgressEventType.TEAM_CHANGED,
                internalTelegramUserIds = setOf(44L),
            ),
        )

        verify(progressService).recomputeEnabledForUser(44L)
    }

    @Test
    fun `series finalization recompute is isolated from the generic listener and continues after one failure`() {
        val event = AchievementProgressEvent(
            type = AchievementProgressEventType.SERIES_FINALIZED,
            internalTelegramUserIds = linkedSetOf(55L, 66L),
        )
        doThrow(IllegalStateException("broken user")).`when`(progressService).recomputeEnabledForUser(55L)

        listener.onAchievementProgress(event)
        seriesFinalizationListener.onSeriesFinalized(event)

        verify(progressService).recomputeEnabledForUser(55L)
        verify(progressService).recomputeEnabledForUser(66L)
    }
}
