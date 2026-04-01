package io.github.mralex1810.fantasy.telegram

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class TelegramBotApiClient(
    private val restClient: RestClient,
    private val objectMapper: ObjectMapper,
) {

    /**
     * @throws IllegalStateException when Telegram returns ok=false or body cannot be parsed
     */
    fun sendMessage(botToken: String, chatId: Long, text: String) {
        val responseBody = restClient.post()
            .uri("/bot/{token}/sendMessage", botToken)
            .contentType(MediaType.APPLICATION_JSON)
            .body(mapOf("chat_id" to chatId, "text" to text))
            .retrieve()
            .body(String::class.java)
            ?: throw IllegalStateException("Telegram sendMessage: empty response body")
        val tree = objectMapper.readTree(responseBody)
        if (!tree.path("ok").asBoolean()) {
            val desc = tree.path("description").asText("unknown error")
            throw IllegalStateException("Telegram API: $desc")
        }
    }
}
