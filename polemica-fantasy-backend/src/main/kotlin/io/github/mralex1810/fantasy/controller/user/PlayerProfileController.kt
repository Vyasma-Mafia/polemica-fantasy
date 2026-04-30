package io.github.mralex1810.fantasy.controller.user

import io.github.mralex1810.fantasy.dto.user.response.PlayerProfileDto
import io.github.mralex1810.fantasy.entity.TelegramUser
import io.github.mralex1810.fantasy.service.PlayerProfileService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/players")
class PlayerProfileController(
    private val playerProfileService: PlayerProfileService,
) {
    @GetMapping("/{telegramId}/profile")
    fun getProfile(
        @AuthenticationPrincipal user: TelegramUser,
        @PathVariable telegramId: Long,
    ): PlayerProfileDto = playerProfileService.getProfile(telegramId)
}
