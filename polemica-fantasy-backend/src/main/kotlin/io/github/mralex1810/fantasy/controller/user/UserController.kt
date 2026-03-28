package io.github.mralex1810.fantasy.controller.user

import io.github.mralex1810.fantasy.dto.user.response.UserCardItemDto
import io.github.mralex1810.fantasy.dto.user.response.UserProfileDto
import io.github.mralex1810.fantasy.entity.Rarity
import io.github.mralex1810.fantasy.entity.TelegramUser
import io.github.mralex1810.fantasy.service.UserCardCollectionService
import io.github.mralex1810.fantasy.service.UserFantasyTeamService
import io.github.mralex1810.fantasy.service.UserService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
class UserController(
    private val userService: UserService,
    private val userCardCollectionService: UserCardCollectionService,
    private val userFantasyTeamService: UserFantasyTeamService,
) {

    @GetMapping("/me")
    fun me(@AuthenticationPrincipal user: TelegramUser): UserProfileDto = userService.toProfileDto(user)

    @GetMapping("/me/cards")
    fun myCards(
        @AuthenticationPrincipal user: TelegramUser,
        @RequestParam(required = false) tournamentId: Long?,
        @RequestParam(required = false) rarity: Rarity?,
    ): List<UserCardItemDto> = userCardCollectionService.listCards(user, tournamentId, rarity)

    @GetMapping("/me/fantasy-teams")
    fun fantasyTeams(@AuthenticationPrincipal user: TelegramUser) =
        userFantasyTeamService.listAllTeams(user)

    @GetMapping("/me/fantasy-teams/{seriesId}")
    fun fantasyTeamForSeries(
        @AuthenticationPrincipal user: TelegramUser,
        @PathVariable seriesId: Long,
    ) = userFantasyTeamService.getTeamForSeries(user, seriesId)
}
