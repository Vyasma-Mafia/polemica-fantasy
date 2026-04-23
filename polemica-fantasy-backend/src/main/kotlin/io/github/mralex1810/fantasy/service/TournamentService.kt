package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.dto.admin.request.AddTournamentPlayerRequest
import io.github.mralex1810.fantasy.dto.admin.request.PatchTournamentPlayerRequest
import io.github.mralex1810.fantasy.dto.admin.request.CreateTournamentRequest
import io.github.mralex1810.fantasy.dto.admin.request.UpdateTournamentRequest
import io.github.mralex1810.fantasy.dto.admin.response.ActiveSeriesBriefDto
import io.github.mralex1810.fantasy.dto.admin.response.TournamentDetailDto
import io.github.mralex1810.fantasy.dto.admin.response.TournamentDto
import io.github.mralex1810.fantasy.dto.admin.response.TournamentPlayerDto
import io.github.mralex1810.fantasy.entity.FantasyPlayer
import io.github.mralex1810.fantasy.entity.SeriesStatus
import io.github.mralex1810.fantasy.entity.Tournament
import io.github.mralex1810.fantasy.entity.TournamentKind
import io.github.mralex1810.fantasy.entity.TournamentPlayer
import io.github.mralex1810.fantasy.repository.FantasyPlayerRepository
import io.github.mralex1810.fantasy.repository.SeriesPlayerRepository
import io.github.mralex1810.fantasy.repository.SeriesRepository
import io.github.mralex1810.fantasy.repository.TournamentPlayerRepository
import io.github.mralex1810.fantasy.repository.TournamentRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException

