package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.dto.admin.request.AssignSeriesPlayersRequest
import io.github.mralex1810.fantasy.dto.admin.request.CreateSeriesRequest
import io.github.mralex1810.fantasy.dto.admin.request.UpdateSeriesRequest
import io.github.mralex1810.fantasy.dto.admin.response.SeriesDto
import io.github.mralex1810.fantasy.entity.Series
import io.github.mralex1810.fantasy.entity.SeriesPlayer
import io.github.mralex1810.fantasy.entity.SeriesStatus
import io.github.mralex1810.fantasy.entity.TournamentKind
import io.github.mralex1810.fantasy.polemica.GameSyncService
import io.github.mralex1810.fantasy.repository.SeriesPlayerRepository
import io.github.mralex1810.fantasy.repository.SeriesRepository
import io.github.mralex1810.fantasy.repository.TournamentPlayerRepository
import io.github.mralex1810.fantasy.repository.TournamentRepository
import io.github.mralex1810.fantasy.scoring.ScoringService
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class SeriesService(
    private val tournamentRepository: TournamentRepository,
    private val seriesRepository: SeriesRepository,
    private val tournamentPlayerRepository: TournamentPlayerRepository,
    private val seriesPlayerRepository: SeriesPlayerRepository,
    private val gameSyncService: GameSyncService,
    private val scoringService: ScoringService,
    private val seriesFinalizationService: SeriesFinalizationService,
    private val fantasyTeamRosterPruningService: FantasyTeamRosterPruningService,
) {

    @Transactional
    fun createSeries(tournamentId: Long, request: CreateSeriesRequest): SeriesDto {
        val tournament = tournamentRepository.findById(tournamentId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Tournament $tournamentId not found")
        }
        val (namePrefix, gameFrom, gameTo) = validatedSeriesFields(tournament.kind, request)
        val s = seriesRepository.save(
            Series(
                tournament = tournament,
                name = request.name.trim(),
                namePrefix = namePrefix,
                gameNumFrom = gameFrom,
                gameNumTo = gameTo,
                status = request.status,
                startsAt = request.startsAt,
                teamDeadline = request.teamDeadline,
            ),
        )
        if (s.status == SeriesStatus.FINISHED && !s.finalized) {
            seriesFinalizationService.finalizeSeries(s.id!!)
        }
        return seriesRepository.findById(s.id!!).get().toDto(emptyList())
    }

    @Transactional
    fun updateSeries(id: Long, request: UpdateSeriesRequest): SeriesDto {
        val s = seriesRepository.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Series $id not found")
        }
        val kind = s.tournament!!.kind
        request.name?.let { s.name = it.trim() }
        request.status?.let { s.status = it }
        request.startsAt?.let { s.startsAt = it }
        request.teamDeadline?.let { s.teamDeadline = it }
        when (kind) {
            TournamentKind.STANDALONE -> {
                request.namePrefix?.let { p ->
                    val t = p.trim()
                    if (t.isEmpty()) {
                        throw ResponseStatusException(HttpStatus.BAD_REQUEST, "namePrefix cannot be empty for STANDALONE")
                    }
                    s.namePrefix = t
                }
                if (request.gameNumFrom != null || request.gameNumTo != null) {
                    throw ResponseStatusException(HttpStatus.BAD_REQUEST, "game numbers are not used for STANDALONE tournaments")
                }
            }
            TournamentKind.POLEMICA_COMPETITION -> {
                val from = request.gameNumFrom ?: s.gameNumFrom
                val to = request.gameNumTo ?: s.gameNumTo
                if (request.gameNumFrom != null || request.gameNumTo != null) {
                    if (from == null || to == null) {
                        throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Both gameNumFrom and gameNumTo must be set")
                    }
                    if (from > to) {
                        throw ResponseStatusException(HttpStatus.BAD_REQUEST, "gameNumFrom must be <= gameNumTo")
                    }
                    s.gameNumFrom = from
                    s.gameNumTo = to
                }
                request.namePrefix?.let { s.namePrefix = it.trim().takeIf { x -> x.isNotEmpty() } }
            }
        }
        val saved = seriesRepository.save(s)
        if (saved.status == SeriesStatus.FINISHED && !saved.finalized) {
            seriesFinalizationService.finalizeSeries(saved.id!!)
        }
        val result = seriesRepository.findById(id).get()
        return result.toDto(tournamentPlayerIdsForSeries(id))
    }

    private fun validatedSeriesFields(
        kind: TournamentKind,
        request: CreateSeriesRequest,
    ): Triple<String?, Long?, Long?> =
        when (kind) {
            TournamentKind.STANDALONE -> {
                val p = request.namePrefix?.trim()?.takeIf { it.isNotEmpty() }
                    ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "namePrefix is required for STANDALONE tournaments")
                if (request.gameNumFrom != null || request.gameNumTo != null) {
                    throw ResponseStatusException(HttpStatus.BAD_REQUEST, "game numbers are not used for STANDALONE tournaments")
                }
                Triple(p, null, null)
            }
            TournamentKind.POLEMICA_COMPETITION -> {
                val from = request.gameNumFrom
                    ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "gameNumFrom is required for POLEMICA_COMPETITION tournaments")
                val to = request.gameNumTo
                    ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "gameNumTo is required for POLEMICA_COMPETITION tournaments")
                if (from > to) {
                    throw ResponseStatusException(HttpStatus.BAD_REQUEST, "gameNumFrom must be <= gameNumTo")
                }
                val prefix = request.namePrefix?.trim()?.takeIf { it.isNotEmpty() }
                Triple(prefix, from, to)
            }
        }

    @Transactional
    fun assignPlayers(seriesId: Long, request: AssignSeriesPlayersRequest): SeriesDto {
        val series = seriesRepository.findById(seriesId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Series $seriesId not found")
        }
        val tournamentId = series.tournament!!.id!!
        val ids = request.tournamentPlayerIds.distinct()
        ids.forEach { tpId ->
            val tp = tournamentPlayerRepository.findByIdAndTournament_Id(tpId, tournamentId)
                ?: throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Tournament player $tpId is not part of tournament $tournamentId",
                )
        }
        seriesPlayerRepository.deleteAllBySeries_Id(seriesId)
        seriesPlayerRepository.flush()
        ids.forEach { tpId ->
            val tp = tournamentPlayerRepository.findById(tpId).get()
            seriesPlayerRepository.save(SeriesPlayer(series = series, tournamentPlayer = tp))
        }
        fantasyTeamRosterPruningService.pruneInvalidCardsForSeries(seriesId)
        return seriesRepository.findById(seriesId).get().toDto(tournamentPlayerIdsForSeries(seriesId))
    }

    @Transactional(readOnly = true)
    fun listSeriesByTournament(tournamentId: Long): List<SeriesDto> {
        if (!tournamentRepository.existsById(tournamentId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Tournament $tournamentId not found")
        }
        val seriesList = seriesRepository.findAllByTournament_IdOrderByIdDesc(tournamentId)
        val sidList = seriesList.map { it.id!! }
        if (sidList.isEmpty()) return emptyList()
        val bySeriesId = seriesPlayerRepository.findAllBySeries_IdIn(sidList).groupBy { sp ->
            sp.series!!.id!!
        }
        return seriesList.map { s ->
            val tpIds = bySeriesId[s.id]?.map { it.tournamentPlayer!!.id!! }?.sorted() ?: emptyList()
            s.toDto(tpIds)
        }
    }

    @Transactional(readOnly = true)
    fun getSeries(id: Long): SeriesDto {
        val s = seriesRepository.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Series $id not found")
        }
        return s.toDto(tournamentPlayerIdsForSeries(id))
    }

    fun syncGames(seriesId: Long) {
        gameSyncService.syncGames(seriesId)
    }

    fun calculateScores(seriesId: Long) {
        scoringService.calculateScores(seriesId)
    }

    private fun tournamentPlayerIdsForSeries(seriesId: Long): List<Long> =
        seriesPlayerRepository.findAllBySeries_IdWithTournamentPlayers(seriesId).map { it.tournamentPlayer!!.id!! }

    private fun Series.toDto(tournamentPlayerIds: List<Long>) = SeriesDto(
        id = id!!,
        tournamentId = tournament!!.id!!,
        name = name,
        namePrefix = namePrefix,
        gameNumFrom = gameNumFrom,
        gameNumTo = gameNumTo,
        status = status,
        startsAt = startsAt,
        teamDeadline = teamDeadline,
        finalized = finalized,
        tournamentPlayerIds = tournamentPlayerIds,
    )
}
