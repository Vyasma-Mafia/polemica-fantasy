package io.github.mralex1810.fantasy.controller.admin

import io.github.mralex1810.fantasy.dto.admin.request.GiveFantikiRequest
import io.github.mralex1810.fantasy.dto.admin.response.AdminUserListItemDto
import io.github.mralex1810.fantasy.dto.user.response.UserProfileDto
import io.github.mralex1810.fantasy.service.AdminUserListService
import io.github.mralex1810.fantasy.service.UserService
import jakarta.validation.Valid
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
    ): List<AdminUserListItemDto> = adminUserListService.listForAdmin(tournamentId, seriesId)

    @PostMapping("/{telegramUserId}/give-fantiki")
    fun giveFantiki(
        @PathVariable telegramUserId: Long,
        @Valid @RequestBody body: GiveFantikiRequest,
    ): UserProfileDto = userService.grantFantikiByTelegramId(telegramUserId, body.amount)
}
