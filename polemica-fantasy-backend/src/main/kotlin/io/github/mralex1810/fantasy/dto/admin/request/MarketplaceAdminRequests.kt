package io.github.mralex1810.fantasy.dto.admin.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.PositiveOrZero
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

data class BanDuration(
    @field:Positive
    val days: Int? = null,
)

data class SanctionTransactionRequest(
    @field:NotBlank
    val reason: String? = null,
    @field:NotNull
    @field:PositiveOrZero
    val sellerFine: Long? = null,
    @field:NotNull
    @field:PositiveOrZero
    val buyerFine: Long? = null,
    @field:NotNull
    @field:PositiveOrZero
    val complainantReward: Long? = null,
    val banSeller: BanDuration? = null,
    val banBuyer: BanDuration? = null,
)

data class BanUserRequest(
    @field:Positive
    val days: Int? = null,
)
