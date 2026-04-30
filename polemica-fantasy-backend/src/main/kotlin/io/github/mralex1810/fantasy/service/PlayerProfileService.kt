package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.config.AppRatingProperties
import io.github.mralex1810.fantasy.dto.user.response.PlayerCollectionSummaryDto
import io.github.mralex1810.fantasy.dto.user.response.PlayerMarketplaceStatsDto
import io.github.mralex1810.fantasy.dto.user.response.PlayerMarketplaceTradeDto
import io.github.mralex1810.fantasy.dto.user.response.PlayerProfileDto
import io.github.mralex1810.fantasy.dto.user.response.PlayerRatingSnapshotDto
import io.github.mralex1810.fantasy.dto.user.response.PlayerSeriesResultDto
import io.github.mralex1810.fantasy.dto.user.response.TradeType
import io.github.mralex1810.fantasy.dto.user.response.UserPublicDto
import io.github.mralex1810.fantasy.entity.MarketplaceListingStatus
import io.github.mralex1810.fantasy.entity.Rarity
import io.github.mralex1810.fantasy.repository.FantasyTeamRepository
import io.github.mralex1810.fantasy.repository.MarketplaceListingRepository
import io.github.mralex1810.fantasy.repository.TelegramUserRepository
import io.github.mralex1810.fantasy.repository.UserCardRepository
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class PlayerProfileService(
    private val telegramUserRepository: TelegramUserRepository,
    private val globalRatingDataCache: GlobalRatingDataCache,
    private val appRatingProperties: AppRatingProperties,
    private val fantasyTeamRepository: FantasyTeamRepository,
    private val userCardRepository: UserCardRepository,
    private val marketplaceListingRepository: MarketplaceListingRepository,
) {
    companion object {
        private const val MAX_SERIES_HISTORY = 20
        private const val MAX_RECENT_TRADES = 20
    }

    @Transactional(readOnly = true)
    fun getProfile(telegramId: Long): PlayerProfileDto {
        val user = telegramUserRepository.findByTelegramId(telegramId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")
        val userId = user.id!!

        val rating = buildRatingSnapshot(userId, telegramId)
        val seriesHistory = buildSeriesHistory(userId)
        val collectionSummary = buildCollectionSummary(userId)
        val marketplaceStats = buildMarketplaceStats(userId)
        val recentTrades = buildRecentTrades(userId)

        return PlayerProfileDto(
            user = UserPublicDto(
                telegramId = user.telegramId,
                username = user.username,
                firstName = user.firstName,
                displayName = user.displayName,
            ),
            memberSince = user.createdAt,
            rating = rating,
            seriesHistory = seriesHistory,
            collectionSummary = collectionSummary,
            marketplaceStats = marketplaceStats,
            recentTrades = recentTrades,
        )
    }

    private fun buildRatingSnapshot(userId: Long, telegramId: Long): PlayerRatingSnapshotDto? {
        if (appRatingProperties.isExcludedFromRating(telegramId)) return null
        val entry = globalRatingDataCache.loadSnapshot().find { it.userId == userId } ?: return null
        return PlayerRatingSnapshotDto(
            rank = entry.rank,
            fantikiBalance = entry.fantikiBalance,
            cardsValue = entry.cardsValue,
            cardsCount = entry.cardsCount,
            prizeWinnings = entry.prizeWinnings,
            totalValue = entry.totalValue,
        )
    }

    private fun buildSeriesHistory(userId: Long): List<PlayerSeriesResultDto> {
        val teams = fantasyTeamRepository.findAllByUserIdWithSeriesAndLeague(userId)
            .take(MAX_SERIES_HISTORY)

        return teams.map { ft ->
            val series = ft.series!!
            val tournament = series.tournament!!
            val seriesLeague = ft.seriesLeague!!
            val league = seriesLeague.league!!

            val leaderboard = fantasyTeamRepository.findLeaderboardForSeriesLeague(seriesLeague.id!!)
            val rank = leaderboard.indexOfFirst { it.telegramUser!!.id == userId }
                .let { if (it >= 0) it + 1 else null }
            val participantsCount = leaderboard.size

            PlayerSeriesResultDto(
                seriesId = series.id!!,
                seriesName = series.name,
                tournamentId = tournament.id!!,
                tournamentName = tournament.name,
                leagueCode = league.code,
                leagueName = league.name,
                rank = rank,
                totalScore = ft.totalScore,
                participantsCount = participantsCount,
                status = series.status,
            )
        }
    }

    private fun buildCollectionSummary(userId: Long): PlayerCollectionSummaryDto {
        val rows = userCardRepository.countByUserGroupedByRarity(userId)
        val byRarity = mutableMapOf<Rarity, Int>()
        var total = 0
        for (row in rows) {
            val rarity = row[0] as Rarity
            val count = (row[1] as Number).toInt()
            byRarity[rarity] = count
            total += count
        }
        return PlayerCollectionSummaryDto(totalCards = total, byRarity = byRarity)
    }

    private fun buildMarketplaceStats(userId: Long): PlayerMarketplaceStatsDto {
        val activeSales = marketplaceListingRepository
            .countBySeller_IdAndStatus(userId, MarketplaceListingStatus.ACTIVE)
        val totalSold = marketplaceListingRepository
            .countBySeller_IdAndStatus(userId, MarketplaceListingStatus.SOLD)
        val totalPurchased = marketplaceListingRepository
            .countByBuyer_IdAndStatus(userId, MarketplaceListingStatus.SOLD)
        return PlayerMarketplaceStatsDto(
            activeSalesCount = activeSales.toInt(),
            totalSoldCount = totalSold.toInt(),
            totalPurchasedCount = totalPurchased.toInt(),
        )
    }

    private fun buildRecentTrades(userId: Long): List<PlayerMarketplaceTradeDto> {
        val listings = marketplaceListingRepository.findRecentTradesByUserId(
            sold = MarketplaceListingStatus.SOLD,
            userId = userId,
            pageable = PageRequest.of(0, MAX_RECENT_TRADES),
        )
        return listings.map { ml ->
            val isSale = ml.seller!!.id == userId
            val counterparty = if (isSale) ml.buyer!! else ml.seller!!
            val ct = ml.userCard!!.cardTemplate!!
            PlayerMarketplaceTradeDto(
                playerName = ct.fantasyPlayer!!.nickname,
                rarity = ct.rarity,
                price = ml.price,
                date = ml.soldAt!!,
                counterpartyDisplayName = counterparty.displayName
                    ?: counterparty.firstName
                    ?: counterparty.username
                    ?: counterparty.telegramId.toString(),
                type = if (isSale) TradeType.SALE else TradeType.PURCHASE,
            )
        }
    }
}
