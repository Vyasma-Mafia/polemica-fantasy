package io.github.mralex1810.fantasy.service

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.mralex1810.fantasy.config.TelegramProperties
import io.github.mralex1810.fantasy.config.TelegramSupportProperties
import io.github.mralex1810.fantasy.entity.TelegramUser
import io.github.mralex1810.fantasy.repository.TelegramUserRepository
import io.github.mralex1810.fantasy.telegram.TelegramBotApiClient
import io.github.mralex1810.fantasy.telegram.TelegramSupportBotIdentity
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mockito.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class TelegramSupportUpdateServiceTest {

    private val objectMapper = ObjectMapper()
    private lateinit var telegramSupportProperties: TelegramSupportProperties
    private lateinit var telegramProperties: TelegramProperties
    private lateinit var telegramUserRepository: TelegramUserRepository
    private lateinit var relay: TelegramSupportRelayService
    private lateinit var telegramBotApiClient: TelegramBotApiClient
    private lateinit var botIdentity: TelegramSupportBotIdentity
    private lateinit var service: TelegramSupportUpdateService

    @BeforeEach
    fun setup() {
        telegramSupportProperties = TelegramSupportProperties().apply {
            forumChatId = FORUM_CHAT_ID
        }
        telegramProperties = TelegramProperties().apply { token = "test-token" }
        telegramUserRepository = mock(TelegramUserRepository::class.java)
        relay = mock(TelegramSupportRelayService::class.java)
        telegramBotApiClient = mock(TelegramBotApiClient::class.java)
        botIdentity = mock(TelegramSupportBotIdentity::class.java)
        whenever(botIdentity.botUserId()).thenReturn(BOT_ID)
        service = TelegramSupportUpdateService(
            telegramSupportProperties,
            telegramProperties,
            telegramUserRepository,
            relay,
            telegramBotApiClient,
            botIdentity,
        )
    }

    @Test
    fun `private message from unknown user sends registration hint`() {
        whenever(telegramUserRepository.findByTelegramId(USER_TG_ID)).thenReturn(null)
        val json = """
            {"update_id":1,"message":{"message_id":10,"from":{"id":$USER_TG_ID},"chat":{"id":$USER_TG_ID,"type":"private"},"text":"hi"}}
        """.trimIndent()
        service.processUpdate(objectMapper.readTree(json))
        verify(telegramBotApiClient).sendMessage(
            "test-token",
            USER_TG_ID,
            TelegramSupportUpdateService.NOT_REGISTERED_REPLY,
        )
        verify(relay, never()).ensureTopicAndForwardToForum(anyLong(), anyInt())
    }

    @Test
    fun `private message from registered user forwards to forum`() {
        val user = TelegramUser(telegramId = USER_TG_ID).apply { id = 5L }
        whenever(telegramUserRepository.findByTelegramId(USER_TG_ID)).thenReturn(user)
        val json = """
            {"update_id":1,"message":{"message_id":77,"from":{"id":$USER_TG_ID},"chat":{"id":$USER_TG_ID,"type":"private"},"text":"help"}}
        """.trimIndent()
        service.processUpdate(objectMapper.readTree(json))
        verify(relay).ensureTopicAndForwardToForum(USER_TG_ID, 77)
        org.mockito.Mockito.verifyNoMoreInteractions(telegramBotApiClient)
    }

    @Test
    fun `start command sends hint only`() {
        val user = TelegramUser(telegramId = USER_TG_ID).apply { id = 5L }
        whenever(telegramUserRepository.findByTelegramId(USER_TG_ID)).thenReturn(user)
        val json = """
            {"update_id":1,"message":{"message_id":1,"from":{"id":$USER_TG_ID},"chat":{"id":$USER_TG_ID,"type":"private"},"text":"/start"}}
        """.trimIndent()
        service.processUpdate(objectMapper.readTree(json))
        verify(telegramBotApiClient).sendMessage(
            "test-token",
            USER_TG_ID,
            TelegramSupportUpdateService.START_REPLY,
        )
        verify(relay, never()).ensureTopicAndForwardToForum(anyLong(), anyInt())
    }

    @Test
    fun `forum message from admin copies to user`() {
        val json = """
            {"update_id":2,"message":{"message_id":99,"from":{"id":777},"chat":{"id":$FORUM_CHAT_ID,"type":"supergroup"},"message_thread_id":42,"text":"hello back"}}
        """.trimIndent()
        service.processUpdate(objectMapper.readTree(json))
        verify(relay).copyAdminReplyToUser(42, 99)
    }

    @Test
    fun `message from bot in forum is ignored`() {
        val json = """
            {"update_id":2,"message":{"message_id":99,"from":{"id":$BOT_ID},"chat":{"id":$FORUM_CHAT_ID,"type":"supergroup"},"message_thread_id":42,"text":"fwd"}}
        """.trimIndent()
        service.processUpdate(objectMapper.readTree(json))
        verify(relay, never()).copyAdminReplyToUser(anyInt(), anyInt())
    }

    companion object {
        private const val BOT_ID = 999L
        private const val USER_TG_ID = 100L
        private const val FORUM_CHAT_ID = -1003620873111L
    }
}
