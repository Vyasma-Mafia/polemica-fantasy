package io.github.mralex1810.fantasy.controller.user

import io.github.mralex1810.fantasy.entity.TelegramUser
import io.github.mralex1810.fantasy.dto.periodicrating.PeriodicRatingRewardDraftRequest
import io.github.mralex1810.fantasy.dto.periodicrating.PeriodicRatingRewardVersionRequest
import io.github.mralex1810.fantasy.service.PeriodicRatingService
import io.github.mralex1810.fantasy.service.PeriodicRatingRewardService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/periodic-ratings")
class PeriodicRatingController(
    private val service: PeriodicRatingService,
    private val rewards: PeriodicRatingRewardService,
) {
    @GetMapping("/current") fun current() = service.current()
    @GetMapping("/periods") fun periods() = service.listVisiblePeriods()
    @GetMapping("/periods/{id}/leaderboard")
    fun leaderboard(@PathVariable id: Long, @AuthenticationPrincipal user: TelegramUser) = service.leaderboard(id, user)
    @GetMapping("/periods/{id}/me")
    fun me(@PathVariable id: Long, @AuthenticationPrincipal user: TelegramUser) = service.me(id, user)

    @GetMapping("/rewards")
    fun rewards(@AuthenticationPrincipal user: TelegramUser) = rewards.listForUser(user)

    @GetMapping("/rewards/{id}")
    fun reward(@PathVariable id: Long, @AuthenticationPrincipal user: TelegramUser) = rewards.getForUser(id, user)

    @GetMapping("/rewards/{id}/players")
    fun players(
        @PathVariable id: Long,
        @AuthenticationPrincipal user: TelegramUser,
        @RequestParam(required = false) q: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ) = rewards.searchPlayers(id, user, q, page, size)

    @PutMapping("/rewards/{id}/draft")
    fun draft(
        @PathVariable id: Long,
        @AuthenticationPrincipal user: TelegramUser,
        @RequestBody request: PeriodicRatingRewardDraftRequest,
    ) = rewards.saveDraft(id, user, request)

    @PostMapping("/rewards/{id}/submit")
    fun submit(
        @PathVariable id: Long,
        @AuthenticationPrincipal user: TelegramUser,
        @RequestBody request: PeriodicRatingRewardVersionRequest,
    ) = rewards.submit(id, user, request.version)
}
