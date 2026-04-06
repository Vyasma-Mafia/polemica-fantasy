package io.github.mralex1810.fantasy.controller.user

import io.github.mralex1810.fantasy.dto.user.request.LegendaryUpgradeRequest
import io.github.mralex1810.fantasy.dto.user.response.LegendaryUpgradeInfoDto
import io.github.mralex1810.fantasy.dto.user.response.UserCardItemDto
import io.github.mralex1810.fantasy.entity.TelegramUser
import io.github.mralex1810.fantasy.service.LegendaryUpgradeService
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/legendary-upgrade")
class LegendaryUpgradeController(
    private val legendaryUpgradeService: LegendaryUpgradeService,
) {

    @GetMapping("/info")
    fun info(@AuthenticationPrincipal user: TelegramUser): LegendaryUpgradeInfoDto =
        legendaryUpgradeService.getInfo(user)

    @PostMapping
    fun upgrade(
        @AuthenticationPrincipal user: TelegramUser,
        @Valid @RequestBody body: LegendaryUpgradeRequest,
    ): UserCardItemDto = legendaryUpgradeService.upgrade(user, body.userCardId, body.achievementId)
}
