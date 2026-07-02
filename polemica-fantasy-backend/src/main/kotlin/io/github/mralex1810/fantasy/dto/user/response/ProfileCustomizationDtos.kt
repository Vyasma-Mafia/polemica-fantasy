package io.github.mralex1810.fantasy.dto.user.response

data class ProfileCustomizationDto(
    val profileFrameCode: String?,
    val unlockedFrames: List<ProfileFrameDto>,
    val profileTitleCode: String?,
    val profileAccentCode: String?,
    val profileBackgroundCode: String?,
    val unlockedCosmetics: ProfileCosmeticOptionsDto,
    val featuredAchievementCodes: List<String>,
    val availableFeaturedAchievements: List<AchievementBadgeDto>,
    val favoriteBadgeFantasyPlayerId: Long?,
    val favoriteBadgePlayerOptions: List<FavoriteBadgePlayerOptionDto>,
)

data class ProfileFrameDto(
    val code: String,
    val name: String,
    val assetUrl: String?,
)

data class ProfileCosmeticOptionsDto(
    val titles: List<ProfileCosmeticDto>,
    val accents: List<ProfileCosmeticDto>,
    val backgrounds: List<ProfileCosmeticDto>,
)

data class ProfileCosmeticDto(
    val code: String,
    val kind: String,
    val name: String,
    val description: String?,
    val styleToken: String?,
)

data class AchievementBadgeDto(
    val code: String,
    val title: String,
    val iconUrl: String?,
    val rarity: String,
    val accentColor: String?,
)

data class FavoriteBadgePlayerOptionDto(
    val fantasyPlayerId: Long,
    val nickname: String,
    val rarityCount: Int,
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
    val profileTitle: ProfileCosmeticDto?,
    val profileAccent: ProfileCosmeticDto?,
    val profileBackground: ProfileCosmeticDto?,
    val featuredAchievements: List<AchievementBadgeDto>,
    val nextAchievement: PlayerNextAchievementDto?,
)
