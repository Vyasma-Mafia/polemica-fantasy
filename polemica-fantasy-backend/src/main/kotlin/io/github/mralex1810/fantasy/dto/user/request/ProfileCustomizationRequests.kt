package io.github.mralex1810.fantasy.dto.user.request

data class UpdateProfileCustomizationRequest(
    val profileFrameCode: String?,
    val featuredAchievementCodes: List<String> = emptyList(),
    val favoriteBadgeFantasyPlayerId: Long? = null,
)
