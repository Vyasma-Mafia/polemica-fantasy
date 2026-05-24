package io.github.mralex1810.fantasy.controller.user

import io.github.mralex1810.fantasy.dto.user.request.ProductEventRequest
import io.github.mralex1810.fantasy.entity.TelegramUser
import io.github.mralex1810.fantasy.service.ProductEventService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/product-events")
class ProductEventController(
    private val productEventService: ProductEventService,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun record(
        @AuthenticationPrincipal user: TelegramUser,
        @Valid @RequestBody request: ProductEventRequest,
    ) {
        productEventService.record(user.id!!, request)
    }
}
