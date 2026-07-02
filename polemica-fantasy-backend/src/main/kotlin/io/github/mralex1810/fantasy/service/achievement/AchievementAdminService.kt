package io.github.mralex1810.fantasy.service.achievement

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.mralex1810.fantasy.dto.admin.request.UpdateAchievementAdminRequest
import io.github.mralex1810.fantasy.dto.admin.request.UpdateAchievementAdminRewardRequest
import io.github.mralex1810.fantasy.dto.admin.response.AchievementAdminDefinitionDto
import io.github.mralex1810.fantasy.dto.admin.response.AchievementAdminListResponseDto
import io.github.mralex1810.fantasy.dto.admin.response.AchievementAdminRewardDto
import io.github.mralex1810.fantasy.dto.admin.response.AchievementAdminStatsDto
import io.github.mralex1810.fantasy.dto.admin.response.AchievementDryRunResponseDto
import io.github.mralex1810.fantasy.dto.admin.response.AchievementDryRunRowDto
import io.github.mralex1810.fantasy.entity.AchievementDefinition
import io.github.mralex1810.fantasy.entity.AchievementReward
import io.github.mralex1810.fantasy.repository.AchievementAdminStatsProjection
import io.github.mralex1810.fantasy.repository.AchievementDefinitionRepository
import io.github.mralex1810.fantasy.repository.AchievementRewardRepository
import io.github.mralex1810.fantasy.repository.CardSkinRepository
import io.github.mralex1810.fantasy.repository.ProfileCosmeticRepository
import io.github.mralex1810.fantasy.repository.TelegramUserRepository
import io.github.mralex1810.fantasy.repository.UserAchievementRepository
import jakarta.persistence.EntityManager
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

