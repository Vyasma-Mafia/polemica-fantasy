package io.github.mralex1810.fantasy.service.achievement

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.core.type.TypeReference
import io.github.mralex1810.fantasy.dto.user.response.AchievementCardChoiceOptionDto
import io.github.mralex1810.fantasy.dto.user.response.AchievementClaimResultDto
import io.github.mralex1810.fantasy.dto.user.response.AchievementCosmeticUnlockDto
import io.github.mralex1810.fantasy.dto.user.response.AchievementGrantedCardDto
import io.github.mralex1810.fantasy.dto.user.response.AchievementPendingCardChoiceDto
import io.github.mralex1810.fantasy.entity.AchievementDefinition
import io.github.mralex1810.fantasy.entity.AchievementReward
import io.github.mralex1810.fantasy.entity.FantikiTransactionReason
import io.github.mralex1810.fantasy.entity.TelegramUser
import io.github.mralex1810.fantasy.entity.UserAchievement
import io.github.mralex1810.fantasy.event.AchievementProgressEvent
import io.github.mralex1810.fantasy.event.AchievementProgressEventType
import io.github.mralex1810.fantasy.repository.AchievementDefinitionRepository
import io.github.mralex1810.fantasy.repository.UserAchievementRepository
import io.github.mralex1810.fantasy.service.UserService
import org.springframework.context.ApplicationEventPublisher
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
    private val achievementCardRewardService: AchievementCardRewardService,
    private val userService: UserService,
    private val jdbcTemplate: JdbcTemplate,
    private val objectMapper: ObjectMapper,
    private val applicationEventPublisher: ApplicationEventPublisher,
) {
    @Transactional
    fun claim(user: TelegramUser, code: String): AchievementClaimResultDto {
        // `user.id` is the internal telegram_user.id. `user.telegramId` is the Telegram platform id.
        val (definition, row, now) = loadCompletedForUpdate(user, code)
        val internalUserId = user.id!!
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
        val pendingChoices = ensureChoiceRows(internalUserId, definition, rewards)
        if (pendingChoices.isNotEmpty()) {
            row.updatedAt = now
            userAchievementRepository.save(row)
            return AchievementClaimResultDto(
                achievementCode = definition.code,
                claimedAt = null,
                fantikiDelta = 0L,
                newFantikiBalance = userService.getBalance(internalUserId),
                cosmeticUnlocks = emptyList(),
                pendingChoices = pendingChoices,
            )
        }
        return finalizeClaim(user, definition, row, rewards, now, preGrantedCards = emptyList())
    }

    @Transactional
    fun selectChoice(user: TelegramUser, code: String, rewardId: Long, optionIds: List<String>): AchievementClaimResultDto {
        val (definition, row, now) = loadCompletedForUpdate(user, code)
        val internalUserId = user.id!!
        if (row.claimedAt != null) {
            return AchievementClaimResultDto(
                achievementCode = definition.code,
                claimedAt = row.claimedAt,
                fantikiDelta = 0L,
                newFantikiBalance = userService.getBalance(internalUserId),
                cosmeticUnlocks = emptyList(),
            )
        }
        val rewards = definition.rewards.sortedWith(compareBy({ it.displayOrder }, { it.id ?: 0L }))
        val reward = rewards.firstOrNull { it.id == rewardId && it.rewardType == "CARD_CHOICE_ROLL" }
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Choice reward $rewardId not found")
        ensureChoiceRows(internalUserId, definition, rewards)
        val choice = loadChoiceRow(internalUserId, definition.id!!, reward.id!!)
            ?: throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Choice reward row not found")
        if (choice.claimedAt != null) {
            val remaining = pendingChoiceDtos(internalUserId, definition, rewards)
            return AchievementClaimResultDto(
                achievementCode = definition.code,
                claimedAt = null,
                fantikiDelta = 0L,
                newFantikiBalance = userService.getBalance(internalUserId),
                cosmeticUnlocks = emptyList(),
                pendingChoices = remaining,
            )
        }
        val selectedIds = optionIds.map { it.trim() }.filter { it.isNotEmpty() }
        if (selectedIds.distinct().size != selectedIds.size || selectedIds.size != choice.requiredCount) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Select exactly ${choice.requiredCount} unique options")
        }
        val selectedOptions = selectedIds.map { optionId ->
            choice.options.firstOrNull { it.optionId == optionId }
                ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown choice option: $optionId")
        }
        val grantedCards = achievementCardRewardService.issueCardsFromOptions(user, selectedOptions, now)
        jdbcTemplate.update(
            """
            UPDATE user_achievement_card_choice
            SET selected_option_ids = ?::jsonb,
                selected_user_card_ids = ?::jsonb,
                claimed_at = ?
            WHERE telegram_user_id = ?
              AND achievement_id = ?
              AND reward_id = ?
            """,
            objectMapper.writeValueAsString(selectedIds),
            objectMapper.writeValueAsString(grantedCards.map { it.userCardId }),
            java.sql.Timestamp.from(now),
            internalUserId,
            definition.id!!,
            reward.id!!,
        )
        val remaining = pendingChoiceDtos(internalUserId, definition, rewards)
        if (remaining.isNotEmpty()) {
            return AchievementClaimResultDto(
                achievementCode = definition.code,
                claimedAt = null,
                fantikiDelta = 0L,
                newFantikiBalance = userService.getBalance(internalUserId),
                cosmeticUnlocks = emptyList(),
                grantedCards = grantedCards,
                pendingChoices = remaining,
            )
        }
        return finalizeClaim(user, definition, row, rewards, now, preGrantedCards = grantedCards)
    }

    private fun loadCompletedForUpdate(user: TelegramUser, code: String): ClaimContext {
        val internalUserId = user.id!!
        val definition = achievementDefinitionRepository.findByCodeWithRewards(code)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Achievement $code not found")
        if (!definition.enabled || definition.visibility != "PUBLIC") {
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
        return ClaimContext(definition, row, now)
    }

    private fun finalizeClaim(
        user: TelegramUser,
        definition: AchievementDefinition,
        row: UserAchievement,
        rewards: List<AchievementReward>,
        now: Instant,
        preGrantedCards: List<AchievementGrantedCardDto>,
    ): AchievementClaimResultDto {
        val internalUserId = user.id!!
        val fantikiDelta = rewards.filter { it.rewardType == "FANTIKI" }.sumOf { it.amount ?: 0L }
        val cosmeticUnlocks = rewards
            .filter { it.rewardType in COSMETIC_REWARD_TYPES && !it.rewardCode.isNullOrBlank() }
            .map { AchievementCosmeticUnlockDto(type = it.rewardType, code = it.rewardCode!!) }
        val randomCards = rewards
            .filter { it.rewardType == "RANDOM_CARD" }
            .associate { reward ->
                val rewardId = reward.id
                    ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "reward id is required")
                rewardId to achievementCardRewardService.issueRandomCards(user, reward, now)
            }
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
        val grantedCards = preGrantedCards + randomCards.values.flatten()
        row.claimedAt = now
        row.rewardSnapshot = objectMapper.writeValueAsString(
            buildRewardSnapshot(
                internalUserId = internalUserId,
                definition = definition,
                rewards = rewards,
                randomCardsByRewardId = randomCards,
            ),
        )
        row.updatedAt = now
        userAchievementRepository.save(row)
        if (grantedCards.isNotEmpty()) {
            applicationEventPublisher.publishEvent(
                AchievementProgressEvent(AchievementProgressEventType.COLLECTION_CHANGED, setOf(internalUserId)),
            )
        }
        return AchievementClaimResultDto(
            achievementCode = definition.code,
            claimedAt = now,
            fantikiDelta = fantikiDelta,
            newFantikiBalance = userService.getBalance(internalUserId),
            cosmeticUnlocks = cosmeticUnlocks,
            grantedCards = grantedCards,
        )
    }

    private fun ensureChoiceRows(
        internalUserId: Long,
        definition: AchievementDefinition,
        rewards: List<AchievementReward>,
    ): List<AchievementPendingCardChoiceDto> {
        val achievementId = definition.id!!
        rewards.filter { it.rewardType == "CARD_CHOICE_ROLL" }.forEach { reward ->
            val rewardId = reward.id ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "reward id is required")
            if (loadChoiceRow(internalUserId, achievementId, rewardId) == null) {
                val spec = achievementCardRewardService.parseSpec(reward)
                val options = achievementCardRewardService.generateOptions(reward)
                jdbcTemplate.update(
                    """
                    INSERT INTO user_achievement_card_choice (
                        telegram_user_id, achievement_id, reward_id, required_count, options
                    )
                    VALUES (?, ?, ?, ?, ?::jsonb)
                    """,
                    internalUserId,
                    achievementId,
                    rewardId,
                    spec.count,
                    objectMapper.writeValueAsString(options),
                )
            }
        }
        return pendingChoiceDtos(internalUserId, definition, rewards)
    }

    private fun pendingChoiceDtos(
        internalUserId: Long,
        definition: AchievementDefinition,
        rewards: List<AchievementReward>,
    ): List<AchievementPendingCardChoiceDto> =
        rewards.filter { it.rewardType == "CARD_CHOICE_ROLL" }.mapNotNull { reward ->
            val rewardId = reward.id ?: return@mapNotNull null
            val row = loadChoiceRow(internalUserId, definition.id!!, rewardId) ?: return@mapNotNull null
            if (row.claimedAt != null) {
                null
            } else {
                AchievementPendingCardChoiceDto(
                    rewardId = rewardId,
                    requiredCount = row.requiredCount,
                    options = row.options.map { achievementCardRewardService.optionDto(it) },
                )
            }
        }

    private fun loadChoiceRow(
        internalUserId: Long,
        achievementId: Long,
        rewardId: Long,
    ): ChoiceRow? =
        jdbcTemplate.query(
            """
            SELECT required_count, options::text, claimed_at
                 , selected_option_ids::text, selected_user_card_ids::text
            FROM user_achievement_card_choice
            WHERE telegram_user_id = ?
              AND achievement_id = ?
              AND reward_id = ?
            """,
            { rs, _ ->
                ChoiceRow(
                    requiredCount = rs.getInt("required_count"),
                    options = objectMapper.readValue(
                        rs.getString("options"),
                        object : TypeReference<List<AchievementCardRewardOptionInternal>>() {},
                    ),
                    claimedAt = rs.getTimestamp("claimed_at")?.toInstant(),
                    selectedOptionIds = rs.getString("selected_option_ids")?.let { json ->
                        objectMapper.readValue(json, object : TypeReference<List<String>>() {})
                    }.orEmpty(),
                    selectedUserCardIds = rs.getString("selected_user_card_ids")?.let { json ->
                        objectMapper.readValue(json, object : TypeReference<List<Long>>() {})
                    }.orEmpty(),
                )
            },
            internalUserId,
            achievementId,
            rewardId,
        ).firstOrNull()

    private fun buildRewardSnapshot(
        internalUserId: Long,
        definition: AchievementDefinition,
        rewards: List<AchievementReward>,
        randomCardsByRewardId: Map<Long, List<AchievementGrantedCardDto>>,
    ): List<Map<String, Any?>> {
        val choiceRows = rewards
            .filter { it.rewardType == "CARD_CHOICE_ROLL" && it.id != null }
            .associate { it.id!! to loadChoiceRow(internalUserId, definition.id!!, it.id!!) }
        return rewards.map { reward ->
            val choiceRow = choiceRows[reward.id]
            val grantedCards = when (reward.rewardType) {
                "CARD_CHOICE_ROLL" -> choiceRow?.selectedUserCardIds.orEmpty()
                "RANDOM_CARD" -> reward.id?.let { randomCardsByRewardId[it] }.orEmpty().map { it.userCardId }
                else -> emptyList()
            }
            mapOf<String, Any?>(
                "rewardId" to reward.id,
                "type" to reward.rewardType,
                "amount" to reward.amount,
                "code" to reward.rewardCode,
                "metadata" to reward.metadata,
                "displayOrder" to reward.displayOrder,
                "choiceRequiredCount" to choiceRow?.requiredCount,
                "choiceOptions" to choiceRow?.options?.map { option ->
                    mapOf<String, Any?>(
                        "optionId" to option.optionId,
                        "fantasyPlayerId" to option.fantasyPlayerId,
                        "playerName" to option.playerName,
                        "playerPhotoUrl" to option.playerPhotoUrl,
                        "rarity" to option.rarity,
                        "skinCode" to option.skinCode,
                        "perkIds" to option.perkIds,
                    )
                },
                "selectedOptionIds" to choiceRow?.selectedOptionIds.orEmpty(),
                "selectedUserCardIds" to choiceRow?.selectedUserCardIds.orEmpty(),
                "choiceClaimedAt" to choiceRow?.claimedAt,
                "grantedCardIds" to grantedCards,
                "grantedCards" to when (reward.rewardType) {
                    "RANDOM_CARD" -> reward.id?.let { randomCardsByRewardId[it] }.orEmpty()
                    else -> emptyList()
                },
            )
        }
    }

    private companion object {
        val COSMETIC_REWARD_TYPES = setOf("PROFILE_FRAME", "COSMETIC_UNLOCK", "BADGE_STYLE")
    }

    private data class ClaimContext(
        val definition: AchievementDefinition,
        val row: UserAchievement,
        val now: Instant,
    )

    private data class ChoiceRow(
        val requiredCount: Int,
        val options: List<AchievementCardRewardOptionInternal>,
        val claimedAt: Instant?,
        val selectedOptionIds: List<String>,
        val selectedUserCardIds: List<Long>,
    )
}
