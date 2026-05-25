package io.github.mralex1810.fantasy.service.achievement

import io.github.mralex1810.fantasy.event.AchievementProgressEvent
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
        event.internalTelegramUserIds.forEach { achievementProgressService.recomputeEnabledForUser(it) }
    }
}
