package io.github.mralex1810.fantasy.controller

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.mralex1810.fantasy.config.TelegramSupportProperties
import io.github.mralex1810.fantasy.service.TelegramSupportUpdateService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/telegram")
class TelegramWebhookController(
    private val telegramSupportProperties: TelegramSupportProperties,
    private val telegramSupportUpdateService: TelegramSupportUpdateService,
    private val objectMapper: ObjectMapper,
) {

    @PostMapping("/webhook")
    fun webhook(
        @RequestHeader(value = X_TELEGRAM_SECRET, required = false) secret: String?,
        @RequestBody body: String,
    ): ResponseEntity<String> {
        if (!telegramSupportProperties.enabled) {
            return ResponseEntity.ok("ok")
        }
        if (telegramSupportProperties.webhookSecret.isBlank()) {
            return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("telegram support webhook secret is not configured")
        }
        if (secret != telegramSupportProperties.webhookSecret) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }
        val root = objectMapper.readTree(body)
        telegramSupportUpdateService.processUpdate(root)
        return ResponseEntity.ok("ok")
    }

    companion object {
        const val X_TELEGRAM_SECRET = "X-Telegram-Bot-Api-Secret-Token"
    }
}
