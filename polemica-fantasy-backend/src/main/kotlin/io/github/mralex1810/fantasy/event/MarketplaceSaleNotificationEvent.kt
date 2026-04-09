package io.github.mralex1810.fantasy.event

import io.github.mralex1810.fantasy.entity.Rarity

data class MarketplaceSaleNotificationEvent(
    val sellerInternalUserId: Long,
    val sellerTelegramChatId: Long,
    val playerName: String,
    val rarity: Rarity,
    val price: Long,
    val sellerReceived: Long,
    val commission: Long,
)
