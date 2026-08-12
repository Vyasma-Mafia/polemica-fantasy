package io.github.mralex1810.fantasy.observability

import io.github.mralex1810.fantasy.entity.NotificationCategory
import io.github.mralex1810.fantasy.entity.SeriesStatus
import io.github.mralex1810.fantasy.entity.TournamentKind
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

@Component
class FantasyMetrics internal constructor(
    private val meterRegistry: MeterRegistry,
    private val clock: Clock,
) {
    @Autowired
    constructor(meterRegistry: MeterRegistry) : this(meterRegistry, Clock.systemUTC())

    enum class OperationResult(val tag: String) {
        SUCCESS("success"),
        ERROR("error"),
    }

    enum class SyncChange(val tag: String) {
        CREATED("created"),
        UPDATED("updated"),
    }

    enum class NotificationOutcome(val tag: String) {
        SENT("sent"),
        BOT_BLOCKED("bot_blocked"),
        PREFERENCE_DISABLED("preference_disabled"),
        GLOBALLY_DISABLED("globally_disabled"),
        ERROR("error"),
    }

    enum class SchedulerJob(val tag: String) {
        ACTIVE_SERIES_SYNC("active_series_sync"),
        DEADLINE_REMINDER("deadline_reminder"),
    }

    enum class SchedulerResult(val tag: String) {
        IDLE("idle"),
        SUCCESS("success"),
        PARTIAL("partial"),
        FAILURE("failure"),
    }

    enum class SchedulerItemResult(val tag: String) {
        SUCCESS("success"),
        FAILURE("failure"),
    }

    class Sample internal constructor(internal val startedAtNanos: Long)

    private val schedulerLastCompletion = SchedulerJob.entries.associateWith { job ->
        timestampGauge("fantasy.scheduler.last.completion.timestamp", "scheduler", job.tag)
    }
    private val schedulerLastSuccess = SchedulerJob.entries.associateWith { job ->
        timestampGauge("fantasy.scheduler.last.success.timestamp", "scheduler", job.tag)
    }
    private val activeSeriesCounts = listOf(SeriesStatus.ACTIVE, SeriesStatus.SCORING).associateWith { status ->
        AtomicLong().also { value ->
            Gauge.builder("fantasy.active.series.count", value) { it.get().toDouble() }
                .tag("status", seriesStatusTag(status))
                .register(meterRegistry)
        }
    }
    private val finalizationLastSuccess = timestampGauge("fantasy.series.finalization.last.success.timestamp")

    fun start(): Sample = Sample(System.nanoTime())

    fun recordSeriesSync(
        sample: Sample,
        kind: TournamentKind?,
        result: OperationResult,
        createdGames: Int = 0,
        updatedGames: Int = 0,
        empty: Boolean = false,
    ) {
        val kindTag = tournamentKindTag(kind)
        stopTimer(
            sample = sample,
            name = "fantasy.series.sync.duration",
            tags = arrayOf("kind", kindTag, "result", result.tag),
        )
        if (result != OperationResult.SUCCESS) return
        incrementCounter("fantasy.series.sync.games", createdGames.toLong(), "kind", kindTag, "change", SyncChange.CREATED.tag)
        incrementCounter("fantasy.series.sync.games", updatedGames.toLong(), "kind", kindTag, "change", SyncChange.UPDATED.tag)
        if (empty) {
            incrementCounter("fantasy.series.sync.empty", 1, "kind", kindTag)
        }
    }

    fun recordScoring(sample: Sample, result: OperationResult, gamesProcessed: Int = 0) {
        stopTimer(
            sample = sample,
            name = "fantasy.scoring.duration",
            tags = arrayOf("result", result.tag),
        )
        if (result == OperationResult.SUCCESS) {
            incrementCounter("fantasy.scoring.games.processed", gamesProcessed.toLong())
        }
    }

    fun recordSeriesFinalization(rewardedUsers: Int, cardUsesDecremented: Int) {
        incrementCounter("fantasy.series.finalizations", 1)
        incrementCounter("fantasy.series.finalization.rewarded.users", rewardedUsers.toLong())
        incrementCounter("fantasy.series.finalization.card.uses.decremented", cardUsesDecremented.toLong())
        finalizationLastSuccess.set(nowEpochSeconds())
    }

    fun recordNotificationDeliveries(
        category: NotificationCategory,
        outcome: NotificationOutcome,
        count: Int = 1,
    ) {
        incrementCounter(
            "fantasy.notification.deliveries",
            count.toLong(),
            "category",
            notificationCategoryTag(category),
            "outcome",
            outcome.tag,
        )
    }

    fun recordSchedulerRun(
        sample: Sample,
        job: SchedulerJob,
        scheduledItems: Int,
        successfulItems: Int,
        failedItems: Int,
        fatalFailure: Boolean = false,
    ): SchedulerResult {
        val result = schedulerResult(scheduledItems, successfulItems, failedItems, fatalFailure)
        stopTimer(
            sample = sample,
            name = "fantasy.scheduler.duration",
            tags = arrayOf("scheduler", job.tag, "result", result.tag),
        )
        incrementCounter("fantasy.scheduler.runs", 1, "scheduler", job.tag, "result", result.tag)
        incrementCounter(
            "fantasy.scheduler.items",
            successfulItems.toLong(),
            "scheduler",
            job.tag,
            "result",
            SchedulerItemResult.SUCCESS.tag,
        )
        incrementCounter(
            "fantasy.scheduler.items",
            failedItems.toLong(),
            "scheduler",
            job.tag,
            "result",
            SchedulerItemResult.FAILURE.tag,
        )
        val now = nowEpochSeconds()
        schedulerLastCompletion.getValue(job).set(now)
        if (result == SchedulerResult.IDLE || result == SchedulerResult.SUCCESS) {
            schedulerLastSuccess.getValue(job).set(now)
        }
        return result
    }

    fun recordActiveSeriesCounts(countsByStatus: Map<SeriesStatus, Int>) {
        activeSeriesCounts.forEach { (status, gauge) ->
            gauge.set(countsByStatus[status]?.toLong() ?: 0L)
        }
    }

    private fun schedulerResult(
        scheduledItems: Int,
        successfulItems: Int,
        failedItems: Int,
        fatalFailure: Boolean,
    ): SchedulerResult = when {
        fatalFailure -> SchedulerResult.FAILURE
        scheduledItems == 0 -> SchedulerResult.IDLE
        failedItems == 0 && successfulItems == scheduledItems -> SchedulerResult.SUCCESS
        successfulItems > 0 -> SchedulerResult.PARTIAL
        else -> SchedulerResult.FAILURE
    }

    private fun stopTimer(sample: Sample, name: String, tags: Array<String>) {
        val elapsed = (System.nanoTime() - sample.startedAtNanos).coerceAtLeast(0L)
        Timer.builder(name)
            .tags(*tags)
            .register(meterRegistry)
            .record(elapsed, TimeUnit.NANOSECONDS)
    }

    private fun incrementCounter(name: String, count: Long, vararg tags: String) {
        if (count <= 0L) return
        Counter.builder(name)
            .tags(*tags)
            .register(meterRegistry)
            .increment(count.toDouble())
    }

    private fun timestampGauge(name: String, vararg tags: String): AtomicLong =
        AtomicLong().also { value ->
            Gauge.builder(name, value) { it.get().toDouble() }
                .baseUnit("seconds")
                .tags(*tags)
                .register(meterRegistry)
        }

    private fun nowEpochSeconds(): Long = Instant.now(clock).epochSecond

    private fun tournamentKindTag(kind: TournamentKind?): String = when (kind) {
        TournamentKind.STANDALONE -> "standalone"
        TournamentKind.POLEMICA_COMPETITION -> "polemica_competition"
        null -> "unknown"
    }

    private fun notificationCategoryTag(category: NotificationCategory): String = when (category) {
        NotificationCategory.ADMIN_BROADCAST -> "admin_broadcast"
        NotificationCategory.SERIES_START -> "series_start"
        NotificationCategory.TEAM_DEADLINE_REMINDER -> "team_deadline_reminder"
        NotificationCategory.SERIES_FINALIZED -> "series_finalized"
        NotificationCategory.SERIES_ROSTER_CHANGE -> "series_roster_change"
        NotificationCategory.MARKETPLACE_SALE -> "marketplace_sale"
        NotificationCategory.MARKETPLACE_WATCH -> "marketplace_watch"
        NotificationCategory.ONBOARDING_TIPS -> "onboarding_tips"
        NotificationCategory.MARKETPLACE_COMPLAINT_RESOLVED -> "marketplace_complaint_resolved"
        NotificationCategory.MARKETPLACE_SANCTION_APPLIED -> "marketplace_sanction_applied"
        NotificationCategory.PAIR_BAN -> "pair_ban"
    }

    private fun seriesStatusTag(status: SeriesStatus): String = when (status) {
        SeriesStatus.ACTIVE -> "active"
        SeriesStatus.SCORING -> "scoring"
        else -> error("Unsupported active series metric status: $status")
    }
}
