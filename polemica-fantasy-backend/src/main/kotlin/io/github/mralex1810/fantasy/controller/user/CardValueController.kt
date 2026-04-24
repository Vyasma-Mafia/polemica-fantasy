package io.github.mralex1810.fantasy.controller.user

import io.github.mralex1810.fantasy.dto.user.response.CardValueInfoDto
import io.github.mralex1810.fantasy.service.EconomyConfigService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/card-value")
class CardValueController(
    private val economyConfigService: EconomyConfigService,
) {
    @GetMapping("/info")
    fun info(): CardValueInfoDto = economyConfigService.buildCardValueInfo()
}
