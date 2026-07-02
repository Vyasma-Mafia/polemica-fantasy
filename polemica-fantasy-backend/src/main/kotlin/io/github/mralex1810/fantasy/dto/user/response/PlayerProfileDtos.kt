package io.github.mralex1810.fantasy.dto.user.response

import io.github.mralex1810.fantasy.entity.Rarity
import io.github.mralex1810.fantasy.entity.SeriesStatus
import java.time.Instant

data class PlayerProfileDto(
    val user: UserPublicDto,
    val memberSince: Instant,
    val rating: PlayerRatingSnapshotDto?,
    val seriesWins: PlayerSeriesWinsDto,
    val achievementSummary: PlayerAchievementSummaryDto,
    val profileFrame: ProfileFrameDto?,
    val profileTitle: ProfileCosmeticDto?,
    val profileAccent: ProfileCosmeticDto?,
    val profileBackground: ProfileCosmeticDto?,
    val featuredAchievements: List<AchievementBadgeDto>,
    val nextAchievement: PlayerNextAchievementDto?,
    val seriesHistory: List<PlayerSeriesResultDto>,
    val collectionSummary: PlayerCollectionSummaryDto,
    val marketplaceStats: PlayerMarketplaceStatsDto,
    val recentTrades: List<PlayerMarketplaceTradeDto>,
)

data class PlayerRatingSnapshotDto(
    val rank: Int,
    val fantikiBalance: Long,
    val cardsValue: Long,
    val cardsCount: Int,
    val prizeWinnings: Long,
    val totalValue: Long,
)

data class PlayerSeriesWinsDto(
    val total: Int,
    val byLeague: List<PlayerLeagueWinsDto>,
)

data class PlayerLeagueWinsDto(
    val leagueCode: String,
    val leagueName: String,
    val winsCount: Int,
)

data class PlayerSeriesResultDto(
    val seriesId: Long,
    val seriesName: String,
    val tournamentId: Long,
    val tournamentName: String,
    val leagueCode: String,
    val leagueName: String,
    val rank: Int?,
    val totalScore: Double?,
    val participantsCount: Int,
    val status: SeriesStatus,
)

data class PlayerCollectionSummaryDto(
    val totalCards: Int,
    val byRarity: Map<Rarity, Int>,
)

data class PlayerMarketplaceStatsDto(
    val activeSalesCount: Int,
    val totalSoldCount: Int,
    val totalPurchasedCount: Int,
)

enum class TradeType {
    SALE,
    PURCHASE,
}

data class PlayerMarketplaceTradeDto(
    val listingId: Long,
    val playerName: String,
    val rarity: Rarity,
    val price: Long,
    val date: Instant,
    val counterpartyDisplayName: String,
    val type: TradeType,
    val sanctioned: Boolean,
)
