package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.dto.user.response.LeaderboardEntryDto
import io.github.mralex1810.fantasy.dto.user.response.SeriesGameEntryDto
import io.github.mralex1810.fantasy.dto.user.response.SeriesLeagueBriefDto
import io.github.mralex1810.fantasy.dto.user.response.SeriesLeagueDto
import io.github.mralex1810.fantasy.dto.user.response.SeriesPlayerEntryDto
import io.github.mralex1810.fantasy.dto.user.response.UserPublicDto
import io.github.mralex1810.fantasy.dto.user.response.UserSeriesDetailDto
import io.github.mralex1810.fantasy.entity.SeriesLeague
import io.github.mralex1810.fantasy.entity.TelegramUser
import io.github.mralex1810.fantasy.repository.FantasyTeamRepository
import io.github.mralex1810.fantasy.repository.SeriesLeagueRepository
import io.github.mralex1810.fantasy.repository.SeriesGameRepository
import io.github.mralex1810.fantasy.repository.SeriesPlayerRepository
import io.github.mralex1810.fantasy.repository.SeriesRepository
import io.github.mralex1810.fantasy.service.achievement.ProfileFrameVisibilityService
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class UserSeriesService(
    private val seriesRepository: SeriesRepository,
    private val seriesLeagueRepository: SeriesLeagueRepository,
    private val seriesPlayerRepository: SeriesPlayerRepository,
    private val seriesGameRepository: SeriesGameRepository,
    private val fantasyTeamRepository: FantasyTeamRepository,
    private val leagueService: LeagueService,
    private val imageStorageService: ImageStorageService,
    private val profileFrameVisibilityService: ProfileFrameVisibilityService,
    private val streamLinkService: StreamLinkService,
) {

    @Transactional(readOnly = true)
    fun getSeriesDetail(user: TelegramUser, seriesId: Long): UserSeriesDetailDto {
        val s = seriesRepository.findById(seriesId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Series $seriesId not found")
        }
        val players = seriesPlayerRepository.findAllBySeries_IdWithTournamentPlayers(seriesId).map { sp ->
            val tp = sp.tournamentPlayer!!
            val fp = tp.fantasyPlayer!!
            SeriesPlayerEntryDto(
                tournamentPlayerId = tp.id!!,
                fantasyPlayerId = fp.id!!,
                polemicaUserId = fp.polemicaUserId,
                nickname = fp.nickname,
                photoUrl = imageStorageService.publicObjectUrl(fp.photoUrl),
            )
        }
        val games = seriesGameRepository.findAllBySeries_Id(seriesId).map { g ->
            SeriesGameEntryDto(
                polemicaGameId = g.polemicaGameId,
                gameName = formatSeriesGameDisplayName(g),
                scored = g.scored,
            )
        }
        val leagueBriefs = listSeriesLeagueBriefs(seriesId, user.id!!)
        return UserSeriesDetailDto(
            id = s.id!!,
            tournamentId = s.tournament!!.id!!,
            tournamentKind = s.tournament!!.kind,
            polemicaCompetitionId = s.tournament!!.polemicaCompetitionId,
            name = s.name,
            publicNumber = s.publicNumber,
            namePrefix = s.namePrefix,
            gameNumFrom = s.gameNumFrom,
            gameNumTo = s.gameNumTo,
            status = s.status,
            startsAt = s.startsAt,
            teamDeadline = s.teamDeadline,
            streamLinks = streamLinkService.effectiveLinksForSeries(s),
            players = players,
            games = games,
            leagues = leagueBriefs,
        )
    }

    @Transactional(readOnly = true)
    fun getLeaderboard(seriesId: Long, leagueCode: String = LeagueService.MAIN_CODE): List<LeaderboardEntryDto> {
        if (!seriesRepository.existsById(seriesId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Series $seriesId not found")
        }
        val seriesLeague = resolveSeriesLeague(seriesId, leagueCode)
        val teams = fantasyTeamRepository.findLeaderboardForSeriesLeague(seriesLeague.id!!)
        val profileFrameCodes = profileFrameVisibilityService.selectedFrameCodes(
            teams.mapNotNull { it.telegramUser?.id },
        )
        return teams.mapIndexed { index, ft ->
            val u = ft.telegramUser!!
            LeaderboardEntryDto(
                rank = index + 1,
                totalScore = ft.totalScore,
                user = UserPublicDto(
                    telegramId = u.telegramId,
                    username = u.username,
                    firstName = u.firstName,
                    displayName = u.displayName,
                    profileFrameCode = profileFrameCodes[u.id!!],
                ),
                fantasyPlayerIds = ft.cards.mapNotNull { c ->
                    c.userCard?.cardTemplate?.fantasyPlayer?.id
                },
            )
        }
    }

    @Transactional(readOnly = true)
    fun listSeriesLeagues(user: TelegramUser, seriesId: Long): List<SeriesLeagueDto> {
        if (!seriesRepository.existsById(seriesId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Series $seriesId not found")
        }
        val userId = user.id!!
        return seriesLeagueRepository.findAllEnabledBySeriesIdWithLeague(seriesId).map { sl ->
            val league = sl.league!!
            SeriesLeagueDto(
                code = league.code,
                name = league.name,
                description = league.description,
                valueCap = leagueService.getEffectiveValueCap(sl),
                maxLegendaryCount = leagueService.getEffectiveMaxLegendary(sl),
                minTeamSize = league.minTeamSize,
                maxTeamSize = league.maxTeamSize,
                rewardScale = leagueService.getEffectiveRewardScale(sl),
                hasTeam = fantasyTeamRepository.findByTelegramUser_IdAndSeriesLeague_Id(userId, sl.id!!) != null,
            )
        }
    }

    private fun listSeriesLeagueBriefs(seriesId: Long, userId: Long): List<SeriesLeagueBriefDto> =
        seriesLeagueRepository.findAllEnabledBySeriesIdWithLeague(seriesId).map { sl ->
            val league = sl.league!!
            SeriesLeagueBriefDto(
                code = league.code,
                name = league.name,
                hasTeam = fantasyTeamRepository.findByTelegramUser_IdAndSeriesLeague_Id(userId, sl.id!!) != null,
                valueCap = leagueService.getEffectiveValueCap(sl),
            )
        }

    private fun resolveSeriesLeague(seriesId: Long, leagueCode: String): SeriesLeague =
        seriesLeagueRepository.findBySeries_IdAndLeague_CodeAndEnabledTrue(seriesId, leagueCode)
            ?: throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "League $leagueCode is not available for series $seriesId",
            )
}
