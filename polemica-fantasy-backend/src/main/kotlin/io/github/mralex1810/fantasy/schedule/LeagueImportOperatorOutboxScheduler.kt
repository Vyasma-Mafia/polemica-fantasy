package io.github.mralex1810.fantasy.schedule

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.mralex1810.fantasy.config.TelegramLeagueImportProperties
import io.github.mralex1810.fantasy.config.TelegramProperties
import io.github.mralex1810.fantasy.repository.LeagueImportOutboxRow
import io.github.mralex1810.fantasy.repository.LeagueImportRepository
import io.github.mralex1810.fantasy.service.leagueimport.LeagueAnnouncementDraft
import io.github.mralex1810.fantasy.service.leagueimport.LeagueImportActionTokenCodec
import io.github.mralex1810.fantasy.telegram.InlineKeyboardButton
import io.github.mralex1810.fantasy.telegram.InlineKeyboardMarkup
import io.github.mralex1810.fantasy.telegram.TelegramBotApiClient
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Component
@ConditionalOnProperty(prefix = "spring.task.scheduling", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class LeagueImportOperatorOutboxScheduler(
    private val properties: TelegramLeagueImportProperties,
    private val telegramProperties: TelegramProperties,
    private val repository: LeagueImportRepository,
    private val objectMapper: ObjectMapper,
    private val tokenCodec: LeagueImportActionTokenCodec,
    private val telegramBotApiClient: TelegramBotApiClient,
    transactionManager: PlatformTransactionManager,
) {
    private val transaction = TransactionTemplate(transactionManager)
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "${'$'}{telegram.league-import.outbox-poll-ms:1000}")
    fun deliver() {
        if (!properties.enabled || !properties.operatorNotificationsEnabled || telegramProperties.token.isBlank()) return
        repeat(10) {
            val row = transaction.execute { repository.leaseOutbox() } ?: return
            deliver(row)
        }
    }

    private fun deliver(row: LeagueImportOutboxRow) {
        try {
            val isActionOffer = row.eventType == "CREATE_PREVIEW" || row.eventType == "CREATE_CONFIRM" ||
                row.eventType == "FINALIZE_PREVIEW" || row.eventType == "FINALIZE_CONFIRM"
            val action = if (isActionOffer) row.actionId?.let(repository::findAction) else null
            if (isActionOffer && (action == null || action.status != "NOTIFY_PENDING")) {
                transaction.executeWithoutResult {
                    repository.supersedeOutbox(row.id)
                }
                return
            }
            val callback = action?.takeIf { properties.callbackEnabled }?.let { tokenCodec.encode(it.id) }
            val (text, button) = render(row, callback)
            val replyMarkup = when {
                button != null -> InlineKeyboardMarkup(
                    listOf(listOf(InlineKeyboardButton.Callback(button, callback!!))),
                )
                row.eventType == "CREATED" || row.eventType == "FINALIZED" || row.eventType == "INCIDENT" -> InlineKeyboardMarkup(
                    listOf(
                        listOf(
                            InlineKeyboardButton.Url(
                                when (row.eventType) {
                                    "CREATED" -> "Открыть серию и назначить игроков"
                                    "FINALIZED" -> "Открыть финализированную серию"
                                    else -> "Открыть серию для проверки"
                                },
                                "${properties.adminBaseUrl.trimEnd('/')}/series/${row.payload.path("seriesId").asLong()}",
                            ),
                        ),
                    ),
                )
                else -> null
            }
            val messageId = telegramBotApiClient.sendMessage(
                telegramProperties.token,
                properties.operatorChatId,
                text,
                replyMarkup = replyMarkup,
            ).toLong()
            transaction.executeWithoutResult {
                repository.finishOutbox(row.id, delivered = true, messageId = messageId)
                if (row.eventType == "AUTO_CREATE_PENDING" || row.eventType == "AUTO_FINALIZE_PENDING") {
                    val holdSeconds = row.payload.path("holdSeconds").asLong(MIN_AUTO_HOLD_SECONDS)
                        .coerceAtLeast(MIN_AUTO_HOLD_SECONDS)
                    repository.releaseJobsAfterNotificationDelivery(row.id, holdSeconds)
                }
                if (action != null) {
                    val ttl = if (action.actionType.endsWith("CONFIRM")) properties.confirmationTtlSeconds else properties.previewTtlSeconds
                    repository.markActionOffered(action.id, messageId, java.time.Instant.now().plusSeconds(ttl))
                    repository.updateItemState(
                        action.itemId,
                        when {
                            action.actionType.endsWith("CONFIRM") -> "AWAITING_CONFIRMATION"
                            action.actionType.startsWith("FINALIZE_") -> "READY_TO_FINALIZE"
                            else -> "READY_TO_PREVIEW"
                        },
                    )
                }
            }
        } catch (e: Exception) {
            log.warn("Telegram league import operator outbox {} delivery failed: {}", row.eventKey, e.message)
            transaction.executeWithoutResult { repository.finishOutbox(row.id, delivered = false, error = e.message) }
        }
    }

    private fun render(row: LeagueImportOutboxRow, callback: String?): Pair<String, String?> = when (row.eventType) {
        "CREATE_PREVIEW", "CREATE_CONFIRM" -> {
            val draft = objectMapper.treeToValue(row.payload.path("draft"), LeagueAnnouncementDraft::class.java)
            val time = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm 'МСК'")
                .withZone(ZoneId.of("Europe/Moscow")).format(draft.startsAt)
            val deadline = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm 'МСК'")
                .withZone(ZoneId.of("Europe/Moscow")).format(draft.teamDeadline)
            val prefix = if (row.eventType == "CREATE_CONFIRM") "[CONFIRM][PRODUCTION WRITE]" else "[READY][NO PRODUCTION WRITE YET]"
            val label = callback?.let {
                if (row.eventType == "CREATE_CONFIRM") "Создать без состава" else "Проверить создание"
            }
            "$prefix\n${draft.name}\nНачало: $time\nДедлайн составов: $deadline\n" +
                "Турнир: ${row.payload.path("tournamentName").asText("#${draft.tournamentId}")} (#${draft.tournamentId})\n" +
                "Префикс игр: ${draft.namePrefix}\nДата игр: ${draft.gameStartedOn}\n" +
                "Статус: UPCOMING\nРостер: 0 — состав нужно назначить в админке\nИсточник: ${draft.sourceUrl}" to label
        }
        "CREATED" -> {
            "[PRODUCTION][SERIES_CREATED]\n${row.payload.path("name").asText()} (#${row.payload.path("seriesId").asLong()})\n" +
                "Ростер: 0 — назначьте игроков в админке\nИсточник: ${row.payload.path("sourceUrl").asText()}" to null
        }
        "AUTO_CREATE_PENDING" -> {
            "[AUTO][CREATE_PENDING][PRODUCTION WRITE SCHEDULED]\n" +
                "${row.payload.path("league").asText()} · серия ${row.payload.path("seriesNumber").asLong()} будет создана без состава.\n" +
                "ETA: не ранее чем через ${row.payload.path("holdSeconds").asLong(MIN_AUTO_HOLD_SECONDS)} секунд после доставки этого уведомления.\n" +
                "Режим: ${row.payload.path("mode").asText()} · generation: ${row.payload.path("policyGeneration").asText()}\n" +
                "До этого времени операция будет отменена, если закрыть production writes или сменить mode/generation.\n" +
                "Источник: ${row.payload.path("sourceUrl").asText()}" to null
        }
        "FINALIZE_PREVIEW", "FINALIZE_CONFIRM" -> {
            val draft = objectMapper.treeToValue(row.payload.path("draft"), io.github.mralex1810.fantasy.service.leagueimport.LeagueResultDraft::class.java)
            val confirm = row.eventType == "FINALIZE_CONFIRM"
            val prefix = if (confirm) "[CONFIRM][PRODUCTION FINALIZATION]" else "[READY][NO PRODUCTION WRITE YET]"
            val winners = draft.games.joinToString(", ") { "${it.number}: ${if (it.winner.name == "MAFIA") "мафия" else "мирные"}" }
            val label = callback?.let { if (confirm) "Финализировать серию" else "Проверить финализацию" }
            "$prefix\n${draft.league}, серия ${draft.seriesNumber}\nИгры: $winners\n" +
                "Все ${draft.expectedGameCount} игр синхронизированы, очки полны, победители совпадают.\n" +
                "Источник: ${draft.sourceUrl}" to label
        }
        "FINALIZED" -> {
            "[PRODUCTION][SERIES_FINALIZED]\n${row.payload.path("league").asText()} · серия ${row.payload.path("seriesNumber").asLong()} " +
                "(#${row.payload.path("seriesId").asLong()}) финализирована.\n" +
                "Награды начислены: ${row.payload.path("rewardsDistributed").asInt()}\n" +
                "Использований карт списано: ${row.payload.path("cardsDecremented").asInt()}\n" +
                "Источник: ${row.payload.path("sourceUrl").asText()}" to null
        }
        "AUTO_FINALIZE_PENDING" -> {
            "[AUTO][FINALIZE_PENDING][PRODUCTION WRITE SCHEDULED]\n" +
                "${row.payload.path("league").asText()} · серия ${row.payload.path("seriesNumber").asLong()} " +
                "(#${row.payload.path("seriesId").asLong()}) готова к автофинализации.\n" +
                "ETA: не ранее чем через ${row.payload.path("holdSeconds").asLong(MIN_AUTO_HOLD_SECONDS)} секунд после доставки этого уведомления.\n" +
                "Режим: ${row.payload.path("mode").asText()} · generation: ${row.payload.path("policyGeneration").asText()}\n" +
                "До этого времени операция будет отменена, если закрыть production writes/result processing или сменить mode/generation.\n" +
                "Источник: ${row.payload.path("sourceUrl").asText()}" to null
        }
        "AUTO_CANCELLED" -> {
            val mode = row.payload.path("mode").asText("AUTOMATIC")
            val origin = if (mode == "MANUAL") "MANUAL" else "AUTO"
            "[$origin][CANCELLED][NO PRODUCTION WRITE]\n" +
                "Операция ${row.payload.path("operation").asText("-")} отменена и не возобновится автоматически.\n" +
                "Лига/серия: ${row.payload.path("league").asText("-")} · ${row.payload.path("seriesNumber").asText("-")}\n" +
                "Причина: ${row.payload.path("reason").asText("safety gate changed")}\n" +
                "Режим: ${row.payload.path("mode").asText("-")} · generation: ${row.payload.path("policyGeneration").asText("-")}\n" +
                "Источник: ${row.payload.path("sourceUrl").asText("-")}" to null
        }
        "INCIDENT" -> {
            "[INCIDENT][PRODUCTION DATA NOT ROLLED BACK]\n" +
                "Источник изменён после production write для серии #${row.payload.path("seriesId").asLong()}.\n" +
                "Автоматический rollback, sync и rescore не выполнялись.\n" +
                "Источник: ${row.payload.path("sourceUrl").asText()}" to null
        }
        "BLOCKED" -> {
            "[SHADOW][NO PRODUCTION WRITE]\nСоздание недоступно: ${row.payload.path("reason").asText()}\n" +
                "Лига: ${row.payload.path("league").asText("-")}\nИсточник: ${row.payload.path("sourceUrl").asText()}" to null
        }
        "RESULT_SHADOW" -> {
            "[SHADOW][NO PRODUCTION WRITE]\nНайден кандидат результата, лига ${row.payload.path("league").asText("-")}.\n" +
                "Статус: ${row.payload.path("reason").asText("result processing disabled")}.\n" +
                "Источник: ${row.payload.path("sourceUrl").asText()}" to null
        }
        "CREATE_CONFLICT", "CREATE_FAILED" -> {
            val title = if (row.eventType == "CREATE_CONFLICT") "[PRODUCTION][SERIES_NOT_CREATED][CONFLICT]" else
                "[PRODUCTION][SERIES_NOT_CREATED][FAILED]"
            "$title\nСоздание не выполнено: ${row.payload.path("reason").asText("неизвестная ошибка")}\n" +
                "Проверьте серию в админке и повторите импорт после устранения причины.\n" +
                "Источник: ${row.payload.path("sourceUrl").asText()}" to null
        }
        "FINALIZE_FAILED" -> {
            "[PRODUCTION][SERIES_NOT_FINALIZED][FAILED]\nФинализация не выполнена: ${row.payload.path("reason").asText()}\n" +
                "Источник: ${row.payload.path("sourceUrl").asText()}" to null
        }
        else -> error("Unsupported league import outbox event ${row.eventType}")
    }

    private companion object {
        const val MIN_AUTO_HOLD_SECONDS = 120L
    }
}
