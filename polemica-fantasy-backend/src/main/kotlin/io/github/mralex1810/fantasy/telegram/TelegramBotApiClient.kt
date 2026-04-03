package io.github.mralex1810.fantasy.telegram

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.net.URI

@Component
class TelegramBotApiClient(
    private val restClient: RestClient,
    private val objectMapper: ObjectMapper,
) {

    /**
     * @throws IllegalStateException when Telegram returns ok=false or body cannot be parsed
     */
    fun sendMessage(botToken: String, chatId: Long, text: String, messageThreadId: Int? = null) {
        val body = buildMap {
            put("chat_id", chatId)
            put("text", text)
            if (messageThreadId != null) {
                put("message_thread_id", messageThreadId)
            }
        }
        val tree = apiPost(botToken, "sendMessage", body)
        requireTelegramOk(tree, "sendMessage")
    }

    /**
     * @return Bot user id (`result.id` from getMe).
     */
    fun getMe(botToken: String): Long {
        val token = requireBotToken(botToken)
        val uri = URI.create("https://api.telegram.org/bot$token/getMe")
        val responseBody = restClient.get()
            .uri(uri)
            .retrieve()
            .body(String::class.java)
            ?: throw IllegalStateException("Telegram getMe: empty response body")
        val tree = objectMapper.readTree(responseBody)
        requireTelegramOk(tree, "getMe")
        return tree.path("result").path("id").asLong()
    }

    /**
     * @return New forum topic `message_thread_id`.
     */
    fun createForumTopic(botToken: String, chatId: Long, name: String): Int {
        val trimmed = name.take(MAX_FORUM_TOPIC_NAME_LENGTH)
        val body = mapOf(
            "chat_id" to chatId,
            "name" to trimmed,
        )
        val tree = apiPost(botToken, "createForumTopic", body)
        requireTelegramOk(tree, "createForumTopic")
        return tree.path("result").path("message_thread_id").asInt()
    }

    fun forwardMessage(
        botToken: String,
        toChatId: Long,
        fromChatId: Long,
        messageId: Int,
        messageThreadId: Int,
    ) {
        val body = mapOf(
            "chat_id" to toChatId,
            "from_chat_id" to fromChatId,
            "message_id" to messageId,
            "message_thread_id" to messageThreadId,
        )
        val tree = apiPost(botToken, "forwardMessage", body)
        requireTelegramOk(tree, "forwardMessage")
    }

    /**
     * Copy a message (e.g. admin reply in forum) to the user's private chat.
     */
    fun copyMessage(botToken: String, toChatId: Long, fromChatId: Long, messageId: Int) {
        val body = mapOf(
            "chat_id" to toChatId,
            "from_chat_id" to fromChatId,
            "message_id" to messageId,
        )
        val tree = apiPost(botToken, "copyMessage", body)
        requireTelegramOk(tree, "copyMessage")
    }

    private fun apiPost(botToken: String, method: String, body: Map<String, Any>): JsonNode {
        val token = requireBotToken(botToken)
        val uri = URI.create("https://api.telegram.org/bot$token/$method")
        val responseBody = restClient.post()
            .uri(uri)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(String::class.java)
            ?: throw IllegalStateException("Telegram $method: empty response body")
        return objectMapper.readTree(responseBody)
    }

    private fun requireBotToken(botToken: String): String {
        val token = botToken.trim()
        require(token.isNotEmpty()) { "bot token must not be empty" }
        return token
    }

    private fun requireTelegramOk(tree: JsonNode, method: String) {
        if (!tree.path("ok").asBoolean()) {
            val desc = tree.path("description").asText("unknown error")
            throw IllegalStateException("Telegram API $method: $desc")
        }
    }

    companion object {
        private const val MAX_FORUM_TOPIC_NAME_LENGTH = 128
    }
}
