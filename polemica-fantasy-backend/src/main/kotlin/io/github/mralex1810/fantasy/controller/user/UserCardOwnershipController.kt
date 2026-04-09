package io.github.mralex1810.fantasy.controller.user

import io.github.mralex1810.fantasy.dto.user.response.CardOwnershipHistoryEntryDto
import io.github.mralex1810.fantasy.service.UserCardOwnershipService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/user-cards")
class UserCardOwnershipController(
    private val userCardOwnershipService: UserCardOwnershipService,
) {

    @GetMapping("/{userCardId}/ownership-history")
    fun getOwnershipHistory(@PathVariable userCardId: Long): List<CardOwnershipHistoryEntryDto> =
        userCardOwnershipService.listOwnershipHistory(userCardId)
}
