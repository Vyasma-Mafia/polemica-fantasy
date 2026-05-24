package io.github.mralex1810.fantasy.dto.user.request

import io.github.mralex1810.fantasy.entity.Rarity

data class CreateMarketplaceWatchRequest(
    val fantasyPlayerId: Long?,
    val tournamentId: Long?,
    val rarity: Rarity?,
    val maxPrice: Long?,
    val perkIds: List<String> = emptyList(),
)
