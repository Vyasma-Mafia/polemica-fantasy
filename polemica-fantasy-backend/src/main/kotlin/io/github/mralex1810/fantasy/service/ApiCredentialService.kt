package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.auth.ApiCredentialTokenCodec
import io.github.mralex1810.fantasy.dto.admin.request.CreateAgentUserRequest
import io.github.mralex1810.fantasy.dto.admin.response.AgentUserDto
import io.github.mralex1810.fantasy.dto.admin.response.ApiCredentialDto
import io.github.mralex1810.fantasy.dto.admin.response.CreatedApiCredentialDto
import io.github.mralex1810.fantasy.entity.ApiCredential
import io.github.mralex1810.fantasy.entity.ApiCredentialAuthAudit
import io.github.mralex1810.fantasy.entity.TelegramUser
import io.github.mralex1810.fantasy.repository.ApiCredentialAuthAuditRepository
import io.github.mralex1810.fantasy.repository.ApiCredentialRepository
import io.github.mralex1810.fantasy.repository.TelegramUserRepository
import java.time.Instant
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class ApiCredentialService(
    private val apiCredentialRepository: ApiCredentialRepository,
    private val apiCredentialAuthAuditRepository: ApiCredentialAuthAuditRepository,
    private val telegramUserRepository: TelegramUserRepository,
    private val telegramUserBootstrapService: TelegramUserBootstrapService,
    private val tokenCodec: ApiCredentialTokenCodec,
) {
    fun createAgentUser(request: CreateAgentUserRequest): AgentUserDto {
        val displayName = request.displayName?.trim()?.takeIf { it.isNotEmpty() }
        val user = try {
            telegramUserBootstrapService.insertNewUserWithInitialFantiki(
                telegramId = request.telegramId,
                username = request.username?.trim()?.takeIf { it.isNotEmpty() },
                firstName = request.firstName?.trim()?.takeIf { it.isNotEmpty() },
                displayName = displayName,
                isAutomatedAgent = true,
            )
        } catch (_: DataIntegrityViolationException) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "User ${request.telegramId} already exists")
        }
        return user.toAgentDto()
    }

    @Transactional
    fun createCredential(telegramId: Long, label: String, expiresAt: Instant, actor: String): CreatedApiCredentialDto {
        val now = Instant.now()
        if (!expiresAt.isAfter(now)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "expiresAt must be in the future")
        }
        val user = telegramUserRepository.findByTelegramIdForUpdate(telegramId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User with telegram id $telegramId not found")
        val token = tokenCodec.generate()
        user.isAutomatedAgent = true
        val credential = apiCredentialRepository.save(
            ApiCredential(
                telegramUser = user,
                label = label.trim(),
                tokenHash = tokenCodec.hash(token),
                tokenHint = tokenCodec.hint(token),
                createdAt = now,
                expiresAt = expiresAt,
                createdBy = actor,
            ),
        )
        return CreatedApiCredentialDto(credential.toDto(), token)
    }

    @Transactional(readOnly = true)
    fun listCredentials(telegramId: Long): List<ApiCredentialDto> {
        val user = telegramUserRepository.findByTelegramId(telegramId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User with telegram id $telegramId not found")
        return apiCredentialRepository.findAllByTelegramUser_IdOrderByCreatedAtDesc(user.id!!).map { it.toDto() }
    }

    @Transactional
    fun revokeCredential(telegramId: Long, credentialId: Long, actor: String) {
        val user = telegramUserRepository.findByTelegramId(telegramId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User with telegram id $telegramId not found")
        val credential = apiCredentialRepository.findByIdAndTelegramUser_Id(credentialId, user.id!!)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "API credential $credentialId not found")
        if (credential.revokedAt == null) {
            credential.revokedAt = Instant.now()
            credential.revokedBy = actor
        }
    }

    @Transactional
    fun authenticate(token: String, requestMethod: String, requestPath: String): ApiCredentialAuthenticationResult {
        if (!tokenCodec.isWellFormed(token)) return ApiCredentialAuthenticationResult.Malformed
        val credential = apiCredentialRepository.findByTokenHash(tokenCodec.hash(token))
            ?: return ApiCredentialAuthenticationResult.Unknown
        val now = Instant.now()
        val outcome = when {
            credential.revokedAt != null -> ApiCredentialAuthenticationResult.RecognizedFailure.REVOKED
            !credential.expiresAt.isAfter(now) -> ApiCredentialAuthenticationResult.RecognizedFailure.EXPIRED
            else -> null
        }
        apiCredentialAuthAuditRepository.save(
            ApiCredentialAuthAudit(
                apiCredential = credential,
                outcome = outcome?.name ?: "SUCCESS",
                requestMethod = requestMethod.take(16),
                requestPath = requestPath.take(512),
                occurredAt = now,
            ),
        )
        apiCredentialAuthAuditRepository.flush()
        apiCredentialAuthAuditRepository.trimToMostRecent(credential.id!!, MAX_AUDIT_ROWS_PER_CREDENTIAL)
        if (outcome != null) return outcome
        credential.lastUsedAt = now
        return ApiCredentialAuthenticationResult.Success(credential.telegramUser!!)
    }

    private fun ApiCredential.toDto() = ApiCredentialDto(
        id = id!!,
        label = label,
        tokenHint = tokenHint,
        createdAt = createdAt,
        expiresAt = expiresAt,
        lastUsedAt = lastUsedAt,
        revokedAt = revokedAt,
        createdBy = createdBy,
        revokedBy = revokedBy,
    )

    private fun TelegramUser.toAgentDto() = AgentUserDto(
        id = id!!,
        telegramId = telegramId,
        username = username,
        firstName = firstName,
        displayName = displayName,
        createdAt = createdAt,
        fantiki = fantiki,
        automatedAgent = isAutomatedAgent,
    )

    private companion object {
        const val MAX_AUDIT_ROWS_PER_CREDENTIAL = 1_000
    }
}

sealed interface ApiCredentialAuthenticationResult {
    data class Success(val user: TelegramUser) : ApiCredentialAuthenticationResult

    data object Malformed : ApiCredentialAuthenticationResult

    data object Unknown : ApiCredentialAuthenticationResult

    enum class RecognizedFailure : ApiCredentialAuthenticationResult {
        EXPIRED,
        REVOKED,
    }
}
