package io.github.mralex1810.fantasy.observability

import io.github.mralex1810.fantasy.entity.NotificationCategory
import io.github.mralex1810.fantasy.entity.SeriesStatus
import io.github.mralex1810.fantasy.entity.TournamentKind
import io.github.mralex1810.fantasy.observability.FantasyMetrics.NotificationOutcome
import io.github.mralex1810.fantasy.observability.FantasyMetrics.OperationResult
import io.github.mralex1810.fantasy.observability.FantasyMetrics.SchedulerJob
import io.github.mralex1810.fantasy.observability.FantasyMetrics.SchedulerResult
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class FantasyMetricsTest {
    private val registry = SimpleMeterRegistry()
    private val clock = Clock.fixed(Instant.parse("2026-08-05T12:34:56Z"), ZoneOffset.UTC)
    private val metrics = FantasyMetrics(registry, clock)

    @Test
    fun `prometheus export uses expected fantasy metric families`() {
        val prometheusRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
        val prometheusMetrics = FantasyMetrics(prometheusRegistry, clock)

        prometheusMetrics.recordSeriesSync(
            prometheusMetrics.start(),
            TournamentKind.STANDALONE,
            OperationResult.SUCCESS,
            createdGames = 1,
            updatedGames = 1,
            empty = true,
        )
        prometheusMetrics.recordScoring(prometheusMetrics.start(), OperationResult.SUCCESS, gamesProcessed = 1)
        prometheusMetrics.recordSeriesFinalization(rewardedUsers = 1, cardUsesDecremented = 1)
        prometheusMetrics.recordNotificationDeliveries(NotificationCategory.SERIES_FINALIZED, NotificationOutcome.SENT)
        prometheusMetrics.recordActiveSeriesCounts(mapOf(SeriesStatus.ACTIVE to 1))
        prometheusMetrics.recordSchedulerRun(
            sample = prometheusMetrics.start(),
            job = SchedulerJob.ACTIVE_SERIES_SYNC,
            scheduledItems = 1,
            successfulItems = 1,
            failedItems = 0,
        )

        val exported = prometheusRegistry.scrape()
        val expectedFamilies = listOf(
            "fantasy_series_sync_duration_seconds_count",
            "fantasy_series_sync_games_total",
            "fantasy_series_sync_empty_total",
            "fantasy_scoring_duration_seconds_count",
            "fantasy_scoring_games_processed_total",
            "fantasy_series_finalizations_total",
            "fantasy_series_finalization_rewarded_users_total",
            "fantasy_series_finalization_card_uses_decremented_total",
            "fantasy_series_finalization_last_success_timestamp_seconds",
            "fantasy_notification_deliveries_total",
            "fantasy_scheduler_duration_seconds_count",
            "fantasy_scheduler_runs_total",
            "fantasy_scheduler_items_total",
            "fantasy_scheduler_last_completion_timestamp_seconds",
            "fantasy_scheduler_last_success_timestamp_seconds",
            "fantasy_active_series_count",
        )
        expectedFamilies.forEach { family ->
            assertTrue(exported.contains(family), "Missing Prometheus metric family $family")
        }
        assertTrue(exported.contains("scheduler=\"active_series_sync\""))
        assertFalse(exported.contains("series_id="))
        assertFalse(exported.contains("uri="))
    }

    @Test
    fun `sync and scoring use fixed low-cardinality tags and count only successful work`() {
        metrics.recordSeriesSync(
            sample = metrics.start(),
            kind = TournamentKind.STANDALONE,
            result = OperationResult.SUCCESS,
            createdGames = 2,
            updatedGames = 1,
        )
        metrics.recordSeriesSync(
            sample = metrics.start(),
            kind = TournamentKind.POLEMICA_COMPETITION,
            result = OperationResult.SUCCESS,
            empty = true,
        )
        metrics.recordScoring(metrics.start(), OperationResult.SUCCESS, gamesProcessed = 3)
        metrics.recordScoring(metrics.start(), OperationResult.ERROR, gamesProcessed = 99)

        assertEquals(
            1L,
            registry.get("fantasy.series.sync.duration")
                .tags("kind", "standalone", "result", "success")
                .timer().count(),
        )
        assertEquals(
            2.0,
            registry.get("fantasy.series.sync.games")
                .tags("kind", "standalone", "change", "created")
                .counter().count(),
        )
        assertEquals(
            1.0,
            registry.get("fantasy.series.sync.empty")
                .tag("kind", "polemica_competition")
                .counter().count(),
        )
        assertEquals(3.0, registry.get("fantasy.scoring.games.processed").counter().count())
        assertEquals(
            1L,
            registry.get("fantasy.scoring.duration").tag("result", "error").timer().count(),
        )
        assertFalse(
            registry.meters.flatMap { it.id.tags }.any { tag ->
                tag.key in setOf("series_id", "uri", "error_text", "exception", "exception_class")
            },
        )
    }

    @Test
    fun `notification finalization and scheduler metrics expose bounded outcomes and timestamps`() {
        metrics.recordNotificationDeliveries(
            NotificationCategory.TEAM_DEADLINE_REMINDER,
            NotificationOutcome.GLOBALLY_DISABLED,
            count = 4,
        )
        metrics.recordSeriesFinalization(rewardedUsers = 2, cardUsesDecremented = 5)
        metrics.recordActiveSeriesCounts(mapOf(SeriesStatus.ACTIVE to 3, SeriesStatus.SCORING to 1))

        val idleResult = metrics.recordSchedulerRun(
            sample = metrics.start(),
            job = SchedulerJob.DEADLINE_REMINDER,
            scheduledItems = 0,
            successfulItems = 0,
            failedItems = 0,
        )
        val partialResult = metrics.recordSchedulerRun(
            sample = metrics.start(),
            job = SchedulerJob.ACTIVE_SERIES_SYNC,
            scheduledItems = 2,
            successfulItems = 1,
            failedItems = 1,
        )

        assertEquals(SchedulerResult.IDLE, idleResult)
        assertEquals(SchedulerResult.PARTIAL, partialResult)
        assertEquals(
            4.0,
            registry.get("fantasy.notification.deliveries")
                .tags("category", "team_deadline_reminder", "outcome", "globally_disabled")
                .counter().count(),
        )
        assertEquals(1.0, registry.get("fantasy.series.finalizations").counter().count())
        assertEquals(2.0, registry.get("fantasy.series.finalization.rewarded.users").counter().count())
        assertEquals(5.0, registry.get("fantasy.series.finalization.card.uses.decremented").counter().count())
        assertEquals(
            1.0,
            registry.get("fantasy.scheduler.runs")
                .tags("scheduler", "active_series_sync", "result", "partial")
                .counter().count(),
        )
        assertEquals(
            1.0,
            registry.get("fantasy.scheduler.items")
                .tags("scheduler", "active_series_sync", "result", "failure")
                .counter().count(),
        )
        assertEquals(3.0, registry.get("fantasy.active.series.count").tag("status", "active").gauge().value())
        assertEquals(1.0, registry.get("fantasy.active.series.count").tag("status", "scoring").gauge().value())
        assertEquals(
            clock.instant().epochSecond.toDouble(),
            registry.get("fantasy.series.finalization.last.success.timestamp").gauge().value(),
        )
        assertEquals(
            clock.instant().epochSecond.toDouble(),
            registry.get("fantasy.scheduler.last.completion.timestamp")
                .tag("scheduler", "active_series_sync")
                .gauge().value(),
        )
        assertEquals(
            0.0,
            registry.get("fantasy.scheduler.last.success.timestamp")
                .tag("scheduler", "active_series_sync")
                .gauge().value(),
        )
        assertTrue(
            registry.get("fantasy.scheduler.last.success.timestamp")
                .tag("scheduler", "deadline_reminder")
                .gauge().value() > 0,
        )
    }
}
