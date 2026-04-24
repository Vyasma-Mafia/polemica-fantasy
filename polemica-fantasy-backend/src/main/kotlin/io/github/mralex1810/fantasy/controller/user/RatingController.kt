package io.github.mralex1810.fantasy.controller.user

import io.github.mralex1810.fantasy.dto.user.response.GlobalRatingDto
import io.github.mralex1810.fantasy.entity.TelegramUser
import io.github.mralex1810.fantasy.service.GlobalRatingService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/rating")
class RatingController(
    private val globalRatingService: GlobalRatingService,
) {
    @GetMapping
    fun getRating(@AuthenticationPrincipal user: TelegramUser): GlobalRatingDto = globalRatingService.getRating(user)
}
