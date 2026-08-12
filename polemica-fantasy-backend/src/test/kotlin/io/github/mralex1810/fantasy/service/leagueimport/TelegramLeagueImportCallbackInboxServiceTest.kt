package io.github.mralex1810.fantasy.service.leagueimport

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.mralex1810.fantasy.config.TelegramLeagueImportProperties
import io.github.mralex1810.fantasy.repository.LeagueImportRepository
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.util.UUID

class TelegramLeagueImportCallbackInboxServiceTest {
    @Test
    fun `accept persists sanitized callback evidence before processing`() {
        val properties = TelegramLeagueImportProperties(enabled = true, callbackEnabled = true, callbackSigningSecret = "secret")
        val codec = LeagueImportActionTokenCodec(properties)
        val repository = mock<LeagueImportRepository>()
        val service = TelegramLeagueImportCallbackInboxService(properties, codec, repository)
        val actionId = UUID.randomUUID()
        val token = codec.encode(actionId)
        val root = ObjectMapper().readTree(
            """{"update_id":7,"callback_query":{"id":"q7","data":"$token","from":{"id":42,"is_bot":false,"username":"op"},"message":{"message_id":99,"chat":{"id":-5166305654}}}}""",
        )

        assertTrue(service.accept(root))

        verify(repository).insertCallbackInbox(
            updateId = eq(7),
            queryId = eq("q7"),
            actionId = eq(actionId),
            tokenHash = eq(codec.hash(token)),
            operatorChatId = eq(-5166305654),
            operatorMessageId = eq(99),
            actorTelegramId = eq(42),
            actorUsername = eq("op"),
            actorIsBot = eq(false),
            ordinaryMessage = eq(true),
        )
    }
}
