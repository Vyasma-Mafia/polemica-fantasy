package io.github.mralex1810.fantasy.controller.user

import io.github.mralex1810.fantasy.dto.user.request.CreateMarketplaceListingRequest
import io.github.mralex1810.fantasy.dto.user.request.UpdateMarketplaceListingPriceRequest
import io.github.mralex1810.fantasy.dto.user.response.BuyCardResultDto
import io.github.mralex1810.fantasy.dto.user.response.ComplainResultDto
import io.github.mralex1810.fantasy.dto.user.response.MarketplaceAnalyticsDetailDto
import io.github.mralex1810.fantasy.dto.user.response.MarketplaceAnalyticsSummaryDto
import io.github.mralex1810.fantasy.dto.user.response.MarketplaceFeedDto
import io.github.mralex1810.fantasy.dto.user.response.MarketplaceListingEntryDto
import io.github.mralex1810.fantasy.dto.user.response.MarketplaceListingsPageDto
import io.github.mralex1810.fantasy.dto.user.response.MarketplaceTransactionDetailDto
import io.github.mralex1810.fantasy.entity.Rarity
import io.github.mralex1810.fantasy.entity.TelegramUser
import io.github.mralex1810.fantasy.service.MarketplaceComplaintService
import io.github.mralex1810.fantasy.service.MarketplaceService
import io.github.mralex1810.fantasy.service.MarketplaceTransactionService
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/marketplace")
class MarketplaceController(
    private val marketplaceService: MarketplaceService,
    private val marketplaceTransactionService: MarketplaceTransactionService,
    private val marketplaceComplaintService: MarketplaceComplaintService,
) {

    @GetMapping("/listings")
    fun getListings(
        @AuthenticationPrincipal user: TelegramUser,
        @RequestParam(required = false) fantasyPlayerId: Long?,
        @RequestParam(required = false) tournamentId: Long?,
        @RequestParam(required = false) seriesId: Long?,
        @RequestParam(required = false) rarity: Rarity?,
        @RequestParam(required = false) minPrice: Long?,
        @RequestParam(required = false) maxPrice: Long?,
        @RequestParam(required = false) sortBy: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): MarketplaceListingsPageDto =
        marketplaceService.getListings(
            viewer = user,
            fantasyPlayerId = fantasyPlayerId,
            tournamentId = tournamentId,
            seriesId = seriesId,
            rarity = rarity,
            minPrice = minPrice,
            maxPrice = maxPrice,
            sortBy = sortBy,
            page = page,
            size = size,
        )

    @GetMapping("/my-listings")
    fun getMyListings(@AuthenticationPrincipal user: TelegramUser): List<MarketplaceListingEntryDto> =
        marketplaceService.getMyListings(user)

    @PostMapping("/listings")
    fun createListing(
        @AuthenticationPrincipal user: TelegramUser,
        @RequestBody body: CreateMarketplaceListingRequest,
    ): MarketplaceListingEntryDto = marketplaceService.createListing(user, body)

    @PatchMapping("/listings/{id}")
    fun updateListingPrice(
        @AuthenticationPrincipal user: TelegramUser,
        @PathVariable id: Long,
        @RequestBody body: UpdateMarketplaceListingPriceRequest,
    ): MarketplaceListingEntryDto = marketplaceService.updateListingPrice(user, id, body.price)

    @DeleteMapping("/listings/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun cancelListing(
        @AuthenticationPrincipal user: TelegramUser,
        @PathVariable id: Long,
    ) {
        marketplaceService.cancelListing(user, id)
    }

    @PostMapping("/listings/{id}/buy")
    fun buyCard(
        @AuthenticationPrincipal user: TelegramUser,
        @PathVariable id: Long,
    ): BuyCardResultDto = marketplaceService.buyCard(user, id)

    @GetMapping("/feed")
    fun getFeed(@RequestParam(defaultValue = "20") limit: Int): MarketplaceFeedDto =
        marketplaceService.getFeed(limit)

    @GetMapping("/transactions/{listingId}")
    fun getTransactionDetail(
        @AuthenticationPrincipal user: TelegramUser,
        @PathVariable listingId: Long,
    ): MarketplaceTransactionDetailDto =
        marketplaceTransactionService.getTransactionDetail(user, listingId)

    @PostMapping("/transactions/{listingId}/complain")
    @ResponseStatus(HttpStatus.CREATED)
    fun complainTransaction(
        @AuthenticationPrincipal user: TelegramUser,
        @PathVariable listingId: Long,
    ): ComplainResultDto =
        marketplaceComplaintService.complain(user, listingId)

    @GetMapping("/analytics/summary")
    fun getAnalyticsSummary(
        @AuthenticationPrincipal user: TelegramUser,
        @RequestParam fantasyPlayerIds: List<Long>,
    ): MarketplaceAnalyticsSummaryDto =
        marketplaceService.getAnalyticsSummary(fantasyPlayerIds)

    @GetMapping("/analytics/detail")
    fun getAnalyticsDetail(
        @AuthenticationPrincipal user: TelegramUser,
        @RequestParam fantasyPlayerId: Long,
        @RequestParam rarity: Rarity,
    ): MarketplaceAnalyticsDetailDto =
        marketplaceService.getAnalyticsDetail(fantasyPlayerId, rarity)
}
