package io.github.mralex1810.fantasy.controller.admin

import io.github.mralex1810.fantasy.dto.admin.response.AdminCardMergeDetailDto
import io.github.mralex1810.fantasy.dto.admin.response.AdminCardMergePageDto
import io.github.mralex1810.fantasy.service.CardMergeAdminService
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/admin/card-merges")
class CardMergeAdminController(
    private val cardMergeAdminService: CardMergeAdminService,
) {

    @GetMapping
    fun list(
        @RequestParam(required = false) telegramUserId: Long?,
        @RequestParam(required = false) resultUserCardId: Long?,
        @PageableDefault(size = 20) pageable: Pageable,
    ): AdminCardMergePageDto =
        cardMergeAdminService.list(telegramUserId, resultUserCardId, pageable)

    @GetMapping("/{id}")
    fun detail(@PathVariable id: Long): AdminCardMergeDetailDto =
        cardMergeAdminService.detail(id)
}
