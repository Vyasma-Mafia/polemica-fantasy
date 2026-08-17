package io.github.mralex1810.fantasy.service.leagueimport

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.mralex1810.fantasy.config.LeagueImportAutomationMode
import io.github.mralex1810.fantasy.config.TelegramLeagueImportProperties
import io.github.mralex1810.fantasy.repository.LeagueImportRepository
import io.github.mralex1810.fantasy.service.SeriesCompletionService
import io.github.mralex1810.fantasy.service.SeriesFinalizationService
import io.github.mralex1810.fantasy.service.SeriesService
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Service
class LeagueImportResultProcessingService(
    private val properties: TelegramLeagueImportProperties,
    private val repository: LeagueImportRepository,
    private val objectMapper: ObjectMapper,
    private val seriesService: SeriesService,
    private val completionService: SeriesCompletionService,
    private val finalizationService: SeriesFinalizationService,
    private val tokenCodec: LeagueImportActionTokenCodec,
    transactionManager: PlatformTransactionManager,
) {
    private val transaction = TransactionTemplate(transactionManager)

    /** External Polemica/public-points calls deliberately happen before the short state transaction. */
    fun reconcile(jobId: UUID, leaseToken: UUID) {
        requireWriteGates()
        val job = repository.findJob(jobId) ?: error("Import job not found")
        check(job.operation == "RECONCILE" && job.status == "RUNNING") { "Reconcile job is not claimable" }
        check(job.leaseToken == leaseToken) { "Import job lease was lost" }
        check(job.policyGeneration == properties.policyGeneration) { "Import policy generation changed" }
        val item = repository.findItemById(job.itemId) ?: error("Import item not found")
        checkCurrent(item.id, item.version, item.currentRevision, item.draftChecksum, job)
        val draft = resultDraft(item)
        val policy = properties.policy(draft.league) ?: error("League policy is disabled")
        check(policy.finalizeMode != LeagueImportAutomationMode.DISABLED) { "Result finalization is disabled by policy" }
        val seriesId = item.targetSeriesId ?: error("Result item is not linked to a series")

        seriesService.moveToScoringForLeagueImport(seriesId)
        seriesService.syncGames(seriesId)
        seriesService.calculateScores(seriesId)
        val completion = completionService.evaluate(seriesId)
        val now = Instant.now()

        transaction.executeWithoutResult {
            val lockedJob = repository.findJob(jobId, lock = true) ?: error("Import job disappeared")
            check(lockedJob.leaseToken == leaseToken) { "Import job lease was lost" }
            val lockedItem = repository.findItemById(item.id, lock = true) ?: error("Import item disappeared")
            checkCurrent(lockedItem.id, lockedItem.version, lockedItem.currentRevision, lockedItem.draftChecksum, lockedJob)
            if (!completion.ready) {
                repository.updateItemReadiness(
                    lockedItem.id, "WAITING_FOR_GAMES", "NOT_READY", null, 0, null, now, completion.reason,
                )
                check(repository.rescheduleJob(jobId, now.plusSeconds(RECONCILE_INTERVAL_SECONDS), completion.reason,
                    expectedLeaseToken = leaseToken)) { "Import job lease was lost" }
                return@executeWithoutResult
            }

            val same = lockedItem.readinessChecksum == completion.checksum
            val elapsed = lockedItem.lastStableObservationAt?.let { Duration.between(it, now).seconds } ?: Long.MAX_VALUE
            val stableCount = when {
                !same -> 1
                elapsed >= STABLE_POLL_INTERVAL_SECONDS -> lockedItem.stablePollCount + 1
                else -> lockedItem.stablePollCount
            }
            val readySince = if (same) lockedItem.readySince ?: now else now
            repository.updateItemReadiness(
                lockedItem.id, "READY_TO_FINALIZE", "READY", completion.checksum,
                stableCount, readySince, now,
            )
            when (policy.finalizeMode) {
                LeagueImportAutomationMode.MANUAL -> {
                    offerFinalizeAction(
                        lockedItem.id, lockedItem.version, lockedItem.currentRevision, completion.checksum!!, draft,
                        "FINALIZE_PREVIEW", null, properties.previewTtlSeconds,
                    )
                    check(repository.finishJob(jobId, "APPLIED", readinessChecksum = completion.checksum,
                        expectedLeaseToken = leaseToken)) { "Import job lease was lost" }
                }
                LeagueImportAutomationMode.AUTOMATIC -> {
                    val revision = repository.currentRevision(lockedItem.id) ?: error("Current result revision is missing")
                    val graceElapsed = !now.isBefore(revision.observedAt.plusSeconds(AUTO_FINALIZE_GRACE_SECONDS))
                    if (stableCount >= REQUIRED_STABLE_POLLS && graceElapsed) {
                        val notificationOutboxId = repository.enqueueOutbox(
                            "league-import:${lockedItem.id}:v${lockedItem.version}:AUTO_FINALIZE_PENDING:${properties.policyGeneration}",
                            lockedItem.id, null, "AUTO_FINALIZE_PENDING",
                            mapOf(
                                "league" to draft.league,
                                "seriesNumber" to draft.seriesNumber,
                                "seriesId" to seriesId,
                                "sourceUrl" to draft.sourceUrl,
                                "holdSeconds" to AUTO_FINALIZE_CANCEL_HOLD_SECONDS,
                                "mode" to policy.finalizeMode.name,
                                "policyGeneration" to properties.policyGeneration,
                            ),
                        )
                        repository.enqueueJob(
                            lockedItem.id, lockedItem.version, lockedItem.currentRevision, lockedItem.draftChecksum!!,
                            properties.policyGeneration, "FINALIZE", seriesId,
                            readinessChecksum = completion.checksum,
                            availableAt = null,
                            pendingNotificationOutboxId = notificationOutboxId,
                        )
                        repository.updateItemState(lockedItem.id, "FINALIZE_PENDING")
                        check(repository.finishJob(jobId, "APPLIED", readinessChecksum = completion.checksum,
                            expectedLeaseToken = leaseToken)) { "Import job lease was lost" }
                    } else {
                        check(repository.rescheduleJob(
                            jobId, now.plusSeconds(RECONCILE_INTERVAL_SECONDS),
                            "waiting for stable polls/grace", completion.checksum, leaseToken,
                        )) { "Import job lease was lost" }
                    }
                }
                LeagueImportAutomationMode.DISABLED -> error("Result finalization is disabled by policy")
            }
        }
    }

    @Transactional
    fun finalize(jobId: UUID, leaseToken: UUID) {
        requireWriteGates()
        val job = repository.findJob(jobId, lock = true) ?: error("Import job not found")
        check(job.operation == "FINALIZE" && job.status == "RUNNING") { "Finalize job is not claimable" }
        check(job.leaseToken == leaseToken) { "Import job lease was lost" }
        check(job.policyGeneration == properties.policyGeneration) { "Import policy generation changed" }
        // Do not lock evidence before the series. SeriesFinalizationService owns the global
        // series -> RESULT evidence lock order used by both admin and import finalization.
        val item = repository.findItemById(job.itemId) ?: error("Import item not found")
        checkCurrent(item.id, item.version, item.currentRevision, item.draftChecksum, job)
        val draft = resultDraft(item)
        val policy = properties.policy(draft.league) ?: error("League policy is disabled")
        val expectedMode = if (job.actorTelegramId == null) LeagueImportAutomationMode.AUTOMATIC else LeagueImportAutomationMode.MANUAL
        check(policy.finalizeMode == expectedMode) { "Finalize policy mode changed" }
        if (expectedMode == LeagueImportAutomationMode.AUTOMATIC) {
            val cutover = properties.automationCutoverAt ?: error("Automation cutover is not configured")
            val revision = repository.currentRevision(item.id) ?: error("Current result revision is missing")
            check(!revision.postedAt.isBefore(cutover) && !revision.observedAt.isBefore(cutover)) {
                "Result source predates automation cutover"
            }
            check(item.readinessStatus == "READY" && item.readinessChecksum == job.readinessChecksum) {
                "Automatic finalization readiness is stale"
            }
            check(item.stablePollCount >= REQUIRED_STABLE_POLLS) { "Automatic finalization needs fresh stable polls" }
            check(!Instant.now().isBefore(revision.observedAt.plusSeconds(AUTO_FINALIZE_GRACE_SECONDS))) {
                "Automatic finalization grace has not elapsed"
            }
            val lastStable = item.lastStableObservationAt ?: error("Automatic finalization has no stable observation")
            check(Duration.between(lastStable, Instant.now()).seconds in 0..MAX_STABLE_OBSERVATION_AGE_SECONDS) {
                "Automatic finalization stable observation is stale"
            }
        }
        val seriesId = item.targetSeriesId ?: error("Result item is not linked to a series")
        val readinessChecksum = job.readinessChecksum ?: error("Finalize job has no readiness fingerprint")

        val finalization = finalizationService.finalizeSeries(seriesId, readinessChecksum)
        val lockedItem = repository.findItemById(item.id, lock = true) ?: error("Import item disappeared")
        checkCurrent(lockedItem.id, lockedItem.version, lockedItem.currentRevision, lockedItem.draftChecksum, job)
        repository.updateItemState(item.id, "FINALIZED")
        check(repository.finishJob(job.id, "APPLIED", readinessChecksum = readinessChecksum,
            expectedLeaseToken = leaseToken)) { "Import job lease was lost" }
        repository.audit(item.id, null, job.actorTelegramId, "SERIES_FINALIZED", "COMMITTED", mapOf("seriesId" to seriesId))
        repository.enqueueOutbox(
            "league-import:${item.id}:series-finalized:$seriesId", item.id, null, "FINALIZED",
            mapOf(
                "seriesId" to seriesId,
                "league" to draft.league,
                "seriesNumber" to draft.seriesNumber,
                "sourceUrl" to draft.sourceUrl,
                "rewardsDistributed" to finalization.rewardsDistributed,
                "cardsDecremented" to finalization.cardsDecremented,
                "operatorMessageId" to (job.operatorMessageId ?: repository.findLatestDeliveredMessageId(item.id)),
            ),
        )
    }

    @Transactional
    fun fail(jobId: UUID, leaseToken: UUID, reason: String) {
        val job = repository.findJob(jobId, lock = true) ?: return
        if (job.leaseToken != leaseToken) return
        if (job.status in setOf("APPLIED", "CANCELLED")) return
        val item = repository.findItemById(job.itemId, lock = true)
        if (reason.isNonRetryablePolicyFailure()) {
            repository.finishJob(jobId, "CANCELLED", reason, expectedLeaseToken = leaseToken)
            item?.let {
                repository.enqueueOutbox(
                    "league-import:${it.id}:auto-cancelled:$jobId", it.id, null, "AUTO_CANCELLED",
                    mapOf("operation" to job.operation, "reason" to reason.take(256), "policyGeneration" to job.policyGeneration),
                )
            }
            return
        }
        val terminal = job.attempts >= MAX_ATTEMPTS
        if (terminal) {
            repository.finishJob(jobId, "FAILED", reason, expectedLeaseToken = leaseToken)
            item?.let {
                repository.updateItemState(it.id, "FAILED", reason)
                repository.enqueueOutbox(
                    "league-import:${it.id}:${job.operation.lowercase()}-failed:$jobId", it.id, null, "FINALIZE_FAILED",
                    mapOf(
                        "reason" to reason.take(256),
                        "sourceUrl" to "https://t.me/polemica_closed_league/${it.sourceMessageId}",
                        "operatorMessageId" to job.operatorMessageId,
                    ),
                )
            }
        } else {
            repository.rescheduleJob(jobId, Instant.now().plusSeconds(RETRY_SECONDS), reason,
                expectedLeaseToken = leaseToken)
        }
    }

    fun offerFinalizeAction(
        itemId: Long,
        version: Long,
        revision: Int,
        checksum: String,
        draft: LeagueResultDraft,
        actionType: String,
        boundActorId: Long?,
        ttlSeconds: Long,
        operatorMessageId: Long? = null,
    ) {
        val id = UUID.randomUUID()
        val callbackData = tokenCodec.encode(id)
        val targetMessageId = operatorMessageId ?: repository.findLatestDeliveredMessageId(itemId)
        repository.createAction(
            id, itemId, version, revision, checksum, properties.policyGeneration, actionType, tokenCodec.hash(callbackData),
            boundActorId, properties.operatorChatId, Instant.now().plusSeconds(ttlSeconds),
            operatorMessageId = targetMessageId,
        )
        repository.enqueueOutbox(
            "league-import:$itemId:v$version:$actionType:$id", itemId, id, actionType,
            mapOf("draft" to draft, "readinessChecksum" to checksum),
        )
    }

    private fun resultDraft(item: io.github.mralex1810.fantasy.repository.LeagueImportItemRow): LeagueResultDraft =
        item.draftJson?.let { objectMapper.treeToValue(it, LeagueResultDraft::class.java) }
            ?: error("Result draft is missing")

    private fun checkCurrent(itemId: Long, version: Long, revision: Int, checksum: String?, job: io.github.mralex1810.fantasy.repository.LeagueImportJobRow) {
        check(itemId == job.itemId && version == job.itemVersion && revision == job.sourceRevision && checksum == job.evidenceChecksum) {
            "Import source changed while job was running"
        }
    }

    private fun requireWriteGates() {
        check(properties.enabled && properties.resultProcessingEnabled && properties.productionWritesEnabled) {
            "Telegram league import result writes are disabled"
        }
    }

    private fun String.isNonRetryablePolicyFailure(): Boolean =
        contains("disabled", ignoreCase = true) || contains("mode changed", ignoreCase = true) ||
            contains("generation changed", ignoreCase = true) || contains("predates automation cutover", ignoreCase = true) ||
            contains("already finalized", ignoreCase = true)

    private companion object {
        const val RECONCILE_INTERVAL_SECONDS = 120L
        const val STABLE_POLL_INTERVAL_SECONDS = 120L
        const val AUTO_FINALIZE_GRACE_SECONDS = 15L * 60L
        const val AUTO_FINALIZE_CANCEL_HOLD_SECONDS = 120L
        const val REQUIRED_STABLE_POLLS = 3
        const val MAX_STABLE_OBSERVATION_AGE_SECONDS = 5L * 60L
        const val RETRY_SECONDS = 30L
        const val MAX_ATTEMPTS = 10
    }
}
