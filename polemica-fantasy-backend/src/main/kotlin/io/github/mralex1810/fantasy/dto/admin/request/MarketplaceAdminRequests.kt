package io.github.mralex1810.fantasy.dto.admin.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

data class BanPairRequest(
    @field:NotNull
    val telegramIdA: Long? = null,
    @field:NotNull
    val telegramIdB: Long? = null,
    @field:NotBlank
    val reason: String? = null,
)

data class MarkPairClearedRequest(
    @field:NotNull
    val telegramIdA: Long? = null,
    @field:NotNull
    val telegramIdB: Long? = null,
    val note: String? = null,
)
