package io.github.mralex1810.fantasy.controller.user

import io.github.mralex1810.fantasy.dto.user.request.SubmitFantasyTeamRequest
import io.github.mralex1810.fantasy.dto.user.response.FantasyTeamSeriesDetailsDto
import io.github.mralex1810.fantasy.dto.user.response.LeaderboardEntryDto
import io.github.mralex1810.fantasy.dto.user.response.PublicFantasyTeamDto
import io.github.mralex1810.fantasy.dto.user.response.SeriesLeagueDto
import io.github.mralex1810.fantasy.entity.TelegramUser
import io.github.mralex1810.fantasy.service.UserFantasyTeamService
import io.github.mralex1810.fantasy.service.UserSeriesService
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/series/{seriesId}/leagues")
class LeagueController(
    private val userSeriesService: UserSeriesService,
    private val userFantasyTeamService: UserFantasyTeamService,
) {
    @GetMapping
    fun listSeriesLeagues(
        @AuthenticationPrincipal user: TelegramUser,
        @PathVariable seriesId: Long,
    ): List<SeriesLeagueDto> = userSeriesService.listSeriesLeagues(user, seriesId)

    @GetMapping("/{leagueCode}/leaderboard")
    fun leagueLeaderboard(
        @PathVariable seriesId: Long,
        @PathVariable leagueCode: String,
    ): List<LeaderboardEntryDto> = userSeriesService.getLeaderboard(seriesId, leagueCode)

    @PostMapping("/{leagueCode}/fantasy-team")
    fun createFantasyTeam(
        @AuthenticationPrincipal user: TelegramUser,
        @PathVariable seriesId: Long,
        @PathVariable leagueCode: String,
        @Valid @RequestBody body: SubmitFantasyTeamRequest,
    ) = userFantasyTeamService.createFantasyTeam(user, seriesId, body, leagueCode)

    @PutMapping("/{leagueCode}/fantasy-team")
    fun updateFantasyTeam(
        @AuthenticationPrincipal user: TelegramUser,
        @PathVariable seriesId: Long,
        @PathVariable leagueCode: String,
        @Valid @RequestBody body: SubmitFantasyTeamRequest,
    ) = userFantasyTeamService.updateFantasyTeam(user, seriesId, body, leagueCode)

    @GetMapping("/{leagueCode}/users/{telegramId}/fantasy-team")
    fun getPublicFantasyTeam(
        @AuthenticationPrincipal user: TelegramUser,
        @PathVariable seriesId: Long,
        @PathVariable leagueCode: String,
        @PathVariable telegramId: Long,
    ): PublicFantasyTeamDto = userFantasyTeamService.getPublicTeamForSeries(seriesId, telegramId, leagueCode)

    @GetMapping("/{leagueCode}/users/{telegramId}/fantasy-team/details")
    fun getPublicFantasyTeamDetails(
        @AuthenticationPrincipal user: TelegramUser,
        @PathVariable seriesId: Long,
        @PathVariable leagueCode: String,
        @PathVariable telegramId: Long,
    ): FantasyTeamSeriesDetailsDto = userFantasyTeamService.getPublicTeamDetailsForSeries(
        seriesId,
        telegramId,
        leagueCode,
    )
}
