package io.github.mralex1810.fantasy.dto.user.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

data class LegendaryUpgradeRequest(
    @field:NotNull
    val userCardId: Long,
    @field:NotBlank
    val achievementId: String,
)
