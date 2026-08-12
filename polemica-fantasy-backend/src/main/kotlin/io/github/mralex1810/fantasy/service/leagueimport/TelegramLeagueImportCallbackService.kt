package io.github.mralex1810.fantasy.service.leagueimport

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.mralex1810.fantasy.config.TelegramLeagueImportProperties
import io.github.mralex1810.fantasy.config.LeagueImportAutomationMode
import io.github.mralex1810.fantasy.repository.LeagueImportActionRow
import io.github.mralex1810.fantasy.repository.LeagueImportItemRow
import io.github.mralex1810.fantasy.repository.LeagueImportRepository
import io.github.mralex1810.fantasy.service.SeriesCompletionService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

data class LeagueImportCallbackResult(val handled: Boolean, val callbackQueryId: String = "", val answer: String = "")

@Service
class TelegramLeagueImportCallbackService(
    private val properties: TelegramLeagueImportProperties,
    private val tokenCodec: LeagueImportActionTokenCodec,
    private val parser: LeagueAnnouncementParser,
    private val ingestService: LeagueImportIngestService,
    private val repository: LeagueImportRepository,
    private val objectMapper: ObjectMapper,
    private val completionService: SeriesCompletionService,
    private val resultProcessingService: LeagueImportResultProcessingService,
) {
    @Transactional
    fun tryHandle(root: JsonNode): LeagueImportCallbackResult {
        val callback = root.path("callback_query")
        val data = callback.path("data").asText("")
        if (!data.startsWith("lic:")) return LeagueImportCallbackResult(false)
        val queryId = callback.path("id").asText("")
        if (!properties.enabled || !properties.callbackEnabled) return handled(queryId, "Кнопки импорта временно отключены")
        val actionId = tokenCodec.decode(data) ?: return handled(queryId, "Некорректная или устаревшая кнопка")
        val action = repository.findAction(actionId, lock = true) ?: return handled(queryId, "Действие не найдено")
        if (tokenCodec.hash(data) != action.tokenHash) return handled(queryId, "Некорректная кнопка")

        val message = callback.path("message")
        val chatId = message.path("chat").path("id").takeIf { it.isIntegralNumber }?.asLong()
        val messageId = message.path("message_id").takeIf { it.isIntegralNumber }?.asLong()
        val from = callback.path("from")
        val actorId = from.path("id").takeIf { it.isIntegralNumber }?.asLong()
        val humanActor = actorId != null && !from.path("is_bot").asBoolean(false)
        if (chatId != properties.operatorChatId || chatId != action.operatorChatId) return handled(queryId, "Эта кнопка доступна только в админском чате")
        if (messageId == null || messageId != action.operatorMessageId) return handled(queryId, "Сообщение кнопки не совпадает")
        if (!humanActor) return handled(queryId, "Действие доступно только человеку")
        if (action.status != "OFFERED") return handled(queryId, "Действие уже обработано или устарело")
        if (action.boundActorTelegramId != null && action.boundActorTelegramId != actorId) {
            return handled(queryId, "Подтвердить может только тот, кто открыл preview")
        }

        val item = repository.findItemById(action.itemId, lock = true) ?: return handled(queryId, "Кандидат не найден")
        val createReady = if (action.actionType.startsWith("CREATE_")) freshReady(item, action) else null
        val finalizeReady = if (action.actionType.startsWith("FINALIZE_")) freshFinalize(item, action) else null
        if (createReady == null && finalizeReady == null) {
            repository.updateActionStatus(action.id, "STALE")
            return handled(queryId, "Кандидат изменился или больше не готов")
        }
        if (!action.expiresAt.isAfter(Instant.now())) {
            repository.updateActionStatus(action.id, "EXPIRED")
            if (createReady != null) {
                ingestService.offerAction(
                    item.id, item.version, item.currentRevision, createReady, "CREATE_PREVIEW", null,
                    properties.previewTtlSeconds,
                )
            } else {
                resultProcessingService.offerFinalizeAction(
                    item.id, item.version, item.currentRevision, finalizeReady!!.second, finalizeReady.first,
                    "FINALIZE_PREVIEW", null, properties.previewTtlSeconds,
                )
            }
            repository.updateItemState(item.id, "PREVIEW_PENDING")
            return handled(queryId, "Срок кнопки истёк — отправляю новую проверку")
        }
        val updateId = root.path("update_id").takeIf { it.isIntegralNumber }?.asLong()
            ?: return handled(queryId, "Telegram update не содержит идентификатор")
        val actorUsername = from.path("username").asText("").takeIf { it.isNotBlank() }

        return when (action.actionType) {
            "CREATE_PREVIEW" -> {
                if (!repository.recordCallback(action.id, "OFFERED", "CALLBACK_RECEIVED", updateId, queryId, actorId, actorUsername)) {
                    handled(queryId, "Действие уже обработано")
                } else {
                    ingestService.offerAction(
                        item.id, item.version, item.currentRevision, createReady!!, "CREATE_CONFIRM", actorId,
                        properties.confirmationTtlSeconds,
                    )
                    repository.updateItemState(item.id, "AWAITING_CONFIRMATION")
                    repository.audit(item.id, action.id, actorId, "PREVIEW_REQUESTED", "ACCEPTED")
                    handled(queryId, "Проверки пройдены — отправляю подтверждение")
                }
            }
            "CREATE_CONFIRM" -> {
                val mode = properties.policy(createReady!!.draft.league)?.createMode
                if (!properties.productionWritesEnabled || mode != LeagueImportAutomationMode.MANUAL) {
                    return handled(queryId, "Создание серий временно отключено")
                }
                if (!repository.recordCallback(action.id, "OFFERED", "CREATE_PENDING", updateId, queryId, actorId, actorUsername)) {
                    handled(queryId, "Действие уже обработано")
                } else {
                    repository.updateItemState(item.id, "CREATE_PENDING")
                    repository.audit(item.id, action.id, actorId, "CREATE_CONFIRMED", "QUEUED")
                    handled(queryId, "Создание поставлено в очередь")
                }
            }
            "FINALIZE_PREVIEW" -> {
                val (draft, readinessChecksum) = finalizeReady!!
                if (!repository.recordCallback(action.id, "OFFERED", "CALLBACK_RECEIVED", updateId, queryId, actorId, actorUsername)) {
                    handled(queryId, "Действие уже обработано")
                } else {
                    resultProcessingService.offerFinalizeAction(
                        item.id, item.version, item.currentRevision, readinessChecksum, draft,
                        "FINALIZE_CONFIRM", actorId, properties.confirmationTtlSeconds,
                    )
                    repository.updateItemState(item.id, "AWAITING_CONFIRMATION")
                    repository.audit(item.id, action.id, actorId, "FINALIZE_PREVIEW_REQUESTED", "ACCEPTED")
                    handled(queryId, "Готовность подтверждена — отправляю финальное подтверждение")
                }
            }
            "FINALIZE_CONFIRM" -> {
                val (draft, readinessChecksum) = finalizeReady!!
                val mode = properties.policy(draft.league)?.finalizeMode
                if (!properties.resultProcessingEnabled || !properties.productionWritesEnabled || mode != LeagueImportAutomationMode.MANUAL) {
                    return handled(queryId, "Финализация серий временно отключена")
                }
                if (!repository.recordCallback(action.id, "OFFERED", "FINALIZE_PENDING", updateId, queryId, actorId, actorUsername)) {
                    handled(queryId, "Действие уже обработано")
                } else {
                    repository.enqueueJob(
                        item.id, item.version, item.currentRevision, item.draftChecksum!!, properties.policyGeneration,
                        "FINALIZE", item.targetSeriesId, actorId, readinessChecksum,
                    )
                    repository.updateItemState(item.id, "FINALIZE_PENDING")
                    repository.audit(item.id, action.id, actorId, "FINALIZE_CONFIRMED", "QUEUED")
                    handled(queryId, "Финализация поставлена в очередь")
                }
            }
            else -> handled(queryId, "Неподдерживаемое действие")
        }
    }

    private fun freshReady(item: LeagueImportItemRow, action: LeagueImportActionRow): LeagueAnnouncementParseResult.Ready? {
        if (item.targetSeriesId != null || item.version != action.itemVersion || item.currentRevision != action.sourceRevision ||
            item.draftChecksum != action.draftChecksum || item.policyGeneration != properties.policyGeneration ||
            action.policyGeneration != properties.policyGeneration ||
            item.state in setOf("APPLIED", "CONFLICT", "INCIDENT")) return null
        val revision = repository.currentRevision(item.id) ?: return null
        if (revision.revision != action.sourceRevision || revision.contentHash != item.currentContentHash) return null
        val parsed = parser.parse(
            revision.rawText,
            revision.postedAt,
            "https://t.me/polemica_closed_league/${item.sourceMessageId}",
        ) as? LeagueAnnouncementParseResult.Ready ?: return null
        if (parsed.checksum != action.draftChecksum) return null
        if (properties.policy(parsed.draft.league)?.createMode != LeagueImportAutomationMode.MANUAL) return null
        return ingestService.validateProductionPolicy(parsed) as? LeagueAnnouncementParseResult.Ready
    }

    private fun freshFinalize(item: LeagueImportItemRow, action: LeagueImportActionRow): Pair<LeagueResultDraft, String>? {
        if (item.targetSeriesId == null || item.classification != "RESULT" || item.version != action.itemVersion ||
            item.currentRevision != action.sourceRevision || item.policyGeneration != properties.policyGeneration ||
            action.policyGeneration != properties.policyGeneration ||
            item.state in setOf("FINALIZED", "INCIDENT", "FAILED")) return null
        val revision = repository.currentRevision(item.id) ?: return null
        if (revision.revision != action.sourceRevision || revision.contentHash != item.currentContentHash) return null
        val draft = item.draftJson?.let { runCatching { objectMapper.treeToValue(it, LeagueResultDraft::class.java) }.getOrNull() }
            ?: return null
        if (properties.policy(draft.league)?.finalizeMode != LeagueImportAutomationMode.MANUAL) return null
        val completion = completionService.evaluate(item.targetSeriesId)
        if (!completion.ready || completion.checksum != action.draftChecksum) return null
        return draft to completion.checksum!!
    }

    private fun handled(queryId: String, answer: String) = LeagueImportCallbackResult(true, queryId, answer)
}
