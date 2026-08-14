package io.github.mralex1810.fantasy.schedule

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.mralex1810.fantasy.config.TelegramLeagueImportProperties
import io.github.mralex1810.fantasy.config.TelegramProperties
import io.github.mralex1810.fantasy.repository.LeagueImportOutboxRow
import io.github.mralex1810.fantasy.repository.LeagueImportRepository
import io.github.mralex1810.fantasy.service.leagueimport.LeagueImportActionTokenCodec
import io.github.mralex1810.fantasy.telegram.TelegramBotApiClient
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.AbstractPlatformTransactionManager
import org.springframework.transaction.support.DefaultTransactionStatus
import java.util.UUID

class LeagueImportOperatorOutboxSchedulerTest {
    @Test
    fun `create preview explicitly renders caption substitution`() {
        val row = LeagueImportOutboxRow(
            id = 6,
            eventKey = "preview:24",
            itemId = 10,
            actionId = UUID.randomUUID(),
            eventType = "CREATE_PREVIEW",
            payload = ObjectMapper().readTree(
                """
                {
                  "draft": {
                    "league": "ЗЛ", "seriesNumber": 24, "tournamentId": 13,
                    "name": "ЗЛ. Серия 24", "namePrefix": "ЗЛ'26-2",
                    "gameStartedOn": "2030-08-14", "startsAt": "2030-08-14T16:00:00Z",
                    "teamDeadline": "2030-08-14T16:10:00Z", "expectedGameCount": 5,
                    "sourceUrl": "https://t.me/polemica_closed_league/2247"
                  },
                  "tournamentName": "Закрытая лига. Сезон 2",
                  "roster": {
                    "status": "READY", "expectedCount": 10,
                    "resolved": [
                      {"ocrText":"Воробей (замена из подписи)","productionNickname":"Воробей","tournamentPlayerId":701}
                    ],
                    "substitutions": [
                      {"outgoingNickname":"Монарх","incomingNickname":"Воробей"}
                    ]
                  }
                }
                """.trimIndent(),
            ),
        )

        val (text, button) = scheduler(mock(), mock()).render(row, "callback")

        assertTrue(text.contains("Замены из подписи:"))
        assertTrue(text.contains("Монарх → Воробей"))
        assertTrue(text.contains("Итоговый состав:"))
        assertTrue(button!!.contains("с составом"))
    }

    @Test
    fun `delivers created outcome even though its action is already applied`() {
        val repository = mock<LeagueImportRepository>()
        val telegramClient = mock<TelegramBotApiClient>()
        val row = LeagueImportOutboxRow(
            id = 7,
            eventKey = "created:42",
            itemId = 11,
            actionId = UUID.randomUUID(),
            eventType = "CREATED",
            payload = ObjectMapper().readTree(
                """{"seriesId":42,"name":"ЛП. Серия 35","sourceUrl":"https://t.me/polemica_closed_league/1"}""",
            ),
        )
        whenever(repository.leaseOutbox()).thenReturn(row, null)
        whenever(
            telegramClient.sendMessage(
                eq("test-token"), eq(-5166305654), any(), eq(null), eq(null), any(),
            ),
        ).thenReturn(123)

        LeagueImportOperatorOutboxScheduler(
            properties = TelegramLeagueImportProperties(
                enabled = true,
                operatorNotificationsEnabled = true,
                operatorChatId = -5166305654,
            ),
            telegramProperties = TelegramProperties(token = "test-token"),
            repository = repository,
            objectMapper = ObjectMapper().findAndRegisterModules(),
            tokenCodec = mock<LeagueImportActionTokenCodec>(),
            telegramBotApiClient = telegramClient,
            transactionManager = StubTransactionManager(),
        ).deliver()

        verify(telegramClient).sendMessage(
            eq("test-token"), eq(-5166305654), any(), eq(null), eq(null), any(),
        )
        verify(repository).finishOutbox(7, delivered = true, messageId = 123)
    }

    @Test
    fun `automatic job is released only after pending notification delivery`() {
        val repository = mock<LeagueImportRepository>()
        val telegramClient = mock<TelegramBotApiClient>()
        val row = LeagueImportOutboxRow(
            id = 8,
            eventKey = "auto-create:42",
            itemId = 12,
            actionId = null,
            eventType = "AUTO_CREATE_PENDING",
            payload = ObjectMapper().readTree(
                """{"league":"ЛП","seriesNumber":42,"sourceUrl":"https://t.me/polemica_closed_league/2","holdSeconds":120,"mode":"AUTOMATIC","policyGeneration":"g1"}""",
            ),
        )
        whenever(repository.leaseOutbox()).thenReturn(row, null)
        whenever(telegramClient.sendMessage(eq("test-token"), eq(-5166305654), any(), eq(null), eq(null), isNull()))
            .thenReturn(124)

        scheduler(repository, telegramClient).deliver()

        verify(repository).finishOutbox(8, delivered = true, messageId = 124)
        verify(repository).releaseJobsAfterNotificationDelivery(8, 120)
    }

    @Test
    fun `bot api outage never releases automatic job`() {
        val repository = mock<LeagueImportRepository>()
        val telegramClient = mock<TelegramBotApiClient>()
        val row = LeagueImportOutboxRow(
            id = 9,
            eventKey = "auto-finalize:42",
            itemId = 13,
            actionId = null,
            eventType = "AUTO_FINALIZE_PENDING",
            payload = ObjectMapper().readTree(
                """{"league":"ЛП","seriesNumber":42,"seriesId":99,"sourceUrl":"https://t.me/polemica_closed_league/3","holdSeconds":120,"mode":"AUTOMATIC","policyGeneration":"g1"}""",
            ),
        )
        whenever(repository.leaseOutbox()).thenReturn(row, null)
        whenever(telegramClient.sendMessage(eq("test-token"), eq(-5166305654), any(), eq(null), eq(null), isNull()))
            .thenThrow(IllegalStateException("Bot API unavailable"))

        scheduler(repository, telegramClient).deliver()

        verify(repository).finishOutbox(9, delivered = false, error = "Bot API unavailable")
        verify(repository, never()).releaseJobsAfterNotificationDelivery(any(), any())
    }

    private fun scheduler(repository: LeagueImportRepository, telegramClient: TelegramBotApiClient) =
        LeagueImportOperatorOutboxScheduler(
            properties = TelegramLeagueImportProperties(
                enabled = true,
                operatorNotificationsEnabled = true,
                operatorChatId = -5166305654,
            ),
            telegramProperties = TelegramProperties(token = "test-token"),
            repository = repository,
            objectMapper = ObjectMapper().findAndRegisterModules(),
            tokenCodec = mock<LeagueImportActionTokenCodec>(),
            telegramBotApiClient = telegramClient,
            transactionManager = StubTransactionManager(),
        )

    private class StubTransactionManager : AbstractPlatformTransactionManager() {
        override fun doGetTransaction(): Any = Any()

        override fun doBegin(transaction: Any, definition: TransactionDefinition) = Unit

        override fun doCommit(status: DefaultTransactionStatus) = Unit

        override fun doRollback(status: DefaultTransactionStatus) = Unit
    }
}
