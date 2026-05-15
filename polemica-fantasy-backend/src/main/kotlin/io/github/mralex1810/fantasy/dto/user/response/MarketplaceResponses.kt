package io.github.mralex1810.fantasy.dto.user.response

import io.github.mralex1810.fantasy.entity.Rarity
import java.time.Instant

data class MarketplaceSellerBriefDto(
    val displayName: String,
)

data class MarketplaceCardAchievementDto(
    val achievementId: String,
    val name: String,
    val bonusPoints: Double,
)

data class MarketplaceListingCardDto(
    val userCardId: Long,
    val fantasyPlayerId: Long,
    val playerName: String,
    val playerPhotoUrl: String?,
    val rarity: Rarity,
    val achievements: List<MarketplaceCardAchievementDto>,
    val value: Long?,
    val skinCode: String?,
)

data class MarketplaceListingEntryDto(
    val listingId: Long,
    val price: Long,
    val createdAt: Instant,
    val card: MarketplaceListingCardDto,
    val seller: MarketplaceSellerBriefDto?,
    val canBuy: Boolean,
    val canBuyReason: String?,
)

data class MarketplaceListingsPageDto(
    val content: List<MarketplaceListingEntryDto>,
    val totalElements: Long,
    val totalPages: Int,
    val page: Int,
    val size: Int,
)

data class MarketplaceFeedItemDto(
    val listingId: Long,
    val playerName: String,
    val rarity: Rarity,
    val price: Long,
    val soldAt: Instant,
    val sellerDisplayName: String,
    val buyerDisplayName: String,
    val sanctioned: Boolean,
    /** Карта сделки — для превью в ленте (фото, ачивки). */
    val card: MarketplaceListingCardDto,
)

data class MarketplaceFeedDto(
    val items: List<MarketplaceFeedItemDto>,
)

data class BuyCardResultDto(
    val listing: MarketplaceListingEntryDto,
    val card: UserCardItemDto,
    val pricePaid: Long,
    val sellerReceived: Long,
    val commission: Long,
    val newBalance: Long,
)

data class MarketplaceAnalyticsSummaryDto(
    val items: List<MarketplaceAnalyticsSummaryItemDto>,
)

data class MarketplaceAnalyticsSummaryItemDto(
    val fantasyPlayerId: Long,
    val rarity: Rarity,
    val activeCount: Long,
    val minActivePrice: Long?,
)

data class MarketplaceAnalyticsDetailDto(
    val fantasyPlayerId: Long,
    val rarity: Rarity,
    val activeCount: Long,
    val activeMinPrice: Long?,
    val activeMaxPrice: Long?,
    val recentSales: List<MarketplaceRecentSaleDto>,
    val avgSalePrice: Long?,
)

data class MarketplaceRecentSaleDto(
    val price: Long,
    val soldAt: Instant,
)