@Service
class AchievementAdminService(
    private val achievementDefinitionRepository: AchievementDefinitionRepository,
    private val achievementRewardRepository: AchievementRewardRepository,
    private val telegramUserRepository: TelegramUserRepository,
    private val userAchievementRepository: UserAchievementRepository,
    private val cardSkinRepository: CardSkinRepository,
    private val profileCosmeticRepository: ProfileCosmeticRepository,
    private val achievementProgressCalculator: AchievementProgressCalculator,
    private val objectMapper: ObjectMapper,
    private val entityManager: EntityManager,
) {
    @Transactional(readOnly = true)
    fun listAchievements(): AchievementAdminListResponseDto {
        val statsByAchievementId = achievementDefinitionRepository.aggregateAdminStats()
            .associateBy { it.getAchievementId() }
        val achievements = achievementDefinitionRepository.findAllWithRewards()
            .map { toAdminDto(it, statsByAchievementId[it.id!!]) }
        return AchievementAdminListResponseDto(achievements)
    }

    @Transactional
    fun updateAchievement(code: String, request: UpdateAchievementAdminRequest): AchievementAdminDefinitionDto {
        val definition = achievementDefinitionRepository.findByCode(code)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Achievement $code not found")
        val wasEnabled = definition.enabled
        val now = Instant.now()

        definition.title = normalizedText(request.title, "title", required = true, max = 255)!!
        definition.description = normalizedText(request.description, "description", required = false, max = 4096)
        definition.iconUrl = normalizedText(request.iconUrl, "iconUrl", required = false, max = 2048)
        definition.accentColor = normalizedAccentColor(request.accentColor)
        definition.rarity = normalizedEnum(request.rarity, "rarity", allowedRarities)
        definition.visibility = normalizedEnum(request.visibility, "visibility", allowedVisibilities)
        definition.enabled = request.enabled
        definition.displayOrder = nonNegative(request.displayOrder, "displayOrder")
        if (!wasEnabled && definition.enabled && definition.trackingStartedAt == null) {
            definition.trackingStartedAt = now
        }
        definition.updatedAt = now

        replaceRewards(definition, request.rewards)
        val saved = achievementDefinitionRepository.save(definition)
        achievementDefinitionRepository.flush()
        entityManager.clear()

        val stats = achievementDefinitionRepository.aggregateAdminStats()
            .associateBy { it.getAchievementId() }[saved.id!!]
        val reloaded = achievementDefinitionRepository.findByCodeWithRewards(saved.code)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Achievement $code not found")
        return toAdminDto(reloaded, stats)
    }

    @Transactional(readOnly = true)
    fun dryRun(): AchievementDryRunResponseDto {
        val users = telegramUserRepository.findAll()
        val rows = achievementDefinitionRepository.findAllWithRewards()
            .filter { it.enabled }
            .map { definition ->
                val completedUsers = users.count { user ->
                    val existing = userAchievementRepository.findByTelegramUser_IdAndAchievement_Id(
                        user.id!!,
                        definition.id!!,
                    )
                    existing?.claimedAt == null &&
                        achievementProgressCalculator.currentProgress(user.id!!, definition) >= definition.targetValue
                }.toLong()
                val fantikiReward = definition.rewards
                    .filter { it.rewardType == "FANTIKI" }
                    .sumOf { it.amount ?: 0L }
                AchievementDryRunRowDto(
                    code = definition.code,
                    enabled = definition.enabled,
                    instantCompleted = completedUsers,
                    instantFantikiLiability = completedUsers * fantikiReward,
                )
            }
        return AchievementDryRunResponseDto(
            instantCompleted = rows.sumOf { it.instantCompleted },
            instantFantikiLiability = rows.sumOf { it.instantFantikiLiability },
            rows = rows,
        )
    }

    private fun replaceRewards(
        definition: AchievementDefinition,
        requests: List<UpdateAchievementAdminRewardRequest>,
    ) {
        if (requests.size > 10) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "rewards must contain at most 10 rows")
        }
        val normalizedRewards = requests.map { normalizeReward(it) }
        val duplicateDisplayOrder = normalizedRewards
            .groupBy { it.displayOrder }
            .values
            .any { it.size > 1 }
        if (duplicateDisplayOrder) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "reward displayOrder values must be unique")
        }

        val achievementId = definition.id
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "achievement id is required")
        achievementRewardRepository.deleteByAchievementId(achievementId)
        achievementRewardRepository.flush()
        achievementRewardRepository.saveAll(normalizedRewards.map { it.toEntity(definition) })
        achievementRewardRepository.flush()
    }

    private fun normalizeReward(request: UpdateAchievementAdminRewardRequest): NormalizedReward {
        val type = normalizedText(request.type, "reward.type", required = true, max = 48)!!.uppercase()
        val displayOrder = nonNegative(request.displayOrder, "reward.displayOrder")
        val metadata = normalizedMetadata(request.metadata)
        return when (type) {
            "FANTIKI" -> {
                val amount = request.amount
                    ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "reward.amount is required for FANTIKI")
                if (amount <= 0) {
                    throw ResponseStatusException(HttpStatus.BAD_REQUEST, "reward.amount must be positive for FANTIKI")
                }
                if (!request.code.isNullOrBlank()) {
                    throw ResponseStatusException(HttpStatus.BAD_REQUEST, "reward.code must be blank for FANTIKI")
                }
                NormalizedReward(type = type, amount = amount, code = null, metadata = metadata, displayOrder = displayOrder)
            }
            in cosmeticRewardTypes -> {
                if (request.amount != null) {
                    throw ResponseStatusException(HttpStatus.BAD_REQUEST, "reward.amount must be null for $type")
                }
                val rewardCode = normalizedText(request.code, "reward.code", required = true, max = 96)!!
                if (type == "COSMETIC_UNLOCK" && !profileCosmeticRepository.existsById(rewardCode)) {
                    throw ResponseStatusException(HttpStatus.BAD_REQUEST, "reward.code must reference profile_cosmetic for COSMETIC_UNLOCK")
                }
                NormalizedReward(type = type, amount = null, code = rewardCode, metadata = metadata, displayOrder = displayOrder)
            }
            in cardRewardTypes -> {
                if (request.amount != null) {
                    throw ResponseStatusException(HttpStatus.BAD_REQUEST, "reward.amount must be null for $type")
                }
                if (!request.code.isNullOrBlank()) {
                    throw ResponseStatusException(HttpStatus.BAD_REQUEST, "reward.code must be blank for $type")
                }
                val requiredMetadata = metadata
                    ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "reward.metadata is required for $type")
                validateCardRewardMetadata(type, requiredMetadata)
                NormalizedReward(
                    type = type,
                    amount = null,
                    code = null,
                    metadata = requiredMetadata,
                    displayOrder = displayOrder,
                )
            }
            else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "unsupported reward type: $type")
        }
    }

    private fun normalizedText(value: String?, field: String, required: Boolean, max: Int): String? {
        val trimmed = value?.trim()
        if (required && trimmed.isNullOrEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "$field is required")
        }
        if (trimmed != null && trimmed.length > max) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "$field is too long")
        }
        return trimmed?.takeIf { it.isNotEmpty() }
    }

    private fun normalizedAccentColor(value: String?): String? {
        val trimmed = normalizedText(value, "accentColor", required = false, max = 32)
        if (trimmed != null && !accentColorRegex.matches(trimmed)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "accentColor must be #RRGGBB")
        }
        return trimmed
    }

    private fun validateCardRewardMetadata(type: String, metadata: String) {
        val node = try {
            objectMapper.readTree(metadata)
        } catch (e: Exception) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "reward.metadata must be a JSON object", e)
        }
        if (!node.isObject) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "reward.metadata must be a JSON object")
        }
        val rarity = node.get("rarity")?.takeIf { it.isTextual }?.asText()?.uppercase()
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "reward.metadata.rarity is required")
        if (rarity !in allowedRarities) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "reward.metadata.rarity is invalid")
        }
        if (rarity == "LEGENDARY") {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "LEGENDARY card rewards require a non-pack source and are not supported yet",
            )
        }
        val count = node.get("count")?.takeIf { it.isInt }?.asInt()
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "reward.metadata.count is required")
        if (count <= 0) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "reward.metadata.count must be positive")
        }
        val options = node.get("options")?.takeIf { it.isInt }?.asInt()
        if (type == "CARD_CHOICE_ROLL") {
            if (options == null) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "reward.metadata.options is required")
            }
            if (options < count) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "reward.metadata.options must be >= count")
            }
        } else if (node.has("options")) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "reward.metadata.options is only supported for CARD_CHOICE_ROLL")
        }
        val skinCode = node.get("skinCode")?.takeIf { it.isTextual }?.asText()?.trim()?.takeIf { it.isNotEmpty() }
        if (skinCode != null && cardSkinRepository.findByCode(skinCode) == null) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "reward.metadata.skinCode is unknown")
        }
        val source = node.get("source")?.takeIf { it.isTextual }?.asText() ?: "ACTIVE_PACKS"
        if (source != "ACTIVE_PACKS") {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "reward.metadata.source is unsupported")
        }
    }

    private fun normalizedEnum(value: String?, field: String, allowed: Set<String>): String {
        val normalized = normalizedText(value, field, required = true, max = 64)!!.uppercase()
        if (normalized !in allowed) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "$field is invalid")
        }
        return normalized
    }

    private fun nonNegative(value: Int, field: String): Int {
        if (value < 0) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "$field must be non-negative")
        }
        return value
    }

    private fun normalizedMetadata(value: String?): String? {
        val trimmed = normalizedText(value, "reward.metadata", required = false, max = 4096)
        if (trimmed != null) {
            val node = try {
                objectMapper.readTree(trimmed)
            } catch (e: Exception) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "reward.metadata must be a JSON object", e)
            }
            if (!node.isObject) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "reward.metadata must be a JSON object")
            }
        }
        return trimmed
    }

    private fun toAdminDto(
        definition: AchievementDefinition,
        stats: AchievementAdminStatsProjection?,
    ): AchievementAdminDefinitionDto =
        AchievementAdminDefinitionDto(
            code = definition.code,
            category = definition.category,
            conditionType = definition.conditionType,
            historyPolicy = definition.historyPolicy,
            targetValue = definition.targetValue,
            chainGroup = definition.chainGroup,
            chainLevel = definition.chainLevel,
            title = definition.title,
            description = definition.description,
            iconUrl = definition.iconUrl,
            accentColor = definition.accentColor,
            rarity = definition.rarity,
            visibility = definition.visibility,
            enabled = definition.enabled,
            trackingStartedAt = definition.trackingStartedAt,
            displayOrder = definition.displayOrder,
            createdAt = definition.createdAt,
            updatedAt = definition.updatedAt,
            rewards = definition.rewards
                .sortedWith(compareBy<AchievementReward> { it.displayOrder }.thenBy { it.id ?: Long.MAX_VALUE })
                .map { toRewardDto(it) },
            stats = toStatsDto(stats),
        )

    private fun toRewardDto(reward: AchievementReward): AchievementAdminRewardDto =
        AchievementAdminRewardDto(
            type = reward.rewardType,
            amount = reward.amount,
            code = reward.rewardCode,
            metadata = reward.metadata,
            displayOrder = reward.displayOrder,
        )

    private fun toStatsDto(stats: AchievementAdminStatsProjection?): AchievementAdminStatsDto =
        AchievementAdminStatsDto(
            completedUsers = stats?.getCompletedUsers() ?: 0L,
            claimedUsers = stats?.getClaimedUsers() ?: 0L,
            unclaimedUsers = stats?.getUnclaimedUsers() ?: 0L,
            totalProgress = stats?.getTotalProgress() ?: 0L,
            averageProgress = stats?.getAverageProgress() ?: 0.0,
            nearCompletionUsers = stats?.getNearCompletionUsers() ?: 0L,
            lastCompletedAt = stats?.getLastCompletedAt(),
        )

    private data class NormalizedReward(
        val type: String,
        val amount: Long?,
        val code: String?,
        val metadata: String?,
        val displayOrder: Int,
    ) {
        fun toEntity(definition: AchievementDefinition): AchievementReward =
            AchievementReward(
                achievement = definition,
                rewardType = type,
                amount = amount,
                rewardCode = code,
                metadata = metadata,
                displayOrder = displayOrder,
            )
    }

    companion object {
        private val allowedRarities = setOf("COMMON", "RARE", "EPIC", "LEGENDARY")
        private val allowedVisibilities = setOf("PUBLIC", "HIDDEN", "SECRET", "PRIVATE")
        private val cosmeticRewardTypes = setOf("PROFILE_FRAME", "COSMETIC_UNLOCK", "BADGE_STYLE")
        private val cardRewardTypes = setOf("RANDOM_CARD", "CARD_CHOICE_ROLL")
        private val accentColorRegex = Regex("^#[0-9A-Fa-f]{6}$")
    }
}
