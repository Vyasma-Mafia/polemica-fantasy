package io.github.mralex1810.fantasy.dto.admin.response

data class BatchStartSeriesResponse(
    val startedSeries: List<StartedSeriesEntry>,
    val skipped: List<SkippedSeriesEntry>,
    val notificationRecipientCount: Int,
)

data class StartedSeriesEntry(
    val seriesId: Long,
    val name: String,
    val tournamentName: String,
    val previousStatus: String,
)

data class SkippedSeriesEntry(
    val seriesId: Long,
    val reason: String,
)
