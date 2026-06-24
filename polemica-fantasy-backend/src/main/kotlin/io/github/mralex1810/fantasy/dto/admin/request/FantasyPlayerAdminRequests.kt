package io.github.mralex1810.fantasy.dto.admin.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

data class CreateFantasyPlayerAdminRequest(
    @field:NotNull
    val polemicaUserId: Long,
    @field:NotBlank @field:Size(max = 512)
    val nickname: String,
)

data class UpdateFantasyPlayerAdminRequest(
    @field:NotBlank @field:Size(max = 512)
    val nickname: String,
)
