package io.github.mralex1810.fantasy.controller.user

import io.github.mralex1810.fantasy.dto.user.request.UpdateProfileCustomizationRequest
import io.github.mralex1810.fantasy.dto.user.response.ProfileCustomizationDto
import io.github.mralex1810.fantasy.entity.TelegramUser
import io.github.mralex1810.fantasy.service.achievement.ProfileCustomizationService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/me/profile-customization")
class ProfileCustomizationController(
    private val profileCustomizationService: ProfileCustomizationService,
) {
    @GetMapping
    fun get(@AuthenticationPrincipal user: TelegramUser): ProfileCustomizationDto =
        profileCustomizationService.getCustomization(user)

    @PutMapping
    fun update(
        @AuthenticationPrincipal user: TelegramUser,
        @RequestBody request: UpdateProfileCustomizationRequest,
    ): ProfileCustomizationDto = profileCustomizationService.updateCustomization(user, request)
}
