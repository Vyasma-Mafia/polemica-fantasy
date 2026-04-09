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
)

data class MarketplaceListingEntryDto(
    val listingId: Long,
    val price: Long,
    val createdAt: Instant,
    val card: MarketplaceListingCardDto,
    val seller: MarketplaceSellerBriefDto,
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
    val playerName: String,
    val rarity: Rarity,
    val price: Long,
    val soldAt: Instant,
    val buyerDisplayName: String,
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
