package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.dto.user.response.ActiveSeriesDto
import io.github.mralex1810.fantasy.dto.user.response.SeriesOpenForTeamDto
import io.github.mralex1810.fantasy.dto.user.response.SeriesPlayerEntryDto
import io.github.mralex1810.fantasy.dto.user.response.SeriesLeagueBriefDto
import io.github.mralex1810.fantasy.dto.user.response.UserSeriesSummaryDto
import io.github.mralex1810.fantasy.dto.user.response.UserTournamentDetailDto
import io.github.mralex1810.fantasy.dto.user.response.UserTournamentDto
import io.github.mralex1810.fantasy.entity.SeriesStatus
import io.github.mralex1810.fantasy.entity.TelegramUser
import io.github.mralex1810.fantasy.entity.Tournament
import io.github.mralex1810.fantasy.entity.TournamentStatus
import io.github.mralex1810.fantasy.repository.FantasyTeamRepository
import io.github.mralex1810.fantasy.repository.SeriesLeagueRepository
import io.github.mralex1810.fantasy.repository.SeriesRepository
import io.github.mralex1810.fantasy.repository.TournamentPlayerRepository
import io.github.mralex1810.fantasy.repository.TournamentRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

@Service
class UserTournamentService(
    private val tournamentRepository: TournamentRepository,
    private val seriesRepository: SeriesRepository,
    private val seriesLeagueRepository: SeriesLeagueRepository,
    private val fantasyTeamRepository: FantasyTeamRepository,
    private val tournamentPlayerRepository: TournamentPlayerRepository,
    private val leagueService: LeagueService,
    private val imageStorageService: ImageStorageService,
    private val streamLinkService: StreamLinkService,
) {

    @Transactional(readOnly = true)
    fun listActiveTournaments(): List<UserTournamentDto> =
        tournamentRepository.findAllByStatusOrderByIdAsc(TournamentStatus.ACTIVE).map { it.toUserDto() }

    @Transactional(readOnly = true)
    fun listArchivedTournaments(): List<UserTournamentDto> =
        tournamentRepository.findAllByStatusOrderByIdDesc(TournamentStatus.FINISHED).map { it.toUserDto() }

    @Transactional(readOnly = true)
    fun listSeriesOpenForTeam(): List<SeriesOpenForTeamDto> {
        val now = Instant.now()
        val series = seriesRepository.findAllOpenForTeamSubmission(
            tournamentStatus = TournamentStatus.ACTIVE,
            finishedStatus = SeriesStatus.FINISHED,
            now = now,
        )
        val linksBySeriesId = streamLinkService.effectiveLinksBySeries(series)
        return series.map { s ->
            val t = s.tournament!!
            SeriesOpenForTeamDto(
                seriesId = s.id!!,
                tournamentId = t.id!!,
                tournamentName = t.name,
                seriesName = s.name,
                publicNumber = s.publicNumber,
                status = s.status,
                gameNumFrom = s.gameNumFrom,
                gameNumTo = s.gameNumTo,
                teamDeadline = s.teamDeadline,
                streamLinks = linksBySeriesId[s.id!!] ?: emptyList(),
            )
        }
    }

    @Transactional(readOnly = true)
    fun listActiveSeries(): List<ActiveSeriesDto> {
        val now = Instant.now()
        val series = seriesRepository.findAllActiveForHome(
            tournamentStatus = TournamentStatus.ACTIVE,
            statuses = listOf(SeriesStatus.ACTIVE, SeriesStatus.SCORING),
        )
        val linksBySeriesId = streamLinkService.effectiveLinksBySeries(series)
        return series.map { s ->
            val t = s.tournament!!
            ActiveSeriesDto(
                seriesId = s.id!!,
                tournamentId = t.id!!,
                tournamentName = t.name,
                seriesName = s.name,
                publicNumber = s.publicNumber,
                status = s.status,
                startsAt = s.startsAt,
                teamDeadline = s.teamDeadline,
                teamSubmissionOpen = s.teamDeadline > now && s.status != SeriesStatus.FINISHED,
                streamLinks = linksBySeriesId[s.id!!] ?: emptyList(),
            )
        }
    }

    @Transactional(readOnly = true)
    fun getTournament(id: Long, user: TelegramUser): UserTournamentDetailDto {
        val t = tournamentRepository.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Tournament $id not found")
        }
        val userId = user.id!!
        val seriesEntities = seriesRepository.findAllByTournament_IdOrderByIdDesc(id)
        val streamLinksBySeriesId = streamLinkService.effectiveLinksBySeries(seriesEntities)
        val seriesList = seriesEntities.map { s ->
            UserSeriesSummaryDto(
                id = s.id!!,
                tournamentId = t.id!!,
                name = s.name,
                publicNumber = s.publicNumber,
                namePrefix = s.namePrefix,
                gameNumFrom = s.gameNumFrom,
                gameNumTo = s.gameNumTo,
                status = s.status,
                startsAt = s.startsAt,
                teamDeadline = s.teamDeadline,
                streamLinks = streamLinksBySeriesId[s.id!!] ?: emptyList(),
                leagues = listSeriesLeagueBriefs(s.id!!, userId),
            )
        }
        return UserTournamentDetailDto(
            id = t.id!!,
            name = t.name,
            description = t.description,
            status = t.status,
            kind = t.kind,
            polemicaCompetitionId = t.polemicaCompetitionId,
            createdAt = t.createdAt,
            series = seriesList,
        )
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

    @Transactional(readOnly = true)
    fun listParticipants(tournamentId: Long): List<SeriesPlayerEntryDto> {
        if (!tournamentRepository.existsById(tournamentId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Tournament $tournamentId not found")
        }
        return tournamentPlayerRepository.findAllByTournament_IdOrderById(tournamentId).map { tp ->
            val fp = tp.fantasyPlayer!!
            SeriesPlayerEntryDto(
                tournamentPlayerId = tp.id!!,
                fantasyPlayerId = fp.id!!,
                nickname = fp.nickname,
                photoUrl = imageStorageService.publicObjectUrl(fp.photoUrl),
            )
        }
    }

    private fun Tournament.toUserDto() = UserTournamentDto(
        id = id!!,
        name = name,
        description = description,
        status = status,
        kind = kind,
        polemicaCompetitionId = polemicaCompetitionId,
        createdAt = createdAt,
    )
}
