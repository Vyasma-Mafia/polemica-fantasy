package io.github.mralex1810.fantasy.dto.user.response

data class RatingEntryDto(
    val rank: Int,
    val user: UserPublicDto,
    val fantikiBalance: Long,
    val cardsValue: Long,
    val totalValue: Long,
    val cardsCount: Int,
)

data class GlobalRatingDto(
    val entries: List<RatingEntryDto>,
    val currentUser: RatingEntryDto?,
)
