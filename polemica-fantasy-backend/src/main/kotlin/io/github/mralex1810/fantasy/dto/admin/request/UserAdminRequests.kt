package io.github.mralex1810.fantasy.dto.admin.request

import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class GiveFantikiRequest(
    @field:Positive
    val amount: Long,

    @field:NotBlank
    @field:Size(max = 500)
    val adminReason: String,
)

data class TakeFantikiRequest(
    @field:Positive
    val amount: Long,

    @field:NotBlank
    @field:Size(max = 500)
    val adminReason: String,
)
