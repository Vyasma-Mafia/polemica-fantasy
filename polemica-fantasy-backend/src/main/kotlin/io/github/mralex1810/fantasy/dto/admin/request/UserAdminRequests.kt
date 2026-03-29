package io.github.mralex1810.fantasy.dto.admin.request

import jakarta.validation.constraints.Positive

data class GiveFantikiRequest(
    @field:Positive
    val amount: Long,
)
