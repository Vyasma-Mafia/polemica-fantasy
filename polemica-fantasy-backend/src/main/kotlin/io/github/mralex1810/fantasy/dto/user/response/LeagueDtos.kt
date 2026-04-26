package io.github.mralex1810.fantasy.dto.user.response

data class SeriesLeagueDto(
    val code: String,
    val name: String,
    val description: String?,
    val valueCap: Long?,
    val maxLegendaryCount: Int?,
    val minTeamSize: Int,
    val maxTeamSize: Int,
    val rewardScale: Int,
    val hasTeam: Boolean,
)
