package io.github.mralex1810.fantasy.controller.internal

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.mralex1810.fantasy.config.TelegramLeagueImportProperties
import io.github.mralex1810.fantasy.dto.internal.TelegramLeagueImportEventRequest
import io.github.mralex1810.fantasy.dto.internal.TelegramLeagueImportEventResponse
import io.github.mralex1810.fantasy.service.leagueimport.LeagueImportHmacVerifier
import io.github.mralex1810.fantasy.service.leagueimport.LeagueImportIngestService
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@RestController
@RequestMapping("/api/v1/internal/telegram-league-import")
class TelegramLeagueImportIngestController(
    private val properties: TelegramLeagueImportProperties,
    private val verifier: LeagueImportHmacVerifier,
    private val objectMapper: ObjectMapper,
    private val ingestService: LeagueImportIngestService,
) {
    @PostMapping("/events", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun ingest(
        @RequestHeader("X-League-Import-Key-Id") keyId: String,
        @RequestHeader("X-League-Import-Delivery-Id") deliveryIdRaw: String,
        @RequestHeader("X-League-Import-Timestamp") timestamp: String,
        @RequestHeader("X-League-Import-Signature") signature: String,
        @RequestBody body: ByteArray,
    ): TelegramLeagueImportEventResponse {
        if (!properties.enabled || !properties.ingestEnabled) {
            throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Telegram league import ingest is disabled")
        }
        if (body.isEmpty() || body.size > MAX_BODY_BYTES) {
            throw ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Invalid ingest body size")
        }
        val deliveryId = runCatching { UUID.fromString(deliveryIdRaw) }.getOrNull()
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid delivery id")
        val verifiedAt = verifier.verify(keyId, deliveryIdRaw, timestamp, signature, body)
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid ingest signature")
        val request = runCatching { objectMapper.readValue(body, TelegramLeagueImportEventRequest::class.java) }.getOrNull()
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid ingest payload")
        return try {
            ingestService.ingest(keyId, deliveryId, verifiedAt, request)
        } catch (e: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.message ?: "Invalid ingest payload")
        } catch (e: IllegalStateException) {
            throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, e.message ?: "Ingest unavailable")
        }
    }

    companion object {
        private const val MAX_BODY_BYTES = 24 * 1024
    }
}