@Service
class TournamentService(
    private val tournamentRepository: TournamentRepository,
    private val tournamentPlayerRepository: TournamentPlayerRepository,
    private val fantasyPlayerRepository: FantasyPlayerRepository,
    private val seriesPlayerRepository: SeriesPlayerRepository,
    private val seriesRepository: SeriesRepository,
    private val imageStorageService: ImageStorageService,
) {

    @Transactional
    fun createTournament(request: CreateTournamentRequest): TournamentDto {
        val kind = request.kind ?: TournamentKind.STANDALONE
        validateKindAndCompetition(kind, request.polemicaCompetitionId)
        val compId = request.polemicaCompetitionId
        if (kind == TournamentKind.POLEMICA_COMPETITION && tournamentRepository.existsByPolemicaCompetitionId(compId!!)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Another tournament already uses this Polemica competition")
        }
        val t = tournamentRepository.save(
            Tournament(
                name = request.name.trim(),
                description = request.description?.trim()?.takeIf { it.isNotEmpty() },
                status = request.status,
                kind = kind,
                polemicaCompetitionId = if (kind == TournamentKind.STANDALONE) null else compId,
            ),
        )
        return t.toDto()
    }

    @Transactional
    fun updateTournament(id: Long, request: UpdateTournamentRequest): TournamentDto {
        val t = tournamentRepository.findById(id).orElseThrow { notFound("Tournament", id) }
        val hasSeries = seriesRepository.countByTournament_Id(id) > 0
        if (hasSeries) {
            val newKind = request.kind ?: t.kind
            val newPolemicaCompetitionId = when (newKind) {
                TournamentKind.STANDALONE -> null
                TournamentKind.POLEMICA_COMPETITION ->
                    request.polemicaCompetitionId ?: t.polemicaCompetitionId
            }
            if (newKind != t.kind || newPolemicaCompetitionId != t.polemicaCompetitionId) {
                throw ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cannot change kind or polemicaCompetitionId when tournament has series",
                )
            }
        }
        request.name?.let { t.name = it.trim() }
        request.description?.let { t.description = it.trim().takeIf { s -> s.isNotEmpty() } }
        request.status?.let { t.status = it }
        if (!hasSeries) {
            if (request.kind != null) {
                t.kind = request.kind
            }
            when (t.kind) {
                TournamentKind.STANDALONE -> {
                    t.polemicaCompetitionId = null
                }
                TournamentKind.POLEMICA_COMPETITION -> {
                    val cid = request.polemicaCompetitionId ?: t.polemicaCompetitionId
                        ?: throw ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "polemicaCompetitionId is required when kind is POLEMICA_COMPETITION",
                        )
                    if (tournamentRepository.existsByPolemicaCompetitionIdAndIdNot(cid, id)) {
                        throw ResponseStatusException(HttpStatus.CONFLICT, "Another tournament already uses this Polemica competition")
                    }
                    t.polemicaCompetitionId = cid
                }
            }
        }
        validateKindAndCompetition(t.kind, t.polemicaCompetitionId)
        return tournamentRepository.save(t).toDto()
    }

    private fun validateKindAndCompetition(kind: TournamentKind, polemicaCompetitionId: Long?) {
        when (kind) {
            TournamentKind.STANDALONE -> {
                if (polemicaCompetitionId != null) {
                    throw ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "polemicaCompetitionId must be null when kind is STANDALONE",
                    )
                }
            }
            TournamentKind.POLEMICA_COMPETITION -> {
                if (polemicaCompetitionId == null) {
                    throw ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "polemicaCompetitionId is required when kind is POLEMICA_COMPETITION",
                    )
                }
            }
        }
    }

    @Transactional(readOnly = true)
    fun listTournaments(): List<TournamentDto> {
        val tournaments = tournamentRepository.findAll().sortedBy { it.id }
        if (tournaments.isEmpty()) return emptyList()
        val tournamentIds = tournaments.mapNotNull { it.id }
        val nonFinishedSeries = seriesRepository.findAllByTournament_IdInAndStatusNot(
            tournamentIds,
            SeriesStatus.FINISHED,
        )
        val byTournamentId = nonFinishedSeries.groupBy { it.tournament!!.id!! }
        return tournaments.map { t ->
            val tid = t.id!!
            val briefs = (byTournamentId[tid] ?: emptyList())
                .sortedByDescending { it.id }
                .map { s ->
                    ActiveSeriesBriefDto(id = s.id!!, name = s.name, status = s.status)
                }
            t.toDto(activeSeries = briefs)
        }
    }

    @Transactional(readOnly = true)
    fun getTournament(id: Long): TournamentDetailDto {
        val t = tournamentRepository.findById(id).orElseThrow { notFound("Tournament", id) }
        val players = tournamentPlayerRepository.findAllByTournament_IdOrderById(id).map { it.toDto() }
        return TournamentDetailDto(
            id = t.id!!,
            name = t.name,
            description = t.description,
            status = t.status,
            kind = t.kind,
            polemicaCompetitionId = t.polemicaCompetitionId,
            createdAt = t.createdAt,
            players = players,
        )
    }

    @Transactional
    fun addPlayer(tournamentId: Long, request: AddTournamentPlayerRequest): TournamentPlayerDto {
        val tournament = tournamentRepository.findById(tournamentId).orElseThrow { notFound("Tournament", tournamentId) }
        val fp = fantasyPlayerRepository.findByPolemicaUserId(request.polemicaUserId)
            ?: fantasyPlayerRepository.save(
                FantasyPlayer(
                    polemicaUserId = request.polemicaUserId,
                    nickname = request.nickname.trim(),
                ),
            )
        if (tournamentPlayerRepository.existsByTournament_IdAndFantasyPlayer_Id(tournamentId, fp.id!!)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Player with this polemica_user_id already in tournament")
        }
        val p = tournamentPlayerRepository.save(
            TournamentPlayer(
                tournament = tournament,
                fantasyPlayer = fp,
            ),
        )
        return p.toDto()
    }

    @Transactional
    fun removePlayer(tournamentId: Long, playerId: Long) {
        val p = tournamentPlayerRepository.findByIdAndTournament_Id(playerId, tournamentId)
            ?: throw notFound("TournamentPlayer", playerId)
        if (seriesPlayerRepository.existsByTournamentPlayer_Id(playerId)) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Cannot remove player assigned to a series; remove from series first",
            )
        }
        tournamentPlayerRepository.delete(p)
    }

    @Transactional
    fun patchTournamentPlayer(
        tournamentId: Long,
        playerId: Long,
        request: PatchTournamentPlayerRequest,
    ): TournamentPlayerDto {
        val p = tournamentPlayerRepository.findByIdAndTournament_Id(playerId, tournamentId)
            ?: throw notFound("TournamentPlayer", playerId)
        p.excludedFromPackPool = request.excludedFromPackPool
        return tournamentPlayerRepository.save(p).toDto()
    }

    @Transactional
    fun uploadPlayerPhoto(tournamentId: Long, playerId: Long, file: MultipartFile): TournamentPlayerDto {
        file.validateImageUpload()
        val p = tournamentPlayerRepository.findByIdAndTournament_Id(playerId, tournamentId)
            ?: throw notFound("TournamentPlayer", playerId)
        val fp = p.fantasyPlayer!!
        val ext = file.imageExtension()
        val key = imageStorageService.playerPhotoKey(fp.id!!, ext)
        fp.photoUrl?.let { prev ->
            runCatching { imageStorageService.delete(imageStorageService.keyFromUrlOrKey(prev)) }
        }
        val url = imageStorageService.upload(key, file.bytes, file.contentType!!)
        fp.photoUrl = url
        return tournamentPlayerRepository.save(p).toDto()
    }

    private fun Tournament.toDto(activeSeries: List<ActiveSeriesBriefDto> = emptyList()) = TournamentDto(
        id = id!!,
        name = name,
        description = description,
        status = status,
        kind = kind,
        polemicaCompetitionId = polemicaCompetitionId,
        createdAt = createdAt,
        activeSeries = activeSeries,
    )

    private fun TournamentPlayer.toDto(): TournamentPlayerDto {
        val fp = fantasyPlayer!!
        return TournamentPlayerDto(
            id = id!!,
            tournamentId = tournament!!.id!!,
            fantasyPlayerId = fp.id!!,
            polemicaUserId = fp.polemicaUserId,
            nickname = fp.nickname,
            photoUrl = imageStorageService.publicObjectUrl(fp.photoUrl),
            excludedFromPackPool = excludedFromPackPool,
        )
    }

    private fun notFound(entity: String, id: Long): ResponseStatusException =
        ResponseStatusException(HttpStatus.NOT_FOUND, "$entity $id not found")
}
