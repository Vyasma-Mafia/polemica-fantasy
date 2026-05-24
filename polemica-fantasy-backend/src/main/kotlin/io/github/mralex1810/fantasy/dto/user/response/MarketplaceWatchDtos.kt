package io.github.mralex1810.fantasy.dto.user.response

import java.time.Instant

data class TournamentBriefDto(
    val id: Long,
    val name: String,
)

data class MarketplaceWatchDto(
    val id: Long,
    val fantasyPlayer: FantasyPlayerBriefDto?,
    val tournament: TournamentBriefDto?,
    val rarity: String?,
    val maxPrice: Long?,
    val perks: List<MarketplaceWatchPerkDto>,
    val createdAt: Instant,
)

data class MarketplaceWatchPerkDto(
    val id: String,
    val name: String,
)

data class MarketplaceWatchesResponse(
    val watches: List<MarketplaceWatchDto>,
    val maxWatches: Int,
)
