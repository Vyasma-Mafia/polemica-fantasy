package io.github.mralex1810.fantasy.schedule

import io.github.mralex1810.fantasy.entity.Series
import io.github.mralex1810.fantasy.entity.SeriesStatus
import io.github.mralex1810.fantasy.observability.FantasyMetrics
import io.github.mralex1810.fantasy.repository.SeriesRepository
import io.github.mralex1810.fantasy.service.SeriesService
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.whenever

class ActiveSeriesSyncSchedulerTest {
    private lateinit var seriesRepository: SeriesRepository
    private lateinit var seriesService: SeriesService
    private lateinit var registry: SimpleMeterRegistry
    private lateinit var scheduler: ActiveSeriesSyncScheduler

    @BeforeEach
    fun setup() {
        seriesRepository = mock(SeriesRepository::class.java)
        seriesService = mock(SeriesService::class.java)
        registry = SimpleMeterRegistry()
        scheduler = ActiveSeriesSyncScheduler(
            seriesRepository = seriesRepository,
            seriesService = seriesService,
            fantasyMetrics = FantasyMetrics(registry),
        )
    }

    @Test
    fun `mixed series results record partial run and per-status active counts`() {
        val active = Series(status = SeriesStatus.ACTIVE).apply { id = 1L }
        val scoring = Series(status = SeriesStatus.SCORING).apply { id = 2L }
        whenever(
            seriesRepository.findAllByStatusInAndFinalizedIsFalse(
                listOf(SeriesStatus.ACTIVE, SeriesStatus.SCORING),
            ),
        ).thenReturn(listOf(active, scoring))
        doThrow(IllegalStateException("upstream failed")).whenever(seriesService).syncGames(2L)

        scheduler.syncGamesAndCalculateScores()

        assertEquals(
            1.0,
            registry.get("fantasy.scheduler.runs")
                .tags("scheduler", "active_series_sync", "result", "partial")
                .counter().count(),
        )
        assertEquals(
            1.0,
            registry.get("fantasy.scheduler.items")
                .tags("scheduler", "active_series_sync", "result", "success")
                .counter().count(),
        )
        assertEquals(
            1.0,
            registry.get("fantasy.scheduler.items")
                .tags("scheduler", "active_series_sync", "result", "failure")
                .counter().count(),
        )
        assertEquals(1.0, registry.get("fantasy.active.series.count").tag("status", "active").gauge().value())
        assertEquals(1.0, registry.get("fantasy.active.series.count").tag("status", "scoring").gauge().value())
    }

    @Test
    fun `no active series records healthy idle run`() {
        whenever(
            seriesRepository.findAllByStatusInAndFinalizedIsFalse(
                listOf(SeriesStatus.ACTIVE, SeriesStatus.SCORING),
            ),
        ).thenReturn(emptyList())

        scheduler.syncGamesAndCalculateScores()

        assertEquals(
            1.0,
            registry.get("fantasy.scheduler.runs")
                .tags("scheduler", "active_series_sync", "result", "idle")
                .counter().count(),
        )
        assertEquals(0.0, registry.get("fantasy.active.series.count").tag("status", "active").gauge().value())
        assertEquals(0.0, registry.get("fantasy.active.series.count").tag("status", "scoring").gauge().value())
        assertEquals(
            registry.get("fantasy.scheduler.last.completion.timestamp")
                .tag("scheduler", "active_series_sync").gauge().value(),
            registry.get("fantasy.scheduler.last.success.timestamp")
                .tag("scheduler", "active_series_sync").gauge().value(),
        )
    }

    @Test
    fun `repository failure records completion without success on cold start`() {
        whenever(
            seriesRepository.findAllByStatusInAndFinalizedIsFalse(
                listOf(SeriesStatus.ACTIVE, SeriesStatus.SCORING),
            ),
        ).thenThrow(IllegalStateException("database unavailable"))

        assertThrows(IllegalStateException::class.java) {
            scheduler.syncGamesAndCalculateScores()
        }

        assertEquals(
            1.0,
            registry.get("fantasy.scheduler.runs")
                .tags("scheduler", "active_series_sync", "result", "failure")
                .counter().count(),
        )
        assertEquals(
            0.0,
            registry.get("fantasy.scheduler.last.success.timestamp")
                .tag("scheduler", "active_series_sync").gauge().value(),
        )
        assertEquals(0.0, registry.get("fantasy.active.series.count").tag("status", "active").gauge().value())
        assertEquals(0.0, registry.get("fantasy.active.series.count").tag("status", "scoring").gauge().value())
        assertTrue(
            registry.get("fantasy.scheduler.last.completion.timestamp")
                .tag("scheduler", "active_series_sync").gauge().value() > 0,
        )
    }
}
