package io.github.mralex1810.fantasy.schedule

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.mralex1810.fantasy.config.TelegramLeagueImportProperties
import io.github.mralex1810.fantasy.config.TelegramProperties
import io.github.mralex1810.fantasy.repository.LeagueImportCallbackInboxRow
import io.github.mralex1810.fantasy.repository.LeagueImportRepository
import io.github.mralex1810.fantasy.service.leagueimport.LeagueImportActionTokenCodec
import io.github.mralex1810.fantasy.service.leagueimport.TelegramLeagueImportCallbackService
import io.github.mralex1810.fantasy.telegram.TelegramBotApiClient
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@Component
@ConditionalOnProperty(prefix = "spring.task.scheduling", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class LeagueImportCallbackInboxScheduler(
    private val properties: TelegramLeagueImportProperties,
    private val telegramProperties: TelegramProperties,
    private val repository: LeagueImportRepository,
    private val callbackService: TelegramLeagueImportCallbackService,
    private val tokenCodec: LeagueImportActionTokenCodec,
    private val objectMapper: ObjectMapper,
    private val telegramBotApiClient: TelegramBotApiClient,
    transactionManager: PlatformTransactionManager,
) {
    private val transaction = TransactionTemplate(transactionManager)
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "${'$'}{telegram.league-import.callback-poll-ms:250}")
    fun process() {
        if (!properties.enabled || telegramProperties.token.isBlank()) return
        repeat(10) {
            val row = transaction.execute { repository.leaseCallbackInbox() } ?: return
            process(row)
        }
    }

    private fun process(row: LeagueImportCallbackInboxRow) {
        try {
            val result = callbackService.tryHandle(asTelegramUpdate(row))
            transaction.executeWithoutResult { repository.finishCallbackInbox(row.id, processed = true, answer = result.answer) }
            if (result.callbackQueryId.isNotBlank()) {
                runCatching {
                    telegramBotApiClient.answerCallbackQuery(telegramProperties.token, result.callbackQueryId, result.answer)
                }.onFailure {
                    log.warn("Telegram league import callback inbox {} answer failed after domain commit: {}", row.id, it.message)
                }
            }
        } catch (e: Exception) {
            log.warn("Telegram league import callback inbox {} failed: {}", row.id, e.message)
            transaction.executeWithoutResult { repository.finishCallbackInbox(row.id, processed = false, error = e.message) }
        }
    }

    private fun asTelegramUpdate(row: LeagueImportCallbackInboxRow) = objectMapper.createObjectNode().apply {
        put("update_id", row.updateId)
        putObject("callback_query").apply {
            put("id", row.queryId)
            put("data", row.actionId?.let(tokenCodec::encode) ?: "lic:invalid")
            putObject("from").apply {
                row.actorTelegramId?.let { put("id", it) }
                put("is_bot", row.actorIsBot)
                row.actorUsername?.let { put("username", it) }
            }
            if (row.ordinaryMessage) {
                putObject("message").apply {
                    row.operatorMessageId?.let { put("message_id", it) }
                    putObject("chat").apply { row.operatorChatId?.let { put("id", it) } }
                }
            } else {
                put("inline_message_id", "unsupported")
            }
        }
    }
}
