package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.dto.user.request.CreateMarketplaceListingRequest
import io.github.mralex1810.fantasy.dto.user.response.BuyCardResultDto
import io.github.mralex1810.fantasy.dto.user.response.MarketplaceCardAchievementDto
import io.github.mralex1810.fantasy.dto.user.response.MarketplaceFeedDto
import io.github.mralex1810.fantasy.dto.user.response.MarketplaceFeedItemDto
import io.github.mralex1810.fantasy.dto.user.response.MarketplaceAnalyticsDetailDto
import io.github.mralex1810.fantasy.dto.user.response.MarketplaceAnalyticsSummaryDto
import io.github.mralex1810.fantasy.dto.user.response.MarketplaceAnalyticsSummaryItemDto
import io.github.mralex1810.fantasy.dto.user.response.MarketplaceListingCardDto
import io.github.mralex1810.fantasy.dto.user.response.MarketplaceListingEntryDto
import io.github.mralex1810.fantasy.dto.user.response.MarketplaceListingsPageDto
import io.github.mralex1810.fantasy.dto.user.response.MarketplaceRecentSaleDto
import io.github.mralex1810.fantasy.dto.user.response.MarketplaceSellerBriefDto
import io.github.mralex1810.fantasy.entity.CardAcquisitionType
import io.github.mralex1810.fantasy.entity.CardTemplate
import io.github.mralex1810.fantasy.entity.FantikiTransactionReason
import io.github.mralex1810.fantasy.entity.MarketplaceListing
import io.github.mralex1810.fantasy.entity.MarketplaceListingStatus
import io.github.mralex1810.fantasy.entity.Rarity
import io.github.mralex1810.fantasy.entity.TelegramUser
import io.github.mralex1810.fantasy.entity.UserCard
import io.github.mralex1810.fantasy.event.MarketplaceListingCreatedEvent
import io.github.mralex1810.fantasy.event.MarketplaceSaleNotificationEvent
import io.github.mralex1810.fantasy.repository.CardTemplateRepository
import io.github.mralex1810.fantasy.repository.FantasyTeamCardRepository
import io.github.mralex1810.fantasy.repository.MarketplaceListingRepository
import io.github.mralex1810.fantasy.repository.MarketplaceListingSanctionRepository
import io.github.mralex1810.fantasy.repository.TelegramUserRepository
import io.github.mralex1810.fantasy.repository.TournamentPlayerRepository
import io.github.mralex1810.fantasy.repository.UserCardOwnershipHistoryRepository
import io.github.mralex1810.fantasy.repository.UserCardRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

