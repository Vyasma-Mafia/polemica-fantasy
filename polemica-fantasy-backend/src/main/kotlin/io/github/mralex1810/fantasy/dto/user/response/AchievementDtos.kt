package io.github.mralex1810.fantasy.dto.user.response

import java.time.Instant

data class AchievementCatalogDto(
    val categories: List<AchievementCategoryDto>,
    val summary: AchievementSummaryDto,
)

data class AchievementCategoryDto(
    val code: String,
    val name: String,
    val achievements: List<AchievementItemDto>,
)

data class AchievementItemDto(
    val code: String,
    val title: String,
    val description: String?,
    val category: String,
    val conditionType: String,
    val state: String,
    val progressValue: Long,
    val targetValue: Long,
    val completedAt: Instant?,
    val claimedAt: Instant?,
    val historyPolicy: String,
    val rarity: String,
    val visibility: String,
    val iconUrl: String?,
    val accentColor: String?,
    val rewards: List<AchievementRewardDto>,
)

data class AchievementRewardDto(
    val type: String,
    val amount: Long?,
    val code: String?,
)

data class AchievementSummaryDto(
    val completed: Int,
    val claimed: Int,
    val totalVisible: Int,
    val unclaimedRewards: Int,
)

data class AchievementClaimResultDto(
    val achievementCode: String,
    val claimedAt: Instant,
    val fantikiDelta: Long,
    val newFantikiBalance: Long,
    val cosmeticUnlocks: List<AchievementCosmeticUnlockDto>,
)

data class AchievementCosmeticUnlockDto(
    val type: String,
    val code: String,
)
