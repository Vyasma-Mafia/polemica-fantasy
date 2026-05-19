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
    /** Модератор отметил пару как проверенную (не требует повторного просмотра). */
    val cleared: Boolean = false,
    val clearedAt: Instant? = null,
    val clearedNote: String? = null,
)

data class PairTradeDto(
    val listingId: Long,
    val price: Long,
    val sellerReceived: Long,
    val createdAt: Instant,
    val soldAt: Instant?,
    val sellerTelegramId: Long,
    val buyerTelegramId: Long,
    val userCardId: Long,
    val playerName: String,
    val rarity: Rarity,
    val currentOwnerTelegramId: Long,
    /** If false, the card is not removed at pair sanction; seller net for this sale is still recovered via fantiki. */
    val buyerStillOwnsCard: Boolean,
    val complaintsCount: Int,
)

data class PairTradesUserBriefDto(
    val username: String?,
    val telegramId: Long,
    val displayName: String,
    val fantiki: Long,
)

data class PairTradesResultDto(
    val userA: PairTradesUserBriefDto,
    val userB: PairTradesUserBriefDto,
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

data class BanPairPreviewUserDto(
    val telegramId: Long,
    val displayName: String,
    val balance: Long,
    val fantikiToConfiscate: Long,
    val balanceAfter: Long,
    val cardsToConfiscate: List<BanPairConfiscatedCardDto>,
)

data class BanPairPreviewDto(
    val userA: BanPairPreviewUserDto,
    val userB: BanPairPreviewUserDto,
)

data class PairSanctionHistoryItemDto(
    val id: Long,
    val createdAt: Instant,
    val reason: String,
    val userLowTelegramId: Long,
    val userHighTelegramId: Long,
    val userLowDisplayName: String,
    val userHighDisplayName: String,
    val fantikiTakenLow: Long,
    val fantikiTakenHigh: Long,
    val cardsCountLow: Int,
    val cardsCountHigh: Int,
)

data class PagedPairSanctionHistoryDto(
    val content: List<PairSanctionHistoryItemDto>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
)

data class MarketplaceAdminParticipantDto(
    val telegramId: Long,
    val displayName: String,
)

data class ComplainedTransactionDto(
    val listingId: Long,
    val playerName: String,
    val rarity: Rarity,
    val price: Long,
    val createdAt: Instant,
    val soldAt: Instant,
    val seller: MarketplaceAdminParticipantDto,
    val buyer: MarketplaceAdminParticipantDto,
    val complaintsCount: Int,
    val sanctioned: Boolean,
)

data class PagedComplainedTransactionsDto(
    val content: List<ComplainedTransactionDto>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
)

data class TransactionComplaintDetailDto(
    val userId: Long,
    val displayName: String,
    val telegramId: Long,
    val complainedAt: Instant,
)

data class ConcurrentListingDto(
    val listingId: Long,
    val sellerDisplayName: String,
    val sellerTelegramId: Long,
    val price: Long,
    val createdAt: Instant,
    val soldAt: Instant?,
    val active: Boolean,
    val sameTemplate: Boolean,
)

data class TransactionMarketContextDto(
    val listingCreatedAt: Instant,
    val concurrentSameTemplate: List<ConcurrentListingDto>,
    val concurrentSamePlayerRarity: List<ConcurrentListingDto>,
)

data class TransactionComplaintsListDto(
    val complaints: List<TransactionComplaintDetailDto>,
    val marketContext: TransactionMarketContextDto,
)

data class UserByComplaintsDto(
    val telegramId: Long,
    val displayName: String,
    val totalComplaints: Int,
    val transactionsWithComplaints: Int,
    val avgComplaintsPerTransaction: Double,
    val sanctionedTransactions: Int,
    val marketplaceBanned: Boolean,
    val marketplaceBannedUntil: Instant?,
)

data class PagedUsersByComplaintsDto(
    val content: List<UserByComplaintsDto>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
)

data class SanctionTransactionResultDto(
    val listingId: Long,
    val sellerFined: Long,
    val sellerNewBalance: Long,
    val sellerBannedUntil: Instant?,
    val buyerFined: Long,
    val buyerNewBalance: Long,
    val buyerBannedUntil: Instant?,
    val complainantsRewarded: Int,
    val totalRewardPaid: Long,
)
