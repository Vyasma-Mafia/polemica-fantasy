package io.github.mralex1810.fantasy.dto.admin.request

import io.github.mralex1810.fantasy.entity.LeagueVisibility
import jakarta.validation.constraints.Min

data class UpdateLeagueRequest(
    val name: String? = null,
    val description: String? = null,
    @field:Min(1)
    val valueCap: Long? = null,
    @field:Min(0)
    val maxLegendaryCount: Int? = null,
    @field:Min(1)
    val minTeamSize: Int? = null,
    @field:Min(1)
    val maxTeamSize: Int? = null,
    val visibility: LeagueVisibility? = null,
    @field:Min(0)
    val entryFee: Long? = null,
)

data class UpsertSeriesLeagueRequest(
    val leagueId: Long,
    @field:Min(1)
    val valueCapOverride: Long? = null,
    @field:Min(1)
    val rewardScaleOverride: Int? = null,
    val enabled: Boolean = true,
)
