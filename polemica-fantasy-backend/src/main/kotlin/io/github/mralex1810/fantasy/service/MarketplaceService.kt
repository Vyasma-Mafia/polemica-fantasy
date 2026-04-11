package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.dto.user.request.CreateMarketplaceListingRequest
import io.github.mralex1810.fantasy.dto.user.response.BuyCardResultDto
import io.github.mralex1810.fantasy.dto.user.response.MarketplaceCardAchievementDto
import io.github.mralex1810.fantasy.dto.user.response.MarketplaceFeedDto
import io.github.mralex1810.fantasy.dto.user.response.MarketplaceFeedItemDto
import io.github.mralex1810.fantasy.dto.user.response.MarketplaceListingCardDto
import io.github.mralex1810.fantasy.dto.user.response.MarketplaceListingEntryDto
import io.github.mralex1810.fantasy.dto.user.response.MarketplaceListingsPageDto
import io.github.mralex1810.fantasy.dto.user.response.MarketplaceSellerBriefDto
import io.github.mralex1810.fantasy.entity.CardAcquisitionType
import io.github.mralex1810.fantasy.entity.CardTemplate
import io.github.mralex1810.fantasy.entity.FantikiTransactionReason
import io.github.mralex1810.fantasy.entity.MarketplaceListing
import io.github.mralex1810.fantasy.entity.MarketplaceListingStatus
import io.github.mralex1810.fantasy.entity.Rarity
import io.github.mralex1810.fantasy.entity.TelegramUser
import io.github.mralex1810.fantasy.entity.UserCard
import io.github.mralex1810.fantasy.event.MarketplaceSaleNotificationEvent
import io.github.mralex1810.fantasy.repository.CardTemplateRepository
import io.github.mralex1810.fantasy.repository.FantasyTeamCardRepository
import io.github.mralex1810.fantasy.repository.MarketplaceListingRepository
import io.github.mralex1810.fantasy.repository.TelegramUserRepository
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
) {

    @Transactional
    fun createListing(user: TelegramUser, request: CreateMarketplaceListingRequest): MarketplaceListingEntryDto {
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
        val tid = uc.cardTemplate!!.id!!
        val tpl = cardTemplateRepository.findAllByIdWithAchievementsLoaded(listOf(tid)).firstOrNull()
            ?: uc.cardTemplate!!
        return toListingEntryDto(
            listing,
            uc,
            tpl,
            viewer = user,
            forSellerOwnListing = true,
            minPackOpensRequired = economyConfigService.getMinPackOpensBeforeMarketplacePurchase(),
            viewerPackOpens = user.packOpensCount,
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
        val buyerPackOpens =
            telegramUserRepository.findById(buyerId).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")
            }.packOpensCount
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
        val cardDto = uc.toUserCardItemDto(tpl)
        val entry = toListingEntryDto(
            listing,
            uc,
            tpl,
            viewer = buyer,
            forSellerOwnListing = false,
            minPackOpensRequired = economyConfigService.getMinPackOpensBeforeMarketplacePurchase(),
            viewerPackOpens = buyer.packOpensCount,
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
        sortBy: String?,
        page: Int,
        size: Int,
    ): MarketplaceListingsPageDto {
        val sort = parseListingSort(sortBy)
        val pageable = PageRequest.of(page, size.coerceAtLeast(1).coerceAtMost(100), sort)
        val minPackOpens = economyConfigService.getMinPackOpensBeforeMarketplacePurchase()
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

        val items = sold.map { ml ->
            val uc = ml.userCard!!
            val effectiveTemplateId = ml.soldCardTemplate?.id ?: uc.cardTemplate!!.id!!
            val tpl = templatesById[effectiveTemplateId] ?: uc.cardTemplate!!
            val fp = tpl.fantasyPlayer!!
            val seller = ml.seller!!
            val buyer = ml.buyer!!
            MarketplaceFeedItemDto(
                playerName = fp.nickname,
                rarity = tpl.rarity,
                price = ml.price,
                soldAt = ml.soldAt!!,
                sellerDisplayName = seller.publicDisplayName(),
                buyerDisplayName = buyer.publicDisplayName(),
                card = toMarketplaceCardDto(uc, tpl),
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

    private fun toListingEntryDto(
        listing: MarketplaceListing,
        uc: UserCard,
        template: CardTemplate,
        viewer: TelegramUser,
        forSellerOwnListing: Boolean,
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
            card = toMarketplaceCardDto(uc, template),
            seller = MarketplaceSellerBriefDto(displayName = seller.publicDisplayName()),
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

    private fun toMarketplaceCardDto(
        uc: UserCard,
        template: CardTemplate,
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
            playerPhotoUrl = fp.photoUrl,
            rarity = template.rarity,
            achievements = achievements,
        )
    }
}
