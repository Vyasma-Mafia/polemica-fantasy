package io.github.mralex1810.fantasy.service.achievement

import io.github.mralex1810.fantasy.event.AchievementProgressEvent
import io.github.mralex1810.fantasy.event.AchievementProgressEventType
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class AchievementProgressEventListener(
    private val achievementProgressService: AchievementProgressService,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onAchievementProgress(event: AchievementProgressEvent) {
        if (event.type == AchievementProgressEventType.PERIODIC_RATING_FINALIZED) return
        event.internalTelegramUserIds.forEach { internalUserId ->
            achievementProgressService.recomputeEnabledForUser(internalUserId)
        }
    }
}

@Component
class PeriodicRatingAchievementProgressEventListener(
    private val achievementProgressService: AchievementProgressService,
) {
    companion object {
        private val CONDITIONS = setOf(
            "PERIODIC_RATING_PERIODS",
            "PERIODIC_RATING_TOP10",
            "PERIODIC_RATING_PODIUMS",
            "PERIODIC_RATING_WINS",
        )
    }

    private val log = LoggerFactory.getLogger(javaClass)

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onPeriodicRatingFinalized(event: AchievementProgressEvent) {
        if (event.type != AchievementProgressEventType.PERIODIC_RATING_FINALIZED) return
        event.internalTelegramUserIds.forEach { internalUserId ->
            runCatching {
                achievementProgressService.recomputeEnabledForUser(internalUserId, CONDITIONS)
            }.onFailure { error ->
                log.error("Failed to recompute periodic rating achievements for user {}", internalUserId, error)
            }
        }
    }
}
