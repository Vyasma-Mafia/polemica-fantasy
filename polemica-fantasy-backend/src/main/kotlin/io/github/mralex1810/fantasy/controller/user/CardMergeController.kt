package io.github.mralex1810.fantasy.controller.user

import io.github.mralex1810.fantasy.dto.user.request.CardMergeConfirmRequest
import io.github.mralex1810.fantasy.dto.user.request.CardMergePreviewRequest
import io.github.mralex1810.fantasy.dto.user.response.CardMergeConfirmResponseDto
import io.github.mralex1810.fantasy.dto.user.response.CardMergeOptionsDto
import io.github.mralex1810.fantasy.dto.user.response.CardMergePreviewResponseDto
import io.github.mralex1810.fantasy.entity.TelegramUser
import io.github.mralex1810.fantasy.service.CardMergeService
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/cards/merge")
class CardMergeController(
    private val cardMergeService: CardMergeService,
) {

    @GetMapping("/options")
    fun options(@AuthenticationPrincipal user: TelegramUser): CardMergeOptionsDto =
        cardMergeService.getOptions(user)

    @PostMapping("/preview")
    fun preview(
        @AuthenticationPrincipal user: TelegramUser,
        @Valid @RequestBody body: CardMergePreviewRequest,
    ): CardMergePreviewResponseDto = cardMergeService.preview(user, body)

    @PostMapping("/confirm")
    fun confirm(
        @AuthenticationPrincipal user: TelegramUser,
        @Valid @RequestBody body: CardMergeConfirmRequest,
    ): CardMergeConfirmResponseDto = cardMergeService.confirm(user, body)
}
