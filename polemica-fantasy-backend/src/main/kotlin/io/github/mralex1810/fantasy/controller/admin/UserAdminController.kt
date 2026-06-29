package io.github.mralex1810.fantasy.controller.admin

import io.github.mralex1810.fantasy.dto.admin.request.GiveFantikiRequest
import io.github.mralex1810.fantasy.dto.admin.request.TakeFantikiRequest
import io.github.mralex1810.fantasy.dto.admin.response.AdminUserListItemDto
import io.github.mralex1810.fantasy.dto.admin.response.PagedFantikiTransactionsDto
import io.github.mralex1810.fantasy.dto.user.response.UserProfileDto
import io.github.mralex1810.fantasy.entity.FantikiTransactionReason
import io.github.mralex1810.fantasy.service.AdminUserListService
import io.github.mralex1810.fantasy.service.UserService
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/admin/users")
class UserAdminController(
    private val userService: UserService,
    private val adminUserListService: AdminUserListService,
) {

    @GetMapping
    fun listUsers(
        @RequestParam(required = false) tournamentId: Long?,
        @RequestParam(required = false) seriesId: Long?,
        @RequestParam(required = false) q: String?,
    ): List<AdminUserListItemDto> = adminUserListService.listForAdmin(tournamentId, seriesId, q)

    @PostMapping("/{telegramUserId}/give-fantiki")
    fun giveFantiki(
        @PathVariable telegramUserId: Long,
        @Valid @RequestBody body: GiveFantikiRequest,
    ): UserProfileDto = userService.grantFantikiByTelegramId(telegramUserId, body.amount, body.adminReason)

    @PostMapping("/{telegramUserId}/take-fantiki")
    fun takeFantiki(
        @PathVariable telegramUserId: Long,
        @Valid @RequestBody body: TakeFantikiRequest,
    ): UserProfileDto = userService.confiscateFantikiByTelegramId(telegramUserId, body.amount, body.adminReason)

    @GetMapping("/{telegramUserId}/fantiki-adjustments")
    fun listFantikiAdjustments(
        @PathVariable telegramUserId: Long,
        @PageableDefault(size = 20, sort = ["createdAt"], direction = Sort.Direction.DESC) pageable: Pageable,
    ): PagedFantikiTransactionsDto = userService.listFantikiTransactionsForAdmin(
        telegramUserId = telegramUserId,
        reason = null,
        pageable = pageable,
    )

    @GetMapping("/fantiki-transactions")
    fun listFantikiTransactions(
        @RequestParam(required = false) telegramUserId: Long?,
        @RequestParam(required = false) reason: FantikiTransactionReason?,
        @PageableDefault(size = 20, sort = ["createdAt"], direction = Sort.Direction.DESC) pageable: Pageable,
    ): PagedFantikiTransactionsDto = userService.listFantikiTransactionsForAdmin(
        telegramUserId = telegramUserId,
        reason = reason,
        pageable = pageable,
    )
}
