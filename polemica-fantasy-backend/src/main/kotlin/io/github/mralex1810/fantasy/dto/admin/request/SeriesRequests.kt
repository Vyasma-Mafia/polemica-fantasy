package io.github.mralex1810.fantasy.dto.admin.request

import io.github.mralex1810.fantasy.entity.SeriesStatus
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant

data class CreateSeriesRequest(
    @field:NotBlank @field:Size(max = 512)
    val name: String,
    @field:Size(max = 512)
    val namePrefix: String? = null,
    val gameNumFrom: Long? = null,
    val gameNumTo: Long? = null,
    @field:NotNull
    val status: SeriesStatus = SeriesStatus.UPCOMING,
    @field:NotNull
    val startsAt: Instant,
    @field:NotNull
    val teamDeadline: Instant,
)

data class UpdateSeriesRequest(
    @field:Size(max = 512)
    val name: String? = null,
    @field:Size(max = 512)
    val namePrefix: String? = null,
    val gameNumFrom: Long? = null,
    val gameNumTo: Long? = null,
    val status: SeriesStatus? = null,
    val startsAt: Instant? = null,
    val teamDeadline: Instant? = null,
)

data class AssignSeriesPlayersRequest(
    @field:NotNull
    val tournamentPlayerIds: List<Long>,
)

data class BatchStartSeriesRequest(
    @field:NotNull
    val seriesIds: List<Long>,
)
