package io.github.mralex1810.fantasy.dto.admin.response

import io.github.mralex1810.fantasy.entity.Rarity
import java.time.Instant

data class PairAnalysisDto(
    val userATelegramId: Long,
    val userBTelegramId: Long,
    val tradesAtoB: Long,
    /** Суммарная цена сделок (брутто) в направлении A → B. */
    val tradesTotalAtoB: Long,
    val tradesBtoA: Long,
    val tradesTotalBtoA: Long,
    /** Суммарно получено продавцом A от покупок B, минус суммарно получено B от A (в ₣, после комиссии). */
    val netTransfer: Long,
    val bidirectional: Boolean,
)

data class PairTradeDto(
    val listingId: Long,
    val price: Long,
    val sellerReceived: Long,
    val soldAt: Instant?,
    val sellerTelegramId: Long,
    val buyerTelegramId: Long,
    val userCardId: Long,
    val playerName: String,
    val rarity: Rarity,
    val currentOwnerTelegramId: Long,
)

data class PairTradesResultDto(
    val trades: List<PairTradeDto>,
    val totalTrades: Int,
    val totalGrossFantiki: Long,
    val totalSellerReceived: Long,
)

data class BanPairConfiscatedCardDto(
    val userCardId: Long,
    val playerName: String,
    val rarity: Rarity,
)

data class BanPairUserResultDto(
    val telegramId: Long,
    val displayName: String,
    val fantikiConfiscated: Long,
    val newBalance: Long,
    val cardsConfiscated: List<BanPairConfiscatedCardDto>,
    val listingsCancelled: Int,
)

data class BanPairResultDto(
    val userA: BanPairUserResultDto,
    val userB: BanPairUserResultDto,
    val reason: String,
)
