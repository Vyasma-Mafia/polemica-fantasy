package io.github.mralex1810.fantasy.schedule

import io.github.mralex1810.fantasy.config.TelegramLeagueImportProperties
import io.github.mralex1810.fantasy.config.LeagueImportAutomationMode
import io.github.mralex1810.fantasy.repository.LeagueImportRepository
import io.github.mralex1810.fantasy.service.leagueimport.LeagueImportCreateService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@Component
@ConditionalOnProperty(prefix = "spring.task.scheduling", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class LeagueImportActionScheduler(
    private val properties: TelegramLeagueImportProperties,
    private val repository: LeagueImportRepository,
    private val createService: LeagueImportCreateService,
    transactionManager: PlatformTransactionManager,
) {
    private val transaction = TransactionTemplate(transactionManager)
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "${'$'}{telegram.league-import.action-poll-ms:1000}")
    fun process() {
        transaction.executeWithoutResult {
            repository.findOpenActions().forEach { action ->
                val item = repository.findItemById(action.itemId)
                val policy = item?.let { properties.policy(it.leagueCode) }
                val modeSafe = when {
                    action.actionType.startsWith("CREATE_") -> policy?.createMode == LeagueImportAutomationMode.MANUAL
                    action.actionType.startsWith("FINALIZE_") -> policy?.finalizeMode == LeagueImportAutomationMode.MANUAL
                    else -> false
                }
                val generationSafe = action.policyGeneration == properties.policyGeneration &&
                    item?.policyGeneration == properties.policyGeneration
                val preview = action.actionType.endsWith("PREVIEW")
                val writeGatesSafe = preview || when {
                    action.actionType.startsWith("CREATE_") -> properties.productionWritesEnabled
                    action.actionType.startsWith("FINALIZE_") -> properties.productionWritesEnabled &&
                        properties.resultProcessingEnabled
                    else -> false
                }
                val reason = when {
                    !properties.enabled -> "league import disabled"
                    !properties.operatorNotificationsEnabled -> "operator notifications disabled"
                    !properties.callbackEnabled -> "league import callbacks disabled"
                    !modeSafe || !generationSafe -> "policy mode or generation changed"
                    !writeGatesSafe -> "production write gate disabled"
                    else -> null
                }
                if (reason != null && repository.cancelAction(action.id, reason)) {
                    repository.enqueueOutbox(
                        "league-import:${action.itemId}:action-cancelled:${action.id}", action.itemId, action.id,
                        "AUTO_CANCELLED",
                        mapOf(
                            "operation" to action.actionType,
                            "reason" to reason,
                            "league" to (item?.leagueCode ?: "-"),
                            "seriesNumber" to (item?.draftJson?.path("seriesNumber")?.asText("-") ?: "-"),
                            "seriesId" to item?.targetSeriesId,
                            "sourceUrl" to (item?.let { "https://t.me/polemica_closed_league/${it.sourceMessageId}" } ?: "-"),
                            // Every callback action is created only by the MANUAL flow. Keep the
                            // cancellation origin stable even if the current policy already changed.
                            "mode" to "MANUAL",
                            "policyGeneration" to action.policyGeneration,
                        ),
                    )
                }
            }
        }
        if (!properties.enabled || !properties.productionWritesEnabled) return
        transaction.executeWithoutResult { repository.requeueStaleCreatingActions() }
        repeat(5) {
            val actionId = transaction.execute { repository.claimNextCreateAction() } ?: return
            try {
                createService.create(actionId)
            } catch (e: Exception) {
                log.warn("Telegram league import create action {} failed: {}", actionId, e.message)
                createService.fail(actionId, e.message ?: e.javaClass.simpleName)
            }
        }
    }
}
