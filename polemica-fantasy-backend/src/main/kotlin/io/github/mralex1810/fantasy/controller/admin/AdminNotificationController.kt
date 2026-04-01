package io.github.mralex1810.fantasy.controller.admin

import io.github.mralex1810.fantasy.dto.admin.request.BroadcastMessageRequest
import io.github.mralex1810.fantasy.dto.admin.response.BroadcastAcceptedResponse
import io.github.mralex1810.fantasy.service.AdminBroadcastNotificationService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/admin/notifications")
class AdminNotificationController(
    private val adminBroadcastNotificationService: AdminBroadcastNotificationService,
) {

    @PostMapping("/broadcast")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun broadcast(@Valid @RequestBody body: BroadcastMessageRequest): BroadcastAcceptedResponse {
        val recipientCount = adminBroadcastNotificationService.queueBroadcast(body.text.trim())
        return BroadcastAcceptedResponse(recipientCount = recipientCount)
    }
}
