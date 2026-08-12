package io.github.mralex1810.fantasy.schedule

import io.github.mralex1810.fantasy.config.TelegramLeagueImportProperties
import io.github.mralex1810.fantasy.repository.LeagueImportRepository
import io.github.mralex1810.fantasy.service.leagueimport.LeagueImportCreateService
import io.github.mralex1810.fantasy.service.leagueimport.LeagueImportResultProcessingService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@Component
@ConditionalOnProperty(prefix = "spring.task.scheduling", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class LeagueImportJobScheduler(
    private val properties: TelegramLeagueImportProperties,
    private val repository: LeagueImportRepository,
    private val createService: LeagueImportCreateService,
    private val resultService: LeagueImportResultProcessingService,
    transactionManager: PlatformTransactionManager,
) {
    private val transaction = TransactionTemplate(transactionManager)
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "${'$'}{telegram.league-import.job-poll-ms:1000}")
    fun process() {
        sweepUnsafeJobs()
        if (!properties.enabled || !properties.productionWritesEnabled) return
        repeat(5) {
            val job = transaction.execute { repository.leaseJob() } ?: return
            val leaseToken = job.leaseToken ?: error("Leased import job has no fencing token")
            try {
                when (job.operation) {
                    "CREATE" -> createService.createFromJob(job.id, leaseToken)
                    "RECONCILE" -> resultService.reconcile(job.id, leaseToken)
                    "FINALIZE" -> resultService.finalize(job.id, leaseToken)
                    else -> error("Unsupported league import job operation ${job.operation}")
                }
            } catch (e: Exception) {
                log.warn("Telegram league import job {} ({}) failed: {}", job.id, job.operation, e.message)
                if (job.operation == "CREATE") createService.failJob(job.id, leaseToken, e.message ?: e.javaClass.simpleName)
                else resultService.fail(job.id, leaseToken, e.message ?: e.javaClass.simpleName)
            }
        }
    }

    private fun sweepUnsafeJobs() {
        transaction.executeWithoutResult {
            repository.findOpenJobs().forEach { job ->
                val item = repository.findItemById(job.itemId)
                val policy = item?.let { properties.policy(it.leagueCode) }
                val expectedMode = when (job.operation) {
                    "CREATE" -> policy?.createMode
                    "RECONCILE" -> policy?.finalizeMode
                    "FINALIZE" -> policy?.finalizeMode
                    else -> null
                }
                val reason = when {
                    !properties.enabled -> "league import disabled"
                    !properties.productionWritesEnabled -> "production writes disabled"
                    !properties.operatorNotificationsEnabled -> "operator notifications disabled"
                    job.actorTelegramId != null && !properties.callbackEnabled -> "league import callbacks disabled"
                    item == null -> "import item missing"
                    job.policyGeneration != properties.policyGeneration || item.policyGeneration != properties.policyGeneration ->
                        "policy generation changed"
                    job.operation == "CREATE" && expectedMode != io.github.mralex1810.fantasy.config.LeagueImportAutomationMode.AUTOMATIC ->
                        "automatic create mode changed"
                    job.operation in setOf("RECONCILE", "FINALIZE") && !properties.resultProcessingEnabled ->
                        "result processing disabled"
                    job.operation == "RECONCILE" && (expectedMode == null ||
                        expectedMode == io.github.mralex1810.fantasy.config.LeagueImportAutomationMode.DISABLED) ->
                        "result policy disabled"
                    job.operation == "FINALIZE" && job.actorTelegramId == null &&
                        expectedMode != io.github.mralex1810.fantasy.config.LeagueImportAutomationMode.AUTOMATIC ->
                        "automatic finalize mode changed"
                    job.operation == "FINALIZE" && job.actorTelegramId != null &&
                        expectedMode != io.github.mralex1810.fantasy.config.LeagueImportAutomationMode.MANUAL ->
                        "manual finalize mode changed"
                    else -> null
                }
                if (reason != null && repository.cancelOpenJob(job.id, reason)) {
                    repository.enqueueOutbox(
                        "league-import:${job.itemId}:auto-cancelled:${job.id}", job.itemId, null, "AUTO_CANCELLED",
                        cancellationPayload(item, job.operation, expectedMode?.name, job.policyGeneration, reason),
                    )
                }
            }
        }
    }

    private fun cancellationPayload(
        item: io.github.mralex1810.fantasy.repository.LeagueImportItemRow?,
        operation: String,
        mode: String?,
        generation: String,
        reason: String,
    ) = mapOf(
        "operation" to operation,
        "reason" to reason,
        "league" to (item?.leagueCode ?: "-"),
        "seriesNumber" to (item?.draftJson?.path("seriesNumber")?.asText("-") ?: "-"),
        "seriesId" to item?.targetSeriesId,
        "sourceUrl" to (item?.let { "https://t.me/polemica_closed_league/${it.sourceMessageId}" } ?: "-"),
        "mode" to (mode ?: "-"),
        "policyGeneration" to generation,
    )
}
