package io.github.mralex1810.fantasy.controller.user

import io.github.mralex1810.fantasy.dto.user.request.AchievementCardChoiceRequest
import io.github.mralex1810.fantasy.dto.user.response.AchievementCatalogDto
import io.github.mralex1810.fantasy.dto.user.response.AchievementClaimResultDto
import io.github.mralex1810.fantasy.entity.TelegramUser
import io.github.mralex1810.fantasy.service.achievement.AchievementCatalogService
import io.github.mralex1810.fantasy.service.achievement.AchievementClaimService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/achievements")
class AchievementController(
    private val achievementCatalogService: AchievementCatalogService,
    private val achievementClaimService: AchievementClaimService,
) {
    @GetMapping
    fun catalog(@AuthenticationPrincipal user: TelegramUser): AchievementCatalogDto =
        achievementCatalogService.catalogFor(user)

    @PostMapping("/{code}/claim")
    fun claim(
        @AuthenticationPrincipal user: TelegramUser,
        @PathVariable code: String,
    ): AchievementClaimResultDto = achievementClaimService.claim(user, code)

    @PostMapping("/{code}/choices/{rewardId}/select")
    fun selectChoice(
        @AuthenticationPrincipal user: TelegramUser,
        @PathVariable code: String,
        @PathVariable rewardId: Long,
        @RequestBody request: AchievementCardChoiceRequest,
    ): AchievementClaimResultDto = achievementClaimService.selectChoice(user, code, rewardId, request.optionIds)
}
