package io.github.mralex1810.fantasy.service.achievement

import io.github.mralex1810.fantasy.dto.user.response.AchievementCatalogDto
import io.github.mralex1810.fantasy.dto.user.response.AchievementCategoryDto
import io.github.mralex1810.fantasy.dto.user.response.AchievementItemDto
import io.github.mralex1810.fantasy.dto.user.response.AchievementRewardDto
import io.github.mralex1810.fantasy.dto.user.response.AchievementSummaryDto
import io.github.mralex1810.fantasy.entity.AchievementDefinition
import io.github.mralex1810.fantasy.entity.TelegramUser
import io.github.mralex1810.fantasy.entity.UserAchievement
import io.github.mralex1810.fantasy.repository.AchievementDefinitionRepository
import io.github.mralex1810.fantasy.repository.UserAchievementRepository
import io.github.mralex1810.fantasy.repository.UserProfileCustomizationRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AchievementCatalogService(
    private val achievementDefinitionRepository: AchievementDefinitionRepository,
    private val userAchievementRepository: UserAchievementRepository,
    private val achievementProgressCalculator: AchievementProgressCalculator,
    private val customizationRepository: UserProfileCustomizationRepository,
) {
    @Transactional(readOnly = true)
    fun catalogFor(user: TelegramUser): AchievementCatalogDto {
        val userId = user.id!!
        val definitions = achievementDefinitionRepository.findAllEnabledPublicWithRewards()
        val progressByDefinitionId = userAchievementRepository.findAllByTelegramUser_Id(userId)
            .associateBy { it.achievement!!.id!! }
        val items = definitions.map { definition ->
            toItem(definition, progressByDefinitionId[definition.id!!], userId)
        }
        val categories = items.groupBy { it.category }.map { (category, categoryItems) ->
            AchievementCategoryDto(
                code = category,
                name = categoryName(category),
                achievements = categoryItems,
            )
        }
        return AchievementCatalogDto(
            categories = categories,
            summary = AchievementSummaryDto(
                completed = items.count { it.state == "COMPLETED_UNCLAIMED" || it.state == "CLAIMED" },
                claimed = items.count { it.state == "CLAIMED" },
                totalVisible = items.size,
                unclaimedRewards = items.count { it.state == "COMPLETED_UNCLAIMED" },
            ),
        )
    }

    fun toItem(definition: AchievementDefinition, progress: UserAchievement?, internalTelegramUserId: Long): AchievementItemDto {
        val currentProgress =
            if (progress?.completedAt == null) {
                achievementProgressCalculator.currentProgress(internalTelegramUserId, definition)
            } else {
                progress.progressValue
            }
        val progressValue = progress?.progressValue?.coerceAtLeast(currentProgress) ?: currentProgress
        val state = when {
            progress?.claimedAt != null -> "CLAIMED"
            progress?.completedAt != null || progressValue >= definition.targetValue -> "COMPLETED_UNCLAIMED"
            progressValue > 0 -> "IN_PROGRESS"
            else -> "LOCKED"
        }
        return AchievementItemDto(
            code = definition.code,
            title = displayTitle(definition, internalTelegramUserId),
            description = definition.description,
            category = definition.category,
            conditionType = definition.conditionType,
            state = state,
            progressValue = progressValue,
            targetValue = definition.targetValue,
            completedAt = progress?.completedAt,
            claimedAt = progress?.claimedAt,
            historyPolicy = definition.historyPolicy,
            rarity = definition.rarity,
            visibility = definition.visibility,
            iconUrl = definition.iconUrl,
            accentColor = definition.accentColor,
            rewards = definition.rewards.sortedWith(compareBy({ it.displayOrder }, { it.id ?: 0L })).map {
                AchievementRewardDto(
                    id = it.id,
                    type = it.rewardType,
                    amount = it.amount,
                    code = it.rewardCode,
                    metadata = it.metadata,
                )
            },
        )
    }

    fun displayTitle(definition: AchievementDefinition, internalTelegramUserId: Long): String {
        val favoriteBadgeFantasyPlayerId = if (definition.isSamePlayerRarityCollector()) {
            customizationRepository.findByTelegramUser_Id(internalTelegramUserId)?.favoriteBadgeFantasyPlayerId
        } else {
            null
        }
        val nickname = achievementProgressCalculator.samePlayerRarityCollectorNickname(
            internalTelegramUserId,
            definition,
            favoriteBadgeFantasyPlayerId,
        )
        return if (nickname == null) definition.title else "${definition.title}: $nickname"
    }

    private fun AchievementDefinition.isSamePlayerRarityCollector(): Boolean =
        conditionType == "SAME_PLAYER_3_RARITIES" || conditionType == "SAME_PLAYER_4_RARITIES"

    private fun categoryName(category: String): String =
        when (category) {
            "PARTICIPATION" -> "Участие"
            "BUDGET" -> "Бюджетная лига"
            "RESULTS" -> "Результаты"
            "COLLECTION" -> "Коллекция"
            "PACKS" -> "Паки"
            else -> category
        }
}
