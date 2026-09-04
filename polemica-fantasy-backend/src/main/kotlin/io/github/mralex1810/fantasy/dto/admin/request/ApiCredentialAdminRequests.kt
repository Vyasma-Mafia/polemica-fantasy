package io.github.mralex1810.fantasy.dto.admin.request

import jakarta.validation.constraints.Future
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import java.time.Instant

data class CreateAgentUserRequest(
    @field:Positive
    val telegramId: Long,
    @field:Size(max = 255)
    val username: String? = null,
    @field:Size(max = 255)
    val firstName: String? = null,
    @field:Size(max = 64)
    val displayName: String? = null,
)

data class CreateApiCredentialRequest(
    @field:NotBlank
    @field:Size(max = 100)
    val label: String,
    @field:Future
    val expiresAt: Instant,
)
