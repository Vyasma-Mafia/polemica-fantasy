package io.github.mralex1810.fantasy.dto.user.response

data class ProfileCustomizationDto(
    val profileFrameCode: String?,
    val unlockedFrames: List<ProfileFrameDto>,
    val featuredAchievementCodes: List<String>,
    val availableFeaturedAchievements: List<AchievementBadgeDto>,
)

data class ProfileFrameDto(
    val code: String,
    val name: String,
    val assetUrl: String?,
)

data class AchievementBadgeDto(
    val code: String,
    val title: String,
    val iconUrl: String?,
    val rarity: String,
    val accentColor: String?,
)

data class PlayerAchievementSummaryDto(
    val completed: Int,
    val claimed: Int,
    val totalVisible: Int,
)

data class PlayerNextAchievementDto(
    val code: String,
    val title: String,
    val progressValue: Long,
    val targetValue: Long,
)

data class PlayerAchievementShowcaseDto(
    val achievementSummary: PlayerAchievementSummaryDto,
    val profileFrame: ProfileFrameDto?,
    val featuredAchievements: List<AchievementBadgeDto>,
    val nextAchievement: PlayerNextAchievementDto?,
)
