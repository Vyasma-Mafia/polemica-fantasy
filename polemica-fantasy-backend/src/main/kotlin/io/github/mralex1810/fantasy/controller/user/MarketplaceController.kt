package io.github.mralex1810.fantasy.controller.user

import io.github.mralex1810.fantasy.dto.user.request.CreateMarketplaceListingRequest
import io.github.mralex1810.fantasy.dto.user.response.BuyCardResultDto
import io.github.mralex1810.fantasy.dto.user.response.MarketplaceFeedDto
import io.github.mralex1810.fantasy.dto.user.response.MarketplaceListingEntryDto
import io.github.mralex1810.fantasy.dto.user.response.MarketplaceListingsPageDto
import io.github.mralex1810.fantasy.entity.Rarity
import io.github.mralex1810.fantasy.entity.TelegramUser
import io.github.mralex1810.fantasy.service.MarketplaceService
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
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
}
