package io.github.mralex1810.fantasy.service.leagueimport

import io.github.mralex1810.fantasy.config.TelegramLeagueImportProperties
import io.github.mralex1810.fantasy.config.LeagueImportAutomationMode
import io.github.mralex1810.fantasy.dto.internal.TelegramLeagueImportEventRequest
import io.github.mralex1810.fantasy.dto.internal.TelegramLeagueImportEventResponse
import io.github.mralex1810.fantasy.entity.TournamentKind
import io.github.mralex1810.fantasy.entity.TournamentStatus
import io.github.mralex1810.fantasy.repository.LeagueImportRepository
import io.github.mralex1810.fantasy.repository.SeriesRepository
import io.github.mralex1810.fantasy.repository.TournamentRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionTemplate
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

@Service
class LeagueImportIngestService(
    private val properties: TelegramLeagueImportProperties,
    private val parser: LeagueAnnouncementParser,
    private val resultParser: LeagueResultParser,
    private val repository: LeagueImportRepository,
    private val tournamentRepository: TournamentRepository,
    private val seriesRepository: SeriesRepository,
    private val tokenCodec: LeagueImportActionTokenCodec,
    transactionManager: PlatformTransactionManager,
) {
    private val cancellationTransaction = TransactionTemplate(transactionManager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
    }

    @Transactional
    fun ingest(keyId: String, deliveryId: UUID, requestTimestamp: Instant, request: TelegramLeagueImportEventRequest): TelegramLeagueImportEventResponse {
        check(properties.enabled && properties.ingestEnabled) { "Telegram league import ingest is disabled" }
        require(request.sourceChannelPeerId == properties.sourceChannelPeerId) { "source channel is not allowlisted" }
        require(request.messageId > 0 && request.revision > 0) { "message and revision must be positive" }
        require(request.sourceVersion.isNotBlank() && request.sourceVersion.length <= 64) { "invalid source version" }
        require(request.rawText.toByteArray().size <= 16_384) { "raw text is too large" }
        require(request.contentHash.matches(Regex("[0-9a-f]{64}"))) { "invalid content hash" }
        require(sha256(request.rawText) == request.contentHash) { "content hash mismatch" }

        val deliveryFingerprint = deliveryFingerprint(request)
        val deliveryNew = repository.insertDelivery(keyId, deliveryId, requestTimestamp, deliveryFingerprint)
        val existing = repository.findItem(request.sourceChannelPeerId, request.messageId, lock = true)
        if (!deliveryNew) {
            require(repository.findDeliveryContentHash(deliveryId) == deliveryFingerprint) {
                "delivery id was reused with different content"
            }
            return TelegramLeagueImportEventResponse(existing?.id, existing?.state ?: "DUPLICATE", existing?.version, duplicate = true)
        }

        val itemId = existing?.id ?: repository.createItem(
            request.sourceChannelPeerId,
            request.messageId,
            request.revision,
            request.sourceVersion,
            request.contentHash,
            "ANNOUNCEMENT",
            leagueHint(request.rawText),
        )
        repository.attachDelivery(deliveryId, itemId)
        val current = existing ?: repository.findItemById(itemId, lock = true)!!
        if (existing != null && request.contentHash == current.currentContentHash) {
            repository.advanceCurrentSourceVersion(itemId, request.sourceVersion)
            return TelegramLeagueImportEventResponse(itemId, current.state, current.version, duplicate = true)
        }
        if (existing != null && request.sourceVersion < current.currentSourceVersion) {
            repository.insertRevision(
                itemId, request.sourceChannelPeerId, request.messageId, request.revision, request.sourceVersion,
                request.postedAt, request.editedAt, request.contentHash, request.rawText, current = false,
            )
            return TelegramLeagueImportEventResponse(itemId, current.state, current.version, duplicate = true)
        }

        if (existing != null) {
            repository.markRevisionsNotCurrent(itemId)
            repository.invalidateUnusedActions(itemId)
            cancelStaleJobsAfterCommit(itemId, current.version)
        }
        repository.insertRevision(
            itemId, request.sourceChannelPeerId, request.messageId, request.revision, request.sourceVersion,
            request.postedAt, request.editedAt, request.contentHash, request.rawText, current = true,
        )
        val sourceUrl = "https://t.me/polemica_closed_league/${request.messageId}"
        val targetFinalized = current.targetSeriesId?.let { seriesRepository.findById(it).orElse(null)?.finalized } == true
        if (current.targetSeriesId != null && (current.classification != "RESULT" || targetFinalized)) {
            val version = repository.updateCurrentItem(
                itemId, request.revision, request.sourceVersion, request.contentHash,
                "IGNORE", current.leagueCode, "INCIDENT", null, null,
                "source changed after series creation",
            )
            repository.audit(itemId, null, null, "SOURCE_CHANGED_AFTER_APPLY", "INCIDENT", mapOf("seriesId" to current.targetSeriesId))
            repository.enqueueOutbox(
                "league-import:$itemId:v$version:INCIDENT", itemId, null, "INCIDENT",
                mapOf(
                    "league" to current.leagueCode,
                    "seriesId" to current.targetSeriesId,
                    "sourceUrl" to sourceUrl,
                    "reason" to "source changed after production write",
                ),
            )
            return TelegramLeagueImportEventResponse(itemId, "INCIDENT", version, duplicate = false)
        }
        if (RESULT_TAG.containsMatchIn(request.rawText.lowercase().replace('ё', 'е'))) {
            return ingestResult(itemId, request, sourceUrl)
        }
        if (current.targetSeriesId != null && current.classification == "RESULT") {
            val version = repository.updateCurrentItem(
                itemId, request.revision, request.sourceVersion, request.contentHash,
                "IGNORE", current.leagueCode, "BLOCKED", null, null, "result source was retracted or no longer parseable",
            )
            repository.enqueueOutbox(
                "league-import:$itemId:v$version:RESULT_SHADOW", itemId, null, "RESULT_SHADOW",
                mapOf("league" to current.leagueCode, "sourceUrl" to sourceUrl, "reason" to "result source was retracted or no longer parseable"),
            )
            return TelegramLeagueImportEventResponse(itemId, "BLOCKED", version, duplicate = false)
        }
        val parsed = parser.parse(request.rawText, request.postedAt, sourceUrl)
        val checked = when (parsed) {
            is LeagueAnnouncementParseResult.Blocked -> parsed
            is LeagueAnnouncementParseResult.Ready -> validateProductionPolicy(parsed)
        }
        val league = (checked as? LeagueAnnouncementParseResult.Ready)?.draft?.league ?: leagueHint(request.rawText)
        val state = if (checked is LeagueAnnouncementParseResult.Ready) "READY_TO_PREVIEW" else "BLOCKED"
        val version = repository.updateCurrentItem(
            itemId, request.revision, request.sourceVersion, request.contentHash,
            if (checked is LeagueAnnouncementParseResult.Ready) "ANNOUNCEMENT" else "IGNORE",
            league, state,
            (checked as? LeagueAnnouncementParseResult.Ready)?.draft,
            (checked as? LeagueAnnouncementParseResult.Ready)?.checksum,
            (checked as? LeagueAnnouncementParseResult.Blocked)?.reason,
            if (checked is LeagueAnnouncementParseResult.Ready) properties.policyGeneration else null,
        )
        if (checked is LeagueAnnouncementParseResult.Ready) {
            val mode = properties.policy(checked.draft.league)?.createMode ?: LeagueImportAutomationMode.DISABLED
            when (mode) {
                LeagueImportAutomationMode.MANUAL -> {
                    offerAction(itemId, version, request.revision, checked, "CREATE_PREVIEW", null, properties.previewTtlSeconds)
                    repository.updateItemState(itemId, "PREVIEW_PENDING")
                }
                LeagueImportAutomationMode.AUTOMATIC -> {
                    val revision = repository.currentRevision(itemId)!!
                    if (properties.productionWritesEnabled && automationEligible(request.postedAt, revision.observedAt)) {
                        val notificationOutboxId = repository.enqueueOutbox(
                            "league-import:$itemId:v$version:AUTO_CREATE_PENDING:${properties.policyGeneration}",
                            itemId, null, "AUTO_CREATE_PENDING",
                            mapOf(
                                "league" to checked.draft.league,
                                "seriesNumber" to checked.draft.seriesNumber,
                                "sourceUrl" to sourceUrl,
                                "holdSeconds" to AUTO_CREATE_HOLD_SECONDS,
                                "mode" to mode.name,
                                "policyGeneration" to properties.policyGeneration,
                            ),
                        )
                        repository.enqueueJob(
                            itemId, version, request.revision, checked.checksum, properties.policyGeneration,
                            "CREATE", null, availableAt = null,
                            pendingNotificationOutboxId = notificationOutboxId,
                        )
                        repository.updateItemState(itemId, "CREATE_PENDING")
                    } else {
                        repository.updateItemState(itemId, "BLOCKED", "automatic creation safety gates are closed")
                        repository.enqueueOutbox(
                            "league-import:$itemId:v$version:BLOCKED", itemId, null, "BLOCKED",
                            mapOf("league" to league, "sourceUrl" to sourceUrl, "reason" to "automatic creation safety gates are closed"),
                        )
                    }
                }
                LeagueImportAutomationMode.DISABLED -> {
                    repository.updateItemState(itemId, "BLOCKED", "series creation is disabled by league policy")
                    repository.enqueueOutbox(
                        "league-import:$itemId:v$version:BLOCKED", itemId, null, "BLOCKED",
                        mapOf("league" to league, "sourceUrl" to sourceUrl, "reason" to "series creation is disabled by league policy"),
                    )
                }
            }
        } else {
            repository.enqueueOutbox(
                "league-import:$itemId:v$version:BLOCKED", itemId, null, "BLOCKED",
                mapOf(
                    "league" to league,
                    "sourceUrl" to sourceUrl,
                    "reason" to ((checked as? LeagueAnnouncementParseResult.Blocked)?.reason ?: "announcement is not actionable"),
                ),
            )
        }
        val responseState = repository.findItemById(itemId)?.state ?: state
        return TelegramLeagueImportEventResponse(itemId, responseState, version, duplicate = false)
    }

    fun validateProductionPolicy(ready: LeagueAnnouncementParseResult.Ready): LeagueAnnouncementParseResult {
        val draft = ready.draft
        if (!draft.teamDeadline.isAfter(Instant.now())) return LeagueAnnouncementParseResult.Blocked("team deadline has passed")
        val tournament = tournamentRepository.findById(draft.tournamentId).orElse(null)
            ?: return LeagueAnnouncementParseResult.Blocked("configured tournament does not exist")
        if (tournament.status != TournamentStatus.ACTIVE) return LeagueAnnouncementParseResult.Blocked("configured tournament is not ACTIVE")
        if (tournament.kind != TournamentKind.STANDALONE) return LeagueAnnouncementParseResult.Blocked("only STANDALONE tournaments are supported")
        if (seriesRepository.existsByTournament_IdAndPublicNumber(draft.tournamentId, draft.seriesNumber)) {
            return LeagueAnnouncementParseResult.Blocked("series public number already exists in tournament")
        }
        return ready
    }

    fun offerAction(
        itemId: Long,
        version: Long,
        revision: Int,
        ready: LeagueAnnouncementParseResult.Ready,
        type: String,
        boundActorId: Long?,
        ttlSeconds: Long,
    ): UUID {
        val id = UUID.randomUUID()
        val callbackData = tokenCodec.encode(id)
        repository.createAction(
            id, itemId, version, revision, ready.checksum, properties.policyGeneration, type, tokenCodec.hash(callbackData),
            boundActorId, properties.operatorChatId, Instant.now().plusSeconds(ttlSeconds),
        )
        repository.enqueueOutbox(
            eventKey = "league-import:$itemId:v$version:$type:$id",
            itemId = itemId,
            actionId = id,
            eventType = type,
            payload = mapOf(
                "draft" to ready.draft,
                "tournamentName" to (tournamentRepository.findById(ready.draft.tournamentId).orElse(null)?.name
                    ?: "#${ready.draft.tournamentId}"),
            ),
        )
        return id
    }

    private fun leagueHint(text: String): String = when {
        Regex("(?i)#анонс_лп").containsMatchIn(text) -> "ЛП"
        Regex("(?i)#анонс_зл").containsMatchIn(text) -> "ЗЛ"
        else -> "-"
    }

    private fun ingestResult(
        itemId: Long,
        request: TelegramLeagueImportEventRequest,
        sourceUrl: String,
    ): TelegramLeagueImportEventResponse {
        val parsed = resultParser.parse(request.rawText, sourceUrl)
        if (parsed is LeagueResultParseResult.Blocked) {
            val version = repository.updateCurrentItem(
                itemId, request.revision, request.sourceVersion, request.contentHash,
                "RESULT", leagueHint(request.rawText), "PARTIAL_RESULT", null, null, parsed.reason,
            )
            repository.enqueueOutbox(
                "league-import:$itemId:v$version:RESULT_SHADOW", itemId, null, "RESULT_SHADOW",
                mapOf("league" to leagueHint(request.rawText), "sourceUrl" to sourceUrl, "reason" to parsed.reason),
            )
            return TelegramLeagueImportEventResponse(itemId, "PARTIAL_RESULT", version, duplicate = false)
        }
        parsed as LeagueResultParseResult.Ready
        val draft = parsed.draft
        val linked = repository.findAnnouncementLinkedSeriesIds(draft.tournamentId, draft.seriesNumber)
        if (linked.size != 1) {
            val reason = if (linked.isEmpty()) "no series with an ANNOUNCEMENT source link" else "multiple announcement-linked series match result"
            val version = repository.updateCurrentItem(
                itemId, request.revision, request.sourceVersion, request.contentHash,
                "RESULT", draft.league, "BLOCKED_MISMATCH", draft, parsed.checksum, reason,
            )
            repository.enqueueOutbox(
                "league-import:$itemId:v$version:RESULT_SHADOW", itemId, null, "RESULT_SHADOW",
                mapOf("league" to draft.league, "sourceUrl" to sourceUrl, "reason" to reason),
            )
            return TelegramLeagueImportEventResponse(itemId, "BLOCKED_MISMATCH", version, duplicate = false)
        }
        val seriesId = linked.single()
        val version = repository.updateCurrentItem(
            itemId, request.revision, request.sourceVersion, request.contentHash,
            "RESULT", draft.league, "WAITING_FOR_GAMES", draft, parsed.checksum, null, properties.policyGeneration,
        )
        repository.updateItemTargetSeries(itemId, seriesId)
        val linkedItem = repository.findItemById(itemId, lock = true)!!
        repository.insertSourceLink(linkedItem, seriesId, "RESULT")
        val mode = properties.policy(draft.league)?.finalizeMode ?: LeagueImportAutomationMode.DISABLED
        val autoSafe = mode != LeagueImportAutomationMode.AUTOMATIC ||
            automationEligible(request.postedAt, repository.currentRevision(itemId)!!.observedAt)
        if (properties.resultProcessingEnabled && properties.productionWritesEnabled &&
            mode != LeagueImportAutomationMode.DISABLED && autoSafe) {
            repository.enqueueJob(
                itemId, version, request.revision, parsed.checksum, properties.policyGeneration,
                "RECONCILE", seriesId,
            )
            repository.audit(itemId, null, null, "RESULT_LINKED", "RECONCILE_QUEUED", mapOf("seriesId" to seriesId))
        } else {
            repository.enqueueOutbox(
                "league-import:$itemId:v$version:RESULT_SHADOW", itemId, null, "RESULT_SHADOW",
                mapOf("league" to draft.league, "sourceUrl" to sourceUrl, "reason" to "result processing safety gates are closed"),
            )
        }
        return TelegramLeagueImportEventResponse(itemId, "WAITING_FOR_GAMES", version, duplicate = false)
    }

    private fun automationEligible(postedAt: Instant, observedAt: Instant): Boolean {
        val cutover = properties.automationCutoverAt ?: return false
        return properties.policyGeneration.isNotBlank() && properties.policyGeneration != "disabled" &&
            !postedAt.isBefore(cutover) && !observedAt.isBefore(cutover)
    }

    private fun cancelStaleJobsAfterCommit(itemId: Long, maxItemVersion: Long) {
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() {
                cancellationTransaction.executeWithoutResult {
                    repository.cancelJobsThroughItemVersion(itemId, maxItemVersion)
                }
            }
        })
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun deliveryFingerprint(request: TelegramLeagueImportEventRequest): String = sha256(
        listOf(
            request.sourceChannelPeerId,
            request.messageId,
            request.revision,
            request.sourceVersion,
            request.postedAt,
            request.editedAt,
            request.contentHash,
            request.rawText,
            request.classification,
            request.league,
        ).joinToString("\u0000"),
    )

    private companion object {
        val RESULT_TAG = Regex("(?<![\\p{L}\\p{N}_])#результаты_(лп|зл)(?![\\p{L}\\p{N}_])")
        const val AUTO_CREATE_HOLD_SECONDS = 120L
    }
}
