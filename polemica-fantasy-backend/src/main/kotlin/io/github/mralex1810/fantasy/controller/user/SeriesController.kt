package io.github.mralex1810.fantasy.controller.user

import io.github.mralex1810.fantasy.dto.user.request.SubmitFantasyTeamRequest
import io.github.mralex1810.fantasy.dto.user.response.FantasyTeamSeriesDetailsDto
import io.github.mralex1810.fantasy.dto.user.response.LeaderboardEntryDto
import io.github.mralex1810.fantasy.dto.user.response.PublicFantasyTeamDto
import io.github.mralex1810.fantasy.dto.user.response.UserSeriesDetailDto
import io.github.mralex1810.fantasy.entity.TelegramUser
import io.github.mralex1810.fantasy.service.UserFantasyTeamService
import io.github.mralex1810.fantasy.service.UserSeriesService
import io.github.mralex1810.fantasy.service.LeagueService
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/series")
class SeriesController(
    private val userSeriesService: UserSeriesService,
    private val userFantasyTeamService: UserFantasyTeamService,
) {

    @GetMapping("/{id}")
    fun get(
        @AuthenticationPrincipal user: TelegramUser,
        @PathVariable id: Long,
    ): UserSeriesDetailDto = userSeriesService.getSeriesDetail(user, id)

    @GetMapping("/{id}/leaderboard")
    fun leaderboard(
        @PathVariable id: Long,
        @RequestParam(required = false) leagueCode: String?,
    ): List<LeaderboardEntryDto> = userSeriesService.getLeaderboard(id, leagueCode ?: LeagueService.MAIN_CODE)

    @GetMapping("/{id}/users/{telegramId}/fantasy-team")
    fun getPublicFantasyTeam(
        @AuthenticationPrincipal user: TelegramUser,
        @PathVariable id: Long,
        @PathVariable telegramId: Long,
        @RequestParam(required = false) leagueCode: String?,
    ): PublicFantasyTeamDto = userFantasyTeamService.getPublicTeamForSeries(
        id,
        telegramId,
        leagueCode ?: LeagueService.MAIN_CODE,
    )

    @GetMapping("/{id}/users/{telegramId}/fantasy-team/details")
    fun getPublicFantasyTeamDetails(
        @AuthenticationPrincipal user: TelegramUser,
        @PathVariable id: Long,
        @PathVariable telegramId: Long,
        @RequestParam(required = false) leagueCode: String?,
    ): FantasyTeamSeriesDetailsDto = userFantasyTeamService.getPublicTeamDetailsForSeries(
        id,
        telegramId,
        leagueCode ?: LeagueService.MAIN_CODE,
    )

    @PostMapping("/{id}/fantasy-team")
    fun createFantasyTeam(
        @AuthenticationPrincipal user: TelegramUser,
        @PathVariable id: Long,
        @Valid @RequestBody body: SubmitFantasyTeamRequest,
        @RequestParam(required = false) leagueCode: String?,
    ) = userFantasyTeamService.createFantasyTeam(user, id, body, leagueCode ?: LeagueService.MAIN_CODE)

    @PutMapping("/{id}/fantasy-team")
    fun updateFantasyTeam(
        @AuthenticationPrincipal user: TelegramUser,
        @PathVariable id: Long,
        @Valid @RequestBody body: SubmitFantasyTeamRequest,
        @RequestParam(required = false) leagueCode: String?,
    ) = userFantasyTeamService.updateFantasyTeam(user, id, body, leagueCode ?: LeagueService.MAIN_CODE)
}
