package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.dto.user.response.UserSeriesSummaryDto
import io.github.mralex1810.fantasy.dto.user.response.UserTournamentDetailDto
import io.github.mralex1810.fantasy.dto.user.response.UserTournamentDto
import io.github.mralex1810.fantasy.entity.Tournament
import io.github.mralex1810.fantasy.entity.TournamentStatus
import io.github.mralex1810.fantasy.repository.SeriesRepository
import io.github.mralex1810.fantasy.repository.TournamentRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class UserTournamentService(
    private val tournamentRepository: TournamentRepository,
    private val seriesRepository: SeriesRepository,
) {

    @Transactional(readOnly = true)
    fun listActiveTournaments(): List<UserTournamentDto> =
        tournamentRepository.findAllByStatusOrderByIdAsc(TournamentStatus.ACTIVE).map { it.toUserDto() }

    @Transactional(readOnly = true)
    fun getTournament(id: Long): UserTournamentDetailDto {
        val t = tournamentRepository.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Tournament $id not found")
        }
        val seriesList = seriesRepository.findAllByTournament_IdOrderByIdAsc(id).map { s ->
            UserSeriesSummaryDto(
                id = s.id!!,
                tournamentId = t.id!!,
                name = s.name,
                namePrefix = s.namePrefix,
                gameNumFrom = s.gameNumFrom,
                gameNumTo = s.gameNumTo,
                status = s.status,
                startsAt = s.startsAt,
                teamDeadline = s.teamDeadline,
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
