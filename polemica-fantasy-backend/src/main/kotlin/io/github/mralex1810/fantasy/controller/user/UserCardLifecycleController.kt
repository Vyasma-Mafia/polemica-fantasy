package io.github.mralex1810.fantasy.controller.user

import io.github.mralex1810.fantasy.dto.user.response.EconomyInfoDto
import io.github.mralex1810.fantasy.dto.user.response.RecycleResultDto
import io.github.mralex1810.fantasy.dto.user.response.RenewResultDto
import io.github.mralex1810.fantasy.entity.TelegramUser
import io.github.mralex1810.fantasy.service.CardLifecycleService
import io.github.mralex1810.fantasy.service.EconomyConfigService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/me")
class UserCardLifecycleController(
    private val cardLifecycleService: CardLifecycleService,
    private val economyConfigService: EconomyConfigService,
) {

    @PostMapping("/cards/{id}/recycle")
    fun recycle(
        @AuthenticationPrincipal user: TelegramUser,
        @PathVariable id: Long,
    ): RecycleResultDto = cardLifecycleService.recycleCard(user, id)

    @PostMapping("/cards/{id}/renew")
    fun renew(
        @AuthenticationPrincipal user: TelegramUser,
        @PathVariable id: Long,
    ): RenewResultDto = cardLifecycleService.renewCard(user, id)

    @GetMapping("/economy-info")
    fun economyInfo(): EconomyInfoDto = economyConfigService.buildEconomyInfo()
}
