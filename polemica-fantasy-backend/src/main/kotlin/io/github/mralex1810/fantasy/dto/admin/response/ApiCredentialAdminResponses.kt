package io.github.mralex1810.fantasy.dto.admin.response

import java.time.Instant

data class AgentUserDto(
    val id: Long,
    val telegramId: Long,
    val username: String?,
    val firstName: String?,
    val displayName: String?,
    val createdAt: Instant,
    val fantiki: Long,
    val automatedAgent: Boolean,
)

data class ApiCredentialDto(
    val id: Long,
    val label: String,
    val tokenHint: String,
    val createdAt: Instant,
    val expiresAt: Instant,
    val lastUsedAt: Instant?,
    val revokedAt: Instant?,
    val createdBy: String,
    val revokedBy: String?,
)

class CreatedApiCredentialDto(
    val credential: ApiCredentialDto,
    val token: String,
)
