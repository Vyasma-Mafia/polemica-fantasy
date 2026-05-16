package io.github.mralex1810.fantasy.dto.admin.request

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonSetter
import io.github.mralex1810.fantasy.entity.SeriesStatus
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.time.LocalDate

data class CreateSeriesRequest(
    @field:NotBlank @field:Size(max = 512)
    val name: String,
    @field:Size(max = 512)
    val namePrefix: String? = null,
    val gameNumFrom: Long? = null,
    val gameNumTo: Long? = null,
    @field:Min(0) @field:Max(2)
    val gamePhase: Int? = 0,
    val gameStartedOn: LocalDate? = null,
    @field:NotNull
    val status: SeriesStatus = SeriesStatus.UPCOMING,
    @field:NotNull
    val startsAt: Instant,
    @field:NotNull
    val teamDeadline: Instant,
)

class UpdateSeriesRequest {
    @field:Size(max = 512)
    var name: String? = null

    @field:Size(max = 512)
    var namePrefix: String? = null

    var gameNumFrom: Long? = null

    var gameNumTo: Long? = null

    @field:Min(0) @field:Max(2)
    var gamePhase: Int? = null
        @JsonSetter("gamePhase")
        set(value) {
            field = value
            gamePhaseSpecified = true
        }

    var gameStartedOn: LocalDate? = null
        @JsonSetter("gameStartedOn")
        set(value) {
            field = value
            gameStartedOnSpecified = true
        }

    var status: SeriesStatus? = null

    var startsAt: Instant? = null

    var teamDeadline: Instant? = null

    @get:JsonIgnore
    var gamePhaseSpecified: Boolean = false
        private set

    @get:JsonIgnore
    var gameStartedOnSpecified: Boolean = false
        private set
}

data class AssignSeriesPlayersRequest(
    @field:NotNull
    val tournamentPlayerIds: List<Long>,
)

data class BatchStartSeriesRequest(
    @field:NotNull
    val seriesIds: List<Long>,
)
