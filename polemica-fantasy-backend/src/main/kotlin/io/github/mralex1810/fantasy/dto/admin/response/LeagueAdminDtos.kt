package io.github.mralex1810.fantasy.dto.admin.response

import io.github.mralex1810.fantasy.entity.LeagueType
import io.github.mralex1810.fantasy.entity.LeagueVisibility
import java.time.Instant

data class LeagueAdminDto(
    val id: Long,
    val code: String,
    val name: String,
    val description: String?,
    val leagueType: LeagueType,
    val valueCap: Long?,
    val maxLegendaryCount: Int?,
    val minTeamSize: Int,
    val maxTeamSize: Int,
    val createdByTelegramUserId: Long?,
    val visibility: LeagueVisibility,
    val entryFee: Long,
    val createdAt: Instant,
)

data class SeriesLeagueAdminDto(
    val id: Long,
    val seriesId: Long,
    val leagueId: Long,
    val leagueCode: String,
    val leagueName: String,
    val valueCapOverride: Long?,
    val rewardScaleOverride: Int?,
    val enabled: Boolean,
    val effectiveValueCap: Long?,
    val effectiveRewardScale: Int,
)
