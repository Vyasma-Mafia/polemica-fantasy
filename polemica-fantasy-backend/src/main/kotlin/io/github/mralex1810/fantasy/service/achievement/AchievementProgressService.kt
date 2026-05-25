package io.github.mralex1810.fantasy.service.achievement

import io.github.mralex1810.fantasy.entity.AchievementDefinition
import io.github.mralex1810.fantasy.entity.UserAchievement
import io.github.mralex1810.fantasy.repository.AchievementDefinitionRepository
import io.github.mralex1810.fantasy.repository.TelegramUserRepository
import io.github.mralex1810.fantasy.repository.UserAchievementRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class AchievementProgressService(
    private val achievementDefinitionRepository: AchievementDefinitionRepository,
    private val userAchievementRepository: UserAchievementRepository,
    private val telegramUserRepository: TelegramUserRepository,
    private val calculator: AchievementProgressCalculator,
) {
    /**
     * Disabled deferred rows have tracking_started_at = NULL in Stage 1. There is no admin edit UI yet;
     * when such rows become editable, the first enabled transition must set tracking_started_at once.
     * Until then evaluators treat NULL as zero progress.
     */
    @Transactional
    fun recomputeEnabledForUser(internalTelegramUserId: Long) {
        val user = telegramUserRepository.findById(internalTelegramUserId).orElse(null) ?: return
        achievementDefinitionRepository.findAllEnabledWithRewards().forEach { definition ->
            val current = calculator.currentProgress(internalTelegramUserId, definition)
            val existing = userAchievementRepository.findByTelegramUser_IdAndAchievement_Id(
                internalTelegramUserId,
                definition.id!!,
            )
            val now = Instant.now()
            val row = existing ?: UserAchievement(telegramUser = user, achievement = definition)
            if (row.completedAt == null) {
                row.progressValue = current
                if (current >= definition.targetValue) {
                    row.completedAt = now
                    row.progressValue = current.coerceAtLeast(definition.targetValue)
                }
            } else {
                row.progressValue = row.progressValue.coerceAtLeast(definition.targetValue).coerceAtLeast(current)
            }
            row.updatedAt = now
            userAchievementRepository.save(row)
        }
    }

    fun currentProgress(internalTelegramUserId: Long, definition: AchievementDefinition): Long =
        calculator.currentProgress(internalTelegramUserId, definition)
}
