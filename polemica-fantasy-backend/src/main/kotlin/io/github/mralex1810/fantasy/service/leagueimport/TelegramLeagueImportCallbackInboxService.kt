package io.github.mralex1810.fantasy.service.leagueimport

import com.fasterxml.jackson.databind.JsonNode
import io.github.mralex1810.fantasy.config.TelegramLeagueImportProperties
import io.github.mralex1810.fantasy.repository.LeagueImportRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class TelegramLeagueImportCallbackInboxService(
    private val properties: TelegramLeagueImportProperties,
    private val tokenCodec: LeagueImportActionTokenCodec,
    private val repository: LeagueImportRepository,
) {
    @Transactional
    fun accept(root: JsonNode): Boolean {
        val callback = root.path("callback_query")
        val data = callback.path("data").asText("")
        if (!data.startsWith("lic:")) return false
        if (!properties.enabled) return true
        val updateId = root.path("update_id").takeIf { it.isIntegralNumber }?.asLong() ?: return true
        val queryId = callback.path("id").asText("").takeIf { it.isNotBlank() } ?: return true
        val message = callback.path("message")
        val from = callback.path("from")
        repository.insertCallbackInbox(
            updateId = updateId,
            queryId = queryId,
            actionId = if (properties.callbackEnabled) tokenCodec.decode(data) else null,
            tokenHash = tokenCodec.hash(data),
            operatorChatId = message.path("chat").path("id").takeIf { it.isIntegralNumber }?.asLong(),
            operatorMessageId = message.path("message_id").takeIf { it.isIntegralNumber }?.asLong(),
            actorTelegramId = from.path("id").takeIf { it.isIntegralNumber }?.asLong(),
            actorUsername = from.path("username").asText("").takeIf { it.isNotBlank() },
            actorIsBot = from.path("is_bot").asBoolean(false),
            ordinaryMessage = message.isObject && !callback.has("inline_message_id"),
        )
        return true
    }
}
