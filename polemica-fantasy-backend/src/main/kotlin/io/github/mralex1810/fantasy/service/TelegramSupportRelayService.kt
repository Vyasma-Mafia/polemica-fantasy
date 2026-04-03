package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.config.TelegramProperties
import io.github.mralex1810.fantasy.config.TelegramSupportProperties
import io.github.mralex1810.fantasy.entity.TelegramSupportTopic
import io.github.mralex1810.fantasy.entity.TelegramUser
import io.github.mralex1810.fantasy.repository.TelegramSupportTopicRepository
import io.github.mralex1810.fantasy.repository.TelegramUserRepository
import io.github.mralex1810.fantasy.telegram.TelegramBotApiClient
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class TelegramSupportRelayService(
    private val telegramUserRepository: TelegramUserRepository,
    private val telegramSupportTopicRepository: TelegramSupportTopicRepository,
    private val telegramBotApiClient: TelegramBotApiClient,
    private val telegramProperties: TelegramProperties,
    private val telegramSupportProperties: TelegramSupportProperties,
) {

    @Transactional
    fun ensureTopicAndForwardToForum(telegramId: Long, messageId: Int) {
        val token = telegramProperties.token.trim()
        val forumChatId = telegramSupportProperties.forumChatId
        val user = telegramUserRepository.findByTelegramIdForUpdate(telegramId) ?: return
        val userId = user.id ?: return
        var topic = telegramSupportTopicRepository.findByTelegramUser_Id(userId)
        if (topic == null) {
            val threadId = telegramBotApiClient.createForumTopic(token, forumChatId, topicDisplayName(user))
            topic = TelegramSupportTopic(telegramUser = user, forumMessageThreadId = threadId)
            telegramSupportTopicRepository.save(topic)
        }
        telegramBotApiClient.forwardMessage(token, forumChatId, telegramId, messageId, topic.forumMessageThreadId)
    }

    @Transactional(readOnly = true)
    fun copyAdminReplyToUser(forumMessageThreadId: Int, sourceMessageId: Int) {
        val topic = telegramSupportTopicRepository.findByForumMessageThreadIdWithUser(forumMessageThreadId) ?: return
        val token = telegramProperties.token.trim()
        val userChatId = topic.telegramUser.telegramId
        telegramBotApiClient.sendMessage(token, userChatId, SUPPORT_REPLY_HEADER)
        telegramBotApiClient.copyMessage(
            token,
            userChatId,
            telegramSupportProperties.forumChatId,
            sourceMessageId,
        )
    }

    private fun topicDisplayName(user: TelegramUser): String {
        val base = user.displayName?.takeIf { it.isNotBlank() }
            ?: user.username?.takeIf { it.isNotBlank() }?.let { "@$it" }
            ?: "id ${user.telegramId}"
        return base.take(MAX_TOPIC_NAME_LENGTH)
    }

    companion object {
        private const val MAX_TOPIC_NAME_LENGTH = 128

        /** Строка перед копией сообщения админа в личку пользователю. */
        const val SUPPORT_REPLY_HEADER = "Ответ от поддержки:"
    }
}
