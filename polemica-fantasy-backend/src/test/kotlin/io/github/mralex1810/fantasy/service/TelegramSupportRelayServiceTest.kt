package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.config.TelegramProperties
import io.github.mralex1810.fantasy.config.TelegramSupportProperties
import io.github.mralex1810.fantasy.entity.TelegramSupportTopic
import io.github.mralex1810.fantasy.entity.TelegramUser
import io.github.mralex1810.fantasy.repository.TelegramSupportTopicRepository
import io.github.mralex1810.fantasy.repository.TelegramUserRepository
import io.github.mralex1810.fantasy.telegram.TelegramBotApiClient
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.whenever

class TelegramSupportRelayServiceTest {

    private lateinit var telegramUserRepository: TelegramUserRepository
    private lateinit var topicRepo: TelegramSupportTopicRepository
    private lateinit var telegramBotApiClient: TelegramBotApiClient
    private lateinit var telegramProperties: TelegramProperties
    private lateinit var supportProperties: TelegramSupportProperties
    private lateinit var service: TelegramSupportRelayService

    @BeforeEach
    fun setup() {
        telegramUserRepository = mock(TelegramUserRepository::class.java)
        topicRepo = mock(TelegramSupportTopicRepository::class.java)
        telegramBotApiClient = mock(TelegramBotApiClient::class.java)
        telegramProperties = TelegramProperties().apply { token = "tok" }
        supportProperties = TelegramSupportProperties().apply { forumChatId = -100L }
        service = TelegramSupportRelayService(
            telegramUserRepository,
            topicRepo,
            telegramBotApiClient,
            telegramProperties,
            supportProperties,
        )
    }

    @Test
    fun `copyAdminReplyToUser sends header then copies message`() {
        val user = TelegramUser(telegramId = 555L).apply { id = 1L }
        val topic = TelegramSupportTopic(telegramUser = user, forumMessageThreadId = 7)
        whenever(topicRepo.findByForumMessageThreadIdWithUser(7)).thenReturn(topic)

        service.copyAdminReplyToUser(7, 42)

        val inOrder = inOrder(telegramBotApiClient)
        inOrder.verify(telegramBotApiClient).sendMessage("tok", 555L, TelegramSupportRelayService.SUPPORT_REPLY_HEADER)
        inOrder.verify(telegramBotApiClient).copyMessage("tok", 555L, -100L, 42)
    }
}
