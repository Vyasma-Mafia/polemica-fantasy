package io.github.mralex1810.fantasy.dto.admin.response

import java.time.LocalDateTime

data class PolemicaCompetitionSummaryDto(
    val id: Long,
    val name: String,
    val startDate: LocalDateTime?,
    val endDate: LocalDateTime?,
)

data class PolemicaCompetitionDetailDto(
    val id: Long,
    val name: String,
    val startDate: LocalDateTime?,
    val endDate: LocalDateTime?,
    val region: String?,
    val city: String?,
    val description: String?,
    val link: String?,
    val memberCount: Int?,
    val rating: Int?,
    val hasScores: Boolean?,
)
