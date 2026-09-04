package io.github.mralex1810.fantasy.controller.admin

import io.github.mralex1810.fantasy.dto.admin.request.CreateAgentUserRequest
import io.github.mralex1810.fantasy.dto.admin.request.CreateApiCredentialRequest
import io.github.mralex1810.fantasy.dto.admin.response.AgentUserDto
import io.github.mralex1810.fantasy.dto.admin.response.ApiCredentialDto
import io.github.mralex1810.fantasy.dto.admin.response.CreatedApiCredentialDto
import io.github.mralex1810.fantasy.service.ApiCredentialService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/admin")
class ApiCredentialAdminController(
    private val apiCredentialService: ApiCredentialService,
) {
    @PostMapping("/agent-users")
    @ResponseStatus(HttpStatus.CREATED)
    fun createAgentUser(@Valid @RequestBody body: CreateAgentUserRequest): AgentUserDto =
        apiCredentialService.createAgentUser(body)

    @PostMapping("/users/{telegramId}/api-credentials")
    @ResponseStatus(HttpStatus.CREATED)
    fun createCredential(
        @PathVariable telegramId: Long,
        @Valid @RequestBody body: CreateApiCredentialRequest,
        authentication: Authentication,
    ): CreatedApiCredentialDto = apiCredentialService.createCredential(
        telegramId = telegramId,
        label = body.label,
        expiresAt = body.expiresAt,
        actor = authentication.name,
    )

    @GetMapping("/users/{telegramId}/api-credentials")
    fun listCredentials(@PathVariable telegramId: Long): List<ApiCredentialDto> =
        apiCredentialService.listCredentials(telegramId)

    @DeleteMapping("/users/{telegramId}/api-credentials/{credentialId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun revokeCredential(
        @PathVariable telegramId: Long,
        @PathVariable credentialId: Long,
        authentication: Authentication,
    ) {
        apiCredentialService.revokeCredential(telegramId, credentialId, authentication.name)
    }
}
