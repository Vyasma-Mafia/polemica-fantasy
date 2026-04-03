package io.github.mralex1810.fantasy.service

import com.fasterxml.jackson.databind.JsonNode
import io.github.mralex1810.fantasy.config.TelegramProperties
import io.github.mralex1810.fantasy.config.TelegramSupportProperties
import io.github.mralex1810.fantasy.repository.TelegramUserRepository
import io.github.mralex1810.fantasy.telegram.TelegramBotApiClient
import io.github.mralex1810.fantasy.telegram.TelegramSupportBotIdentity
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class TelegramSupportUpdateService(
    private val telegramSupportProperties: TelegramSupportProperties,
    private val telegramProperties: TelegramProperties,
    private val telegramUserRepository: TelegramUserRepository,
    private val telegramSupportRelayService: TelegramSupportRelayService,
    private val telegramBotApiClient: TelegramBotApiClient,
    private val telegramSupportBotIdentity: TelegramSupportBotIdentity,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun processUpdate(root: JsonNode) {
        try {
            processUpdateInner(root)
        } catch (e: Exception) {
            log.warn("Telegram support: failed to process update", e)
        }
    }

    private fun processUpdateInner(root: JsonNode) {
        val msg = root.path("message")
        if (msg.isMissingNode || msg.isNull) {
            return
        }
        val chat = msg.path("chat")
        if (!chat.has("id")) {
            return
        }
        val chatId = chat.path("id").asLong()
        val type = chat.path("type").asText()
        when (type) {
            "private" -> handlePrivate(msg)
            "supergroup" -> {
                if (chatId == telegramSupportProperties.forumChatId) {
                    handleForumSupergroup(msg)
                }
            }
        }
    }

    private fun handlePrivate(msg: JsonNode) {
        val token = telegramProperties.token.trim()
        if (token.isEmpty()) {
            log.warn("Telegram support: TELEGRAM_BOT_TOKEN is empty, skipping")
            return
        }
        val from = msg.path("from")
        if (!from.has("id")) {
            return
        }
        val userTgId = from.path("id").asLong()
        if (userTgId == telegramSupportBotIdentity.botUserId()) {
            return
        }
        val text = msg.path("text").asText("").trim()
        if (text.startsWith("/start")) {
            telegramBotApiClient.sendMessage(token, userTgId, START_REPLY)
            return
        }
        val dbUser = telegramUserRepository.findByTelegramId(userTgId)
        if (dbUser == null) {
            telegramBotApiClient.sendMessage(token, userTgId, NOT_REGISTERED_REPLY)
            return
        }
        val messageId = msg.path("message_id").asInt()
        telegramSupportRelayService.ensureTopicAndForwardToForum(userTgId, messageId)
    }

    private fun handleForumSupergroup(msg: JsonNode) {
        if (telegramProperties.token.trim().isEmpty()) {
            return
        }
        val threadNode = msg.path("message_thread_id")
        if (threadNode.isMissingNode || threadNode.isNull) {
            return
        }
        val threadId = threadNode.asInt()
        val from = msg.path("from")
        if (!from.has("id")) {
            return
        }
        if (from.path("id").asLong() == telegramSupportBotIdentity.botUserId()) {
            return
        }
        val messageId = msg.path("message_id").asInt()
        telegramSupportRelayService.copyAdminReplyToUser(threadId, messageId)
    }

    companion object {
        const val NOT_REGISTERED_REPLY =
            "Чтобы написать в поддержку, сначала откройте приложение Polemica Fantasy."
        const val START_REPLY =
            "Напишите сообщение ниже — оно попадёт команде поддержки. Ответ придёт сюда же в Telegram."
    }
}