@Service
class MarketplaceService(
    private val marketplaceListingRepository: MarketplaceListingRepository,
    private val userCardOwnershipHistoryRepository: UserCardOwnershipHistoryRepository,
    private val userCardRepository: UserCardRepository,
    private val fantasyTeamCardRepository: FantasyTeamCardRepository,
    private val economyConfigService: EconomyConfigService,
    private val userService: UserService,
    private val cardTemplateRepository: CardTemplateRepository,
    private val userCardOwnershipService: UserCardOwnershipService,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val telegramUserRepository: TelegramUserRepository,
    private val marketplaceListingSanctionRepository: MarketplaceListingSanctionRepository,
    private val tournamentPlayerRepository: TournamentPlayerRepository,
    private val imageStorageService: ImageStorageService,
    private val cardValueService: CardValueService,
) {

    @Transactional
    fun createListing(user: TelegramUser, request: CreateMarketplaceListingRequest): MarketplaceListingEntryDto {
        val me = telegramUserRepository.findById(user.id!!).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")
        }
        checkMarketplaceBan(me)
        val uc = userCardRepository.findByIdAndTelegramUser_IdWithTemplateAchievements(request.userCardId, user.id!!)
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Card not found or not owned")
        if (uc.usesRemaining <= 0) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot sell an expired card")
        }
        if (fantasyTeamCardRepository.countInNonFinalizedSeries(request.userCardId) > 0) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot sell a card in an active team")
        }
        if (marketplaceListingRepository.existsByUserCard_IdAndStatus(request.userCardId, MarketplaceListingStatus.ACTIVE)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Card is already listed")
        }
        val rarity = uc.cardTemplate!!.rarity
        val minPrice = economyConfigService.getMinListingPrice(rarity)
        val maxPrice = economyConfigService.getMaxListingPrice(rarity)
        if (minPrice > maxPrice) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Marketplace min price exceeds max for this rarity (economy config)",
            )
        }
        if (request.price < minPrice) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Price below minimum for this rarity")
        }
        if (request.price > maxPrice) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Price above maximum for this rarity")
        }
        val listing = marketplaceListingRepository.save(
            MarketplaceListing(
                seller = user,
                userCard = uc,
                price = request.price,
                status = MarketplaceListingStatus.ACTIVE,
                createdAt = Instant.now(),
            ),
        )
        val fantasyPlayer = uc.cardTemplate!!.fantasyPlayer!!
        val tournamentIds = tournamentPlayerRepository.findDistinctTournamentIdsByFantasyPlayerId(fantasyPlayer.id!!)
        applicationEventPublisher.publishEvent(
            MarketplaceListingCreatedEvent(
                listingId = listing.id!!,
                sellerId = me.id!!,
                fantasyPlayerId = fantasyPlayer.id!!,
                tournamentIds = tournamentIds,
                cardTemplateId = uc.cardTemplate!!.id!!,
                rarity = uc.cardTemplate!!.rarity,
                price = listing.price,
                playerName = fantasyPlayer.nickname,
            ),
        )
        val tid = uc.cardTemplate!!.id!!
        val tpl = cardTemplateRepository.findAllByIdWithAchievementsLoaded(listOf(tid)).firstOrNull()
            ?: uc.cardTemplate!!
        return toListingEntryDto(
            listing,
            uc,
            tpl,
            viewer = user,
            forSellerOwnListing = true,
            includeSeller = true,
            includeCardValue = true,
            minPackOpensRequired = economyConfigService.getMinPackOpensBeforeMarketplacePurchase(),
            viewerPackOpens = user.packOpensCount,
        )
    }

    @Transactional
    fun updateListingPrice(
        user: TelegramUser,
        listingId: Long,
        newPrice: Long,
    ): MarketplaceListingEntryDto {
        val me = telegramUserRepository.findById(user.id!!).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")
        }
        checkMarketplaceBan(me)
        val listing = marketplaceListingRepository.findByIdAndStatusForUpdate(
            listingId,
            MarketplaceListingStatus.ACTIVE,
        ) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found")
        if (listing.seller!!.id != user.id) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found")
        }
        val uc = listing.userCard!!
        if (uc.usesRemaining <= 0) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot sell an expired card")
        }
        val rarity = uc.cardTemplate!!.rarity
        val minPrice = economyConfigService.getMinListingPrice(rarity)
        val maxPrice = economyConfigService.getMaxListingPrice(rarity)
        if (minPrice > maxPrice) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Marketplace min price exceeds max for this rarity (economy config)",
            )
        }
        if (newPrice < minPrice) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Price below minimum for this rarity")
        }
        if (newPrice > maxPrice) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Price above maximum for this rarity")
        }
        listing.price = newPrice
        marketplaceListingRepository.save(listing)
        val tid = uc.cardTemplate!!.id!!
        val tpl = cardTemplateRepository.findAllByIdWithAchievementsLoaded(listOf(tid)).firstOrNull()
            ?: uc.cardTemplate!!
        return toListingEntryDto(
            listing,
            uc,
            tpl,
            viewer = user,
            forSellerOwnListing = true,
            includeSeller = true,
            includeCardValue = true,
            minPackOpensRequired = economyConfigService.getMinPackOpensBeforeMarketplacePurchase(),
            viewerPackOpens = me.packOpensCount,
        )
    }

    @Transactional
    fun cancelListing(user: TelegramUser, listingId: Long) {
        val listing = marketplaceListingRepository.findById(listingId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found")
        }
        if (listing.seller!!.id != user.id) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found")
        }
        if (listing.status != MarketplaceListingStatus.ACTIVE) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Listing is not active")
        }
        listing.status = MarketplaceListingStatus.CANCELLED
        marketplaceListingRepository.save(listing)
    }

    @Transactional
    fun buyCard(buyer: TelegramUser, listingId: Long): BuyCardResultDto {
        val listing = marketplaceListingRepository.findByIdAndStatusForUpdate(listingId, MarketplaceListingStatus.ACTIVE)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found")
        val seller = listing.seller!!
        val uc = listing.userCard!!
        val buyerId = buyer.id!!
        val sellerId = seller.id!!
        if (buyerId == sellerId) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot buy your own card")
        }
        if (userCardOwnershipHistoryRepository.existsByUserCard_IdAndTelegramUser_Id(uc.id!!, buyerId)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot buy a card you previously owned")
        }
        val minPackOpens = economyConfigService.getMinPackOpensBeforeMarketplacePurchase()
        val buyerRow = telegramUserRepository.findById(buyerId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")
        }
        checkMarketplaceBan(buyerRow)
        val buyerPackOpens = buyerRow.packOpensCount
        if (buyerPackOpens < minPackOpens) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Open at least $minPackOpens pack(s) before buying on the marketplace",
            )
        }
        val price = listing.price
        try {
            userService.deductBalance(buyerId, price, FantikiTransactionReason.MARKETPLACE_PURCHASE)
        } catch (e: ResponseStatusException) {
            if (e.statusCode == HttpStatus.BAD_REQUEST) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Insufficient balance")
            }
            throw e
        }
        val pct = economyConfigService.getMarketplaceCommissionPercent()
        val commission = price * pct / 100
        val sellerReceived = price - commission
        userService.addBalance(sellerId, sellerReceived, FantikiTransactionReason.MARKETPLACE_SALE)

        val rarity = uc.cardTemplate!!.rarity
        uc.telegramUser = buyer
        uc.usesRemaining = economyConfigService.getUsesForRarity(rarity)
        uc.timesRenewed = 0
        userCardRepository.save(uc)

        userCardOwnershipService.recordAcquisition(uc, buyer, CardAcquisitionType.MARKETPLACE_PURCHASE)

        listing.status = MarketplaceListingStatus.SOLD
        listing.soldAt = Instant.now()
        listing.buyer = buyer
        listing.soldCardTemplate = uc.cardTemplate
        listing.soldSkinCode = uc.cardSkin?.code
        marketplaceListingRepository.save(listing)

        val newBalance = userService.getBalance(buyerId)

        applicationEventPublisher.publishEvent(
            MarketplaceSaleNotificationEvent(
                sellerInternalUserId = sellerId,
                sellerTelegramChatId = seller.telegramId,
                playerName = uc.cardTemplate!!.fantasyPlayer!!.nickname,
                rarity = rarity,
                price = price,
                sellerReceived = sellerReceived,
                commission = commission,
            ),
        )

        val tid = uc.cardTemplate!!.id!!
        val tpl = cardTemplateRepository.findAllByIdWithAchievementsLoaded(listOf(tid)).firstOrNull()
            ?: uc.cardTemplate!!
        val cardDto = uc.toUserCardItemDto(tpl, imageStorageService, cardValueService)
        val entry = toListingEntryDto(
            listing,
            uc,
            tpl,
            viewer = buyer,
            forSellerOwnListing = false,
            includeSeller = true,
            includeCardValue = true,
            minPackOpensRequired = economyConfigService.getMinPackOpensBeforeMarketplacePurchase(),
            viewerPackOpens = buyerRow.packOpensCount,
            canBuyOverride = false to null,
        )
        return BuyCardResultDto(
            listing = entry,
            card = cardDto,
            pricePaid = price,
            sellerReceived = sellerReceived,
            commission = commission,
            newBalance = newBalance,
        )
    }

    @Transactional(readOnly = true)
    fun getListings(
        viewer: TelegramUser,
        fantasyPlayerId: Long?,
        tournamentId: Long?,
        seriesId: Long?,
        rarity: Rarity?,
        minPrice: Long?,
        maxPrice: Long?,
        achievementIds: Collection<String>?,
        sortBy: String?,
        page: Int,
        size: Int,
    ): MarketplaceListingsPageDto {
        val sort = parseListingSort(sortBy)
        val pageable = PageRequest.of(page, size.coerceAtLeast(1).coerceAtMost(100), sort)
        val minPackOpens = economyConfigService.getMinPackOpensBeforeMarketplacePurchase()
        val normalizedAchievementIds = normalizeAchievementIdsForFilter(achievementIds)
        val viewerPackOpens =
            telegramUserRepository.findById(viewer.id!!).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")
            }.packOpensCount
        val result = marketplaceListingRepository.findAllActiveFiltered(
            MarketplaceListingStatus.ACTIVE,
            fantasyPlayerId,
            tournamentId,
            seriesId,
            rarity,
            minPrice,
            maxPrice,
            normalizedAchievementIds.isEmpty(),
            normalizedAchievementIds.ifEmpty { listOf("__none__") },
            pageable,
        )
        val templateIds = result.content.map { it.userCard!!.cardTemplate!!.id!! }.distinct()
        val templatesById =
            if (templateIds.isEmpty()) emptyMap()
            else cardTemplateRepository.findAllByIdWithAchievementsLoaded(templateIds).associateBy { it.id!! }
        val content = result.content.map { ml ->
            val uc = ml.userCard!!
            val tid = uc.cardTemplate!!.id!!
            val tpl = templatesById[tid] ?: uc.cardTemplate!!
            toListingEntryDto(
                ml,
                uc,
                tpl,
                viewer = viewer,
                forSellerOwnListing = ml.seller!!.id == viewer.id,
                includeSeller = false,
                includeCardValue = false,
                minPackOpensRequired = minPackOpens,
                viewerPackOpens = viewerPackOpens,
            )
        }
        return MarketplaceListingsPageDto(
            content = content,
            totalElements = result.totalElements,
            totalPages = result.totalPages,
            page = result.number,
            size = result.size,
        )
    }

    @Transactional(readOnly = true)
    fun getMyListings(user: TelegramUser): List<MarketplaceListingEntryDto> {
        val listings = marketplaceListingRepository.findBySeller_IdAndStatusOrderByCreatedAtDesc(
            user.id!!,
            MarketplaceListingStatus.ACTIVE,
        )
        if (listings.isEmpty()) return emptyList()
        val minPackOpens = economyConfigService.getMinPackOpensBeforeMarketplacePurchase()
        val viewerPackOpens =
            telegramUserRepository.findById(user.id!!).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")
            }.packOpensCount
        val templateIds = listings.map { it.userCard!!.cardTemplate!!.id!! }.distinct()
        val templatesById = cardTemplateRepository.findAllByIdWithAchievementsLoaded(templateIds).associateBy { it.id!! }
        return listings.map { ml ->
            val uc = ml.userCard!!
            val tpl = templatesById[uc.cardTemplate!!.id!!] ?: uc.cardTemplate!!
            toListingEntryDto(
                ml,
                uc,
                tpl,
                viewer = user,
                forSellerOwnListing = true,
                includeSeller = true,
                includeCardValue = true,
                minPackOpensRequired = minPackOpens,
                viewerPackOpens = viewerPackOpens,
            )
        }
    }

    @Transactional(readOnly = true)
    fun getFeed(limit: Int): MarketplaceFeedDto {
        val capped = limit.coerceIn(1, 50)
        val pageable = PageRequest.of(0, capped)
        val sold = marketplaceListingRepository.findRecentSold(MarketplaceListingStatus.SOLD, pageable)
        if (sold.isEmpty()) return MarketplaceFeedDto(items = emptyList())

        val templateIds = sold.map { ml ->
            ml.soldCardTemplate?.id ?: ml.userCard!!.cardTemplate!!.id!!
        }.distinct()
        val templatesById = cardTemplateRepository.findAllByIdWithAchievementsLoaded(templateIds).associateBy { it.id!! }
        val listingIds = sold.mapNotNull { it.id }
        val sanctionedIds = if (listingIds.isEmpty()) {
            emptySet()
        } else {
            marketplaceListingSanctionRepository.findListingIdsWithSanctions(listingIds).toSet()
        }

        val items = sold.map { ml ->
            val uc = ml.userCard!!
            val effectiveTemplateId = ml.soldCardTemplate?.id ?: uc.cardTemplate!!.id!!
            val tpl = templatesById[effectiveTemplateId] ?: uc.cardTemplate!!
            val fp = tpl.fantasyPlayer!!
            val seller = ml.seller!!
            val buyer = ml.buyer!!
            MarketplaceFeedItemDto(
                listingId = ml.id!!,
                playerName = fp.nickname,
                rarity = tpl.rarity,
                price = ml.price,
                soldAt = ml.soldAt!!,
                sellerDisplayName = seller.publicDisplayName(),
                buyerDisplayName = buyer.publicDisplayName(),
                sanctioned = ml.id!! in sanctionedIds,
                card = toMarketplaceCardDto(uc, tpl, includeValue = true, skinCodeOverride = ml.soldSkinCode),
            )
        }
        return MarketplaceFeedDto(items = items)
    }

    private fun parseListingSort(raw: String?): Sort {
        return when (raw?.lowercase()?.trim() ?: "created_at_desc") {
            "price_asc" -> Sort.by(Sort.Direction.ASC, "price")
            "price_desc" -> Sort.by(Sort.Direction.DESC, "price")
            "created_at_desc" -> Sort.by(Sort.Direction.DESC, "createdAt")
            else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid sortBy")
        }
    }

    private fun normalizeAchievementIdsForFilter(ids: Collection<String>?): List<String> =
        ids.orEmpty()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

    private fun toListingEntryDto(
        listing: MarketplaceListing,
        uc: UserCard,
        template: CardTemplate,
        viewer: TelegramUser,
        forSellerOwnListing: Boolean,
        includeSeller: Boolean,
        includeCardValue: Boolean,
        minPackOpensRequired: Int,
        viewerPackOpens: Int,
        canBuyOverride: Pair<Boolean, String?>? = null,
    ): MarketplaceListingEntryDto {
        val seller = listing.seller!!
        val (canBuy, reason) = canBuyOverride
            ?: computeCanBuy(
                listing,
                uc,
                viewer,
                forSellerOwnListing,
                minPackOpensRequired,
                viewerPackOpens,
            )
        return MarketplaceListingEntryDto(
            listingId = listing.id!!,
            price = listing.price,
            createdAt = listing.createdAt,
            card = toMarketplaceCardDto(uc, template, includeCardValue),
            seller = if (includeSeller) MarketplaceSellerBriefDto(displayName = seller.publicDisplayName()) else null,
            canBuy = canBuy,
            canBuyReason = reason,
        )
    }

    private fun computeCanBuy(
        listing: MarketplaceListing,
        uc: UserCard,
        viewer: TelegramUser,
        forSellerOwnListing: Boolean,
        minPackOpensRequired: Int,
        viewerPackOpens: Int,
    ): Pair<Boolean, String?> {
        if (forSellerOwnListing || listing.seller!!.id == viewer.id) {
            return false to "This is your listing"
        }
        val cardId = uc.id!!
        val viewerId = viewer.id!!
        if (userCardOwnershipHistoryRepository.existsByUserCard_IdAndTelegramUser_Id(cardId, viewerId)) {
            return false to "You have already owned this card"
        }
        if (viewerPackOpens < minPackOpensRequired) {
            return false to "Open at least $minPackOpensRequired pack(s) before buying on the marketplace"
        }
        val balance = userService.getBalance(viewerId)
        if (balance < listing.price) {
            return false to "Insufficient balance"
        }
        return true to null
    }

    @Transactional(readOnly = true)
    fun getAnalyticsSummary(fantasyPlayerIds: Collection<Long>): MarketplaceAnalyticsSummaryDto {
        if (fantasyPlayerIds.isEmpty()) {
            return MarketplaceAnalyticsSummaryDto(items = emptyList())
        }
        val rows = marketplaceListingRepository.findActiveListingSummaryByFantasyPlayerIds(fantasyPlayerIds)
        val items = rows.map { row ->
            MarketplaceAnalyticsSummaryItemDto(
                fantasyPlayerId = (row[0] as Number).toLong(),
                rarity = Rarity.valueOf(row[1] as String),
                activeCount = (row[2] as Number).toLong(),
                minActivePrice = (row[3] as? Number)?.toLong(),
            )
        }
        return MarketplaceAnalyticsSummaryDto(items = items)
    }

    @Transactional(readOnly = true)
    fun getAnalyticsDetail(fantasyPlayerId: Long, rarity: Rarity): MarketplaceAnalyticsDetailDto {
        val activeStatsRaw = marketplaceListingRepository.findActiveListingStatsForPlayerAndRarity(
            MarketplaceListingStatus.ACTIVE, fantasyPlayerId, rarity,
        )
        val activeStats = unwrapAnalyticsStatsRow(activeStatsRaw)
        val activeCount = (activeStats.getOrNull(0) as? Number)?.toLong() ?: 0L
        val activeMinPrice = (activeStats.getOrNull(1) as? Number)?.toLong()
        val activeMaxPrice = (activeStats.getOrNull(2) as? Number)?.toLong()

        val recentSold = marketplaceListingRepository.findRecentSoldByPlayerAndRarity(
            MarketplaceListingStatus.SOLD, fantasyPlayerId, rarity,
            PageRequest.of(0, 10),
        )
        val recentSales = recentSold.map { ml ->
            MarketplaceRecentSaleDto(price = ml.price, soldAt = ml.soldAt!!)
        }
        val avgSalePrice = if (recentSales.isEmpty()) null
        else recentSales.sumOf { it.price } / recentSales.size

        return MarketplaceAnalyticsDetailDto(
            fantasyPlayerId = fantasyPlayerId,
            rarity = rarity,
            activeCount = activeCount,
            activeMinPrice = activeMinPrice,
            activeMaxPrice = activeMaxPrice,
            recentSales = recentSales,
            avgSalePrice = avgSalePrice,
        )
    }

    private fun unwrapAnalyticsStatsRow(raw: Array<Any>): Array<*> {
        val first = raw.firstOrNull()
        return if (raw.size == 1 && first is Array<*>) first else raw
    }

    private fun toMarketplaceCardDto(
        uc: UserCard,
        template: CardTemplate,
        includeValue: Boolean = true,
        skinCodeOverride: String? = null,
    ): MarketplaceListingCardDto {
        val fp = template.fantasyPlayer!!
        val achievements = template.achievements.distinctBy { it.achievement!!.id }.map { a ->
            val def = a.achievement!!
            MarketplaceCardAchievementDto(
                achievementId = def.id,
                name = def.name,
                bonusPoints = a.bonusPoints ?: def.bonusPoints,
            )
        }
        return MarketplaceListingCardDto(
            userCardId = uc.id!!,
            fantasyPlayerId = fp.id!!,
            playerName = fp.nickname,
            playerPhotoUrl = imageStorageService.publicObjectUrl(fp.photoUrl),
            rarity = template.rarity,
            achievements = achievements,
            value = if (includeValue) cardValueService.calculateValue(template) else null,
            skinCode = skinCodeOverride ?: uc.cardSkin?.code,
        )
    }

    private fun checkMarketplaceBan(user: TelegramUser) {
        if (user.marketplaceBanned) {
            throw ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Your marketplace access is suspended",
            )
        }
        val bannedUntil = user.marketplaceBannedUntil
        if (bannedUntil != null && bannedUntil.isAfter(Instant.now())) {
            throw ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Your marketplace access is suspended until $bannedUntil",
            )
        }
    }
}
