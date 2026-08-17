package io.github.mralex1810.fantasy.telegram

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.HttpMethod
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
    fun sendMessage(
        botToken: String,
        chatId: Long,
        text: String,
        messageThreadId: Int? = null,
        parseMode: String? = null,
        replyMarkup: InlineKeyboardMarkup? = null,
    ): Int {
        val body = buildMap<String, Any> {
            put("chat_id", chatId)
            put("text", text)
            if (messageThreadId != null) {
                put("message_thread_id", messageThreadId)
            }
            if (parseMode != null) {
                put("parse_mode", parseMode)
            }
            if (replyMarkup != null) {
                put("reply_markup", replyMarkup.toMap())
            }
        }
        val tree = apiPost(botToken, "sendMessage", body)
        requireTelegramOk(tree, "sendMessage")
        return tree.path("result").path("message_id").asInt().takeIf { it > 0 }
            ?: throw IllegalStateException("Telegram API sendMessage returned no message id")
    }

    /**
     * Replaces an operator message in place and returns its Telegram message id.
     */
    fun editMessageText(
        botToken: String,
        chatId: Long,
        messageId: Long,
        text: String,
        replyMarkup: InlineKeyboardMarkup? = null,
    ): Int {
        val body = buildMap<String, Any> {
            put("chat_id", chatId)
            put("message_id", messageId)
            put("text", text)
            if (replyMarkup != null) {
                put("reply_markup", replyMarkup.toMap())
            }
        }
        val tree = apiPostAllowErrors(botToken, "editMessageText", body)
        if (!tree.path("ok").asBoolean()) {
            val description = tree.path("description").asText("")
            if (description.contains("message is not modified", ignoreCase = true)) return messageId.toInt()
            if (description.contains("message to edit not found", ignoreCase = true) ||
                description.contains("message can't be edited", ignoreCase = true)) {
                throw TelegramMessageNotEditableException(description)
            }
        }
        requireTelegramOk(tree, "editMessageText")
        return tree.path("result").path("message_id").asInt().takeIf { it > 0 }
            ?: throw IllegalStateException("Telegram API editMessageText returned no message id")
    }

    fun sendMessageSafe(
        botToken: String,
        chatId: Long,
        text: String,
        parseMode: String? = null,
        replyMarkup: InlineKeyboardMarkup? = null,
    ): TelegramSendResult {
        val body = buildMap<String, Any> {
            put("chat_id", chatId)
            put("text", text)
            if (parseMode != null) {
                put("parse_mode", parseMode)
            }
            if (replyMarkup != null) {
                put("reply_markup", replyMarkup.toMap())
            }
        }
        return try {
            val tree = apiPostAllowErrors(botToken, "sendMessage", body)
            if (tree.path("ok").asBoolean()) {
                TelegramSendResult.Success
            } else {
                val code = tree.path("error_code").asInt(0)
                val desc = tree.path("description").asText("")
                when {
                    code == 403 -> TelegramSendResult.BotBlocked(desc)
                    code == 400 && desc.equals(CHAT_NOT_FOUND_DESCRIPTION, ignoreCase = true) ->
                        TelegramSendResult.BotBlocked(desc)
                    code == 429 ->
                        TelegramSendResult.RateLimited(tree.path("parameters").path("retry_after").asInt(5))
                    else -> TelegramSendResult.OtherError(code, desc)
                }
            }
        } catch (_: Exception) {
            TelegramSendResult.OtherError(0, "Failed to call Telegram sendMessage")
        }
    }

    /**
     * @return Bot user id (`result.id` from getMe).
     */
    fun getMe(botToken: String): Long {
        val tree = callWithoutSecretInException("getMe") {
            val token = requireBotToken(botToken)
            val uri = URI.create("https://api.telegram.org/bot$token/getMe")
            val responseBody = restClient.get()
                .uri(uri)
                .retrieve()
                .body(String::class.java)
                ?: throw IllegalStateException("empty response body")
            objectMapper.readTree(responseBody)
        }
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

    fun answerCallbackQuery(botToken: String, callbackQueryId: String, text: String? = null) {
        val body = buildMap<String, Any> {
            put("callback_query_id", callbackQueryId)
            if (!text.isNullOrBlank()) {
                put("text", text)
            }
        }
        val tree = apiPost(botToken, "answerCallbackQuery", body)
        requireTelegramOk(tree, "answerCallbackQuery")
    }

    private fun apiPost(botToken: String, method: String, body: Map<String, Any>): JsonNode {
        return callWithoutSecretInException(method) {
            val token = requireBotToken(botToken)
            val uri = URI.create("https://api.telegram.org/bot$token/$method")
            val responseBody = restClient.post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String::class.java)
                ?: throw IllegalStateException("empty response body")
            objectMapper.readTree(responseBody)
        }
    }

    private fun apiPostAllowErrors(botToken: String, method: String, body: Map<String, Any>): JsonNode {
        return callWithoutSecretInException(method) {
            val token = requireBotToken(botToken)
            val uri = URI.create("https://api.telegram.org/bot$token/$method")
            val responseBody = restClient.method(HttpMethod.POST)
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange { _, response ->
                    response.bodyTo(String::class.java)
                        ?: """{"ok":false,"error_code":0,"description":"empty response body"}"""
                }
            objectMapper.readTree(responseBody)
        }
    }

    /**
     * Spring transport exceptions include the absolute request URI. Telegram
     * puts the bot token in that URI, so never retain the original exception as
     * message or cause: callers may serialize the complete stack trace.
     */
    private fun <T> callWithoutSecretInException(method: String, action: () -> T): T =
        try {
            action()
        } catch (exception: Exception) {
            throw IllegalStateException(
                "Telegram API $method request failed (${exception.javaClass.simpleName})",
            )
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
        private const val CHAT_NOT_FOUND_DESCRIPTION = "Bad Request: chat not found"

        /** Bot API `parse_mode` for [sendMessage]. */
        const val PARSE_MODE_MARKDOWN_V2 = "MarkdownV2"
    }
}

class TelegramMessageNotEditableException(message: String) : IllegalStateException(message)
