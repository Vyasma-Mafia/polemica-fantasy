package io.github.mralex1810.fantasy.dto.user.request

data class UpdateProfileCustomizationRequest(
    val profileFrameCode: String?,
    val featuredAchievementCodes: List<String> = emptyList(),
    val favoriteBadgeFantasyPlayerId: Long? = null,
    val profileTitleCode: String? = null,
    val profileTitleCodeSet: Boolean = false,
    val profileAccentCode: String? = null,
    val profileAccentCodeSet: Boolean = false,
    val profileBackgroundCode: String? = null,
    val profileBackgroundCodeSet: Boolean = false,
)
