package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.dto.user.response.ActiveMarketplaceListingBriefDto
import io.github.mralex1810.fantasy.dto.user.response.UserCardItemDto
import io.github.mralex1810.fantasy.entity.MarketplaceListingStatus
import io.github.mralex1810.fantasy.entity.Rarity
import io.github.mralex1810.fantasy.entity.TelegramUser
import io.github.mralex1810.fantasy.repository.FantasyTeamCardRepository
import io.github.mralex1810.fantasy.repository.MarketplaceListingRepository
import io.github.mralex1810.fantasy.repository.SeriesRepository
import io.github.mralex1810.fantasy.repository.UserCardRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class UserCardCollectionService(
    private val userCardRepository: UserCardRepository,
    private val seriesRepository: SeriesRepository,
    private val fantasyTeamCardRepository: FantasyTeamCardRepository,
    private val marketplaceListingRepository: MarketplaceListingRepository,
    private val imageStorageService: ImageStorageService,
    private val cardValueService: CardValueService,
) {

    @Transactional(readOnly = true)
    fun listCards(
        user: TelegramUser,
        tournamentId: Long?,
        seriesId: Long?,
        rarity: Rarity?,
    ): List<UserCardItemDto> {
        if (seriesId != null && !seriesRepository.existsById(seriesId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Series $seriesId not found")
        }
        val rows = userCardRepository.findAllForUserFiltered(
            telegramUserId = user.id!!,
            tournamentId = tournamentId,
            seriesId = seriesId,
            rarity = rarity,
        )
        val userCardIds = rows.mapNotNull { it.id }
        val leaguesByCardId: Map<Long, List<String>> =
            if (seriesId == null || userCardIds.isEmpty()) {
                emptyMap()
            } else {
                fantasyTeamCardRepository.findLeagueCodesBySeriesAndUserCardIds(seriesId, userCardIds)
                    .groupBy(
                        keySelector = { (it[0] as Number).toLong() },
                        valueTransform = { it[1].toString() },
                    )
                    .mapValues { (_, values) -> values.distinct().sorted() }
            }
        val activeListingByCardId: Map<Long, ActiveMarketplaceListingBriefDto> =
            if (userCardIds.isEmpty()) {
                emptyMap()
            } else {
                marketplaceListingRepository
                    .findAllByUserCard_IdInAndStatus(userCardIds, MarketplaceListingStatus.ACTIVE)
                    .associate { ml ->
                        val ucId = ml.userCard!!.id!!
                        ucId to ActiveMarketplaceListingBriefDto(
                            listingId = ml.id!!,
                            price = ml.price,
                        )
                    }
            }
        return rows.map {
            it.toUserCardItemDto(
                imageStorage = imageStorageService,
                cardValueService = cardValueService,
                leaguesInSeries = if (seriesId == null) null else (leaguesByCardId[it.id] ?: emptyList()),
                canJoinMoreLeagues = if (seriesId == null) null else it.usesRemaining > (leaguesByCardId[it.id]?.size ?: 0),
                activeMarketplaceListing = activeListingByCardId[it.id],
            )
        }
    }
}
