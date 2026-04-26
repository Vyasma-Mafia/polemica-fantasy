package io.github.mralex1810.fantasy.dto.user.response

data class RatingEntryDto(
    val rank: Int,
    val user: UserPublicDto,
    val fantikiBalance: Long,
    val cardsValue: Long,
    val totalValue: Long,
    val cardsCount: Int,
    /** Sum of fantiki credited from series leaderboard (SERIES_REWARD). Not included in [totalValue]. */
    val prizeWinnings: Long,
)

data class GlobalRatingDto(
    val entries: List<RatingEntryDto>,
    val currentUser: RatingEntryDto?,
)
