package io.github.mralex1810.fantasy.event

import io.github.mralex1810.fantasy.entity.Rarity

data class MarketplaceListingCreatedEvent(
    val listingId: Long,
    val sellerId: Long,
    val fantasyPlayerId: Long,
    val tournamentIds: List<Long>,
    val cardTemplateId: Long,
    val rarity: Rarity,
    val price: Long,
    val playerName: String,
)
