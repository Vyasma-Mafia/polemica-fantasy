package io.github.mralex1810.fantasy.controller.user

import io.github.mralex1810.fantasy.dto.user.request.CreateMarketplaceWatchRequest
import io.github.mralex1810.fantasy.dto.user.request.UpdateNotificationSettingsRequest
import io.github.mralex1810.fantasy.dto.user.request.UpdateTournamentSubscriptionsRequest
import io.github.mralex1810.fantasy.entity.TelegramUser
import io.github.mralex1810.fantasy.service.MarketplaceWatchService
import io.github.mralex1810.fantasy.service.NotificationSettingsService
import io.github.mralex1810.fantasy.service.TournamentSubscriptionService
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/settings")
class NotificationSettingsController(
    private val notificationSettingsService: NotificationSettingsService,
    private val tournamentSubscriptionService: TournamentSubscriptionService,
    private val marketplaceWatchService: MarketplaceWatchService,
) {
    @GetMapping("/notifications")
    fun getNotifications(
        @AuthenticationPrincipal user: TelegramUser,
    ) = notificationSettingsService.getSettings(user.id!!)

    @PutMapping("/notifications")
    fun updateNotifications(
        @AuthenticationPrincipal user: TelegramUser,
        @RequestBody request: UpdateNotificationSettingsRequest,
    ) = notificationSettingsService.updateSettings(user.id!!, request)

    @GetMapping("/tournament-subscriptions")
    fun getTournamentSubscriptions(
        @AuthenticationPrincipal user: TelegramUser,
    ) = tournamentSubscriptionService.getSubscriptions(user.id!!)

    @PutMapping("/tournament-subscriptions")
    fun updateTournamentSubscriptions(
        @AuthenticationPrincipal user: TelegramUser,
        @RequestBody request: UpdateTournamentSubscriptionsRequest,
    ) = tournamentSubscriptionService.updateSubscriptions(user.id!!, request)

    @GetMapping("/marketplace-watches")
    fun getMarketplaceWatches(
        @AuthenticationPrincipal user: TelegramUser,
    ) = marketplaceWatchService.getWatches(user.id!!)

    @PostMapping("/marketplace-watches")
    fun createMarketplaceWatch(
        @AuthenticationPrincipal user: TelegramUser,
        @RequestBody request: CreateMarketplaceWatchRequest,
    ) = marketplaceWatchService.createWatch(user.id!!, request)

    @DeleteMapping("/marketplace-watches/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteMarketplaceWatch(
        @AuthenticationPrincipal user: TelegramUser,
        @PathVariable id: Long,
    ) {
        marketplaceWatchService.deleteWatch(user.id!!, id)
    }
}
