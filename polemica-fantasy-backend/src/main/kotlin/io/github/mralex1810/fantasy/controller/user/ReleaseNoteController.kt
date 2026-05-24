package io.github.mralex1810.fantasy.controller.user

import io.github.mralex1810.fantasy.entity.TelegramUser
import io.github.mralex1810.fantasy.service.ReleaseNoteService
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/release-notes")
class ReleaseNoteController(
    private val releaseNoteService: ReleaseNoteService,
) {
    @GetMapping
    fun list(
        @AuthenticationPrincipal user: TelegramUser,
        @RequestParam(required = false) appVersion: String?,
    ) = releaseNoteService.listForUser(user.id!!, appVersion)

    @PostMapping("/{id}/seen")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun markSeen(
        @AuthenticationPrincipal user: TelegramUser,
        @PathVariable id: Long,
    ) {
        releaseNoteService.markSeen(user.id!!, id)
    }
}
