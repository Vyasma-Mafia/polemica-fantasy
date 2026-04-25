package io.github.mralex1810.fantasy.event

import java.time.Instant

data class SeriesBatchStartedEvent(
    val startedSeries: List<StartedSeriesInfo>,
)

data class StartedSeriesInfo(
    val seriesId: Long,
    val seriesName: String,
    val tournamentId: Long,
    val tournamentName: String,
    val teamDeadline: Instant?,
)
