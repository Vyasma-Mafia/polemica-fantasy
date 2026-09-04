package io.github.mralex1810.fantasy.controller.user

import io.github.mralex1810.fantasy.dto.user.response.BuyPackResponseDto
import io.github.mralex1810.fantasy.dto.user.response.SelectPackChoiceRequestDto
import io.github.mralex1810.fantasy.dto.user.response.StorePackItemDto
import io.github.mralex1810.fantasy.entity.TelegramUser
import io.github.mralex1810.fantasy.service.UserStoreService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/store")
class StoreController(
    private val userStoreService: UserStoreService,
) {

    @GetMapping("/packs")
    fun listPacks(@AuthenticationPrincipal user: TelegramUser): List<StorePackItemDto> =
        userStoreService.listStorePacks(user)

    @PostMapping("/packs/{id}/buy")
    fun buyPack(
        @AuthenticationPrincipal user: TelegramUser,
        @PathVariable id: Long,
        @RequestHeader(name = "Idempotency-Key", required = false) idempotencyKey: String?,
    ): BuyPackResponseDto = userStoreService.buyPack(user, id, idempotencyKey)

    @PostMapping("/pack-choices/{choiceId}/select")
    fun selectPackChoice(
        @AuthenticationPrincipal user: TelegramUser,
        @PathVariable choiceId: Long,
        @RequestBody body: SelectPackChoiceRequestDto,
    ): BuyPackResponseDto = userStoreService.selectPackChoice(user, choiceId, body.optionId)
}
