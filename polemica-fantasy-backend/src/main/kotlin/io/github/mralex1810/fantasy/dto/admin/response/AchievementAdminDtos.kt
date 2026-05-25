package io.github.mralex1810.fantasy.dto.admin.response

import java.time.Instant

data class AchievementDryRunResponseDto(
    val instantCompleted: Long,
    val instantFantikiLiability: Long,
    val rows: List<AchievementDryRunRowDto>,
)

data class AchievementDryRunRowDto(
    val code: String,
    val enabled: Boolean,
    val instantCompleted: Long,
    val instantFantikiLiability: Long,
)

data class AchievementAdminListResponseDto(
    val achievements: List<AchievementAdminDefinitionDto>,
)

data class AchievementAdminDefinitionDto(
    val code: String,
    val category: String,
    val conditionType: String,
    val historyPolicy: String,
    val targetValue: Long,
    val chainGroup: String?,
    val chainLevel: Int?,
    val title: String,
    val description: String?,
    val iconUrl: String?,
    val accentColor: String?,
    val rarity: String,
    val visibility: String,
    val enabled: Boolean,
    val trackingStartedAt: Instant?,
    val displayOrder: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
    val rewards: List<AchievementAdminRewardDto>,
    val stats: AchievementAdminStatsDto,
)

data class AchievementAdminRewardDto(
    val type: String,
    val amount: Long?,
    val code: String?,
    val metadata: String?,
    val displayOrder: Int,
)

data class AchievementAdminStatsDto(
    val completedUsers: Long,
    val claimedUsers: Long,
    val unclaimedUsers: Long,
    val totalProgress: Long,
    val averageProgress: Double,
    val nearCompletionUsers: Long,
    val lastCompletedAt: Instant?,
)
