package io.github.mralex1810.fantasy.service.achievement

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.mralex1810.fantasy.dto.user.response.AchievementClaimResultDto
import io.github.mralex1810.fantasy.dto.user.response.AchievementCosmeticUnlockDto
import io.github.mralex1810.fantasy.entity.FantikiTransactionReason
import io.github.mralex1810.fantasy.entity.TelegramUser
import io.github.mralex1810.fantasy.repository.AchievementDefinitionRepository
import io.github.mralex1810.fantasy.repository.UserAchievementRepository
import io.github.mralex1810.fantasy.service.UserService
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

@Service
class AchievementClaimService(
    private val achievementDefinitionRepository: AchievementDefinitionRepository,
    private val userAchievementRepository: UserAchievementRepository,
    private val achievementProgressService: AchievementProgressService,
    private val userService: UserService,
    private val jdbcTemplate: JdbcTemplate,
    private val objectMapper: ObjectMapper,
) {
    @Transactional
    fun claim(user: TelegramUser, code: String): AchievementClaimResultDto {
        // `user.id` is the internal telegram_user.id. `user.telegramId` is the Telegram platform id.
        val internalUserId = user.id!!
        val definition = achievementDefinitionRepository.findByCodeWithRewards(code)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Achievement $code not found")
        if (!definition.enabled) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Achievement $code not found")
        }
        val achievementId = definition.id!!
        userAchievementRepository.ensureRow(internalUserId, achievementId)
        val row = userAchievementRepository.findForUpdate(internalUserId, achievementId)
            ?: throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Achievement progress row not found")
        val now = Instant.now()
        val current = achievementProgressService.currentProgress(internalUserId, definition)
        if (row.completedAt == null) {
            row.progressValue = current
            if (current >= definition.targetValue) {
                row.completedAt = now
            }
        } else {
            row.progressValue = row.progressValue.coerceAtLeast(definition.targetValue).coerceAtLeast(current)
        }
        if (row.completedAt == null) {
            row.updatedAt = now
            userAchievementRepository.save(row)
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Achievement is not completed")
        }
        val previousClaimedAt = row.claimedAt
        if (previousClaimedAt != null) {
            return AchievementClaimResultDto(
                achievementCode = definition.code,
                claimedAt = previousClaimedAt,
                fantikiDelta = 0L,
                newFantikiBalance = userService.getBalance(internalUserId),
                cosmeticUnlocks = emptyList(),
            )
        }

        val rewards = definition.rewards.sortedWith(compareBy({ it.displayOrder }, { it.id ?: 0L }))
        val fantikiDelta = rewards.filter { it.rewardType == "FANTIKI" }.sumOf { it.amount ?: 0L }
        val cosmeticUnlocks = rewards
            .filter { it.rewardType in COSMETIC_REWARD_TYPES && !it.rewardCode.isNullOrBlank() }
            .map { AchievementCosmeticUnlockDto(type = it.rewardType, code = it.rewardCode!!) }
        if (fantikiDelta > 0L) {
            userService.addBalance(internalUserId, fantikiDelta, FantikiTransactionReason.ACHIEVEMENT_REWARD)
        }
        cosmeticUnlocks.forEach { unlock ->
            jdbcTemplate.update(
                """
                INSERT INTO user_cosmetic_unlock (
                    telegram_user_id, cosmetic_type, cosmetic_code, source_type, source_code, unlocked_at
                )
                VALUES (?, ?, ?, 'ACHIEVEMENT', ?, ?)
                ON CONFLICT (telegram_user_id, cosmetic_type, cosmetic_code) DO NOTHING
                """,
                internalUserId,
                unlock.type,
                unlock.code,
                definition.code,
                java.sql.Timestamp.from(now),
            )
        }
        row.claimedAt = now
        row.rewardSnapshot = objectMapper.writeValueAsString(rewards.map {
            mapOf(
                "type" to it.rewardType,
                "amount" to it.amount,
                "code" to it.rewardCode,
            )
        })
        row.updatedAt = now
        userAchievementRepository.save(row)
        return AchievementClaimResultDto(
            achievementCode = definition.code,
            claimedAt = now,
            fantikiDelta = fantikiDelta,
            newFantikiBalance = userService.getBalance(internalUserId),
            cosmeticUnlocks = cosmeticUnlocks,
        )
    }

    private companion object {
        val COSMETIC_REWARD_TYPES = setOf("PROFILE_FRAME", "CARD_SKIN_UNLOCK", "COSMETIC_UNLOCK", "BADGE_STYLE")
    }
}
