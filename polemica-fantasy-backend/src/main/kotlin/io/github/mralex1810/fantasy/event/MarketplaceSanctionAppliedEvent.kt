package io.github.mralex1810.fantasy.event

import io.github.mralex1810.fantasy.entity.Rarity

data class MarketplaceSanctionAppliedEvent(
    val listingId: Long,
    val playerName: String,
    val rarity: Rarity,
    val price: Long,
    val reason: String,
    val sellerTelegramChatId: Long,
    val sellerFine: Long,
    val sellerNewBalance: Long,
    val buyerTelegramChatId: Long,
    val buyerFine: Long,
    val buyerNewBalance: Long,
    val complainants: List<ComplainantRewardInfo>,
)

data class ComplainantRewardInfo(
    val telegramChatId: Long,
    val reward: Long,
    val newBalance: Long,
)
