package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.dto.admin.request.UpdateLeagueRequest
import io.github.mralex1810.fantasy.dto.admin.request.UpsertSeriesLeagueRequest
import io.github.mralex1810.fantasy.dto.admin.response.LeagueAdminDto
import io.github.mralex1810.fantasy.dto.admin.response.SeriesLeagueAdminDto
import io.github.mralex1810.fantasy.entity.League
import io.github.mralex1810.fantasy.entity.SeriesLeague
import io.github.mralex1810.fantasy.repository.FantasyTeamRepository
import io.github.mralex1810.fantasy.repository.LeagueRepository
import io.github.mralex1810.fantasy.repository.SeriesLeagueRepository
import io.github.mralex1810.fantasy.repository.SeriesRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class LeagueAdminService(
    private val leagueRepository: LeagueRepository,
    private val seriesRepository: SeriesRepository,
    private val seriesLeagueRepository: SeriesLeagueRepository,
    private val fantasyTeamRepository: FantasyTeamRepository,
    private val leagueService: LeagueService,
) {
    @Transactional(readOnly = true)
    fun listLeagues(): List<LeagueAdminDto> =
        leagueRepository.findAll().sortedBy { it.id }.map { it.toAdminDto() }

    @Transactional
    fun updateLeague(leagueId: Long, request: UpdateLeagueRequest): LeagueAdminDto {
        val league = leagueRepository.findById(leagueId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "League $leagueId not found")
        }
        request.name?.let { league.name = it.trim() }
        request.description?.let { league.description = it.trim().takeIf { d -> d.isNotEmpty() } }
        request.valueCap?.let { league.valueCap = it }
        request.maxLegendaryCount?.let { league.maxLegendaryCount = it }
        request.minTeamSize?.let { league.minTeamSize = it }
        request.maxTeamSize?.let { league.maxTeamSize = it }
        request.visibility?.let { league.visibility = it }
        request.entryFee?.let { league.entryFee = it }
        validateTeamSizeBounds(league.minTeamSize, league.maxTeamSize)
        return leagueRepository.save(league).toAdminDto()
    }

    @Transactional
    fun upsertSeriesLeague(seriesId: Long, request: UpsertSeriesLeagueRequest): SeriesLeagueAdminDto {
        val series = seriesRepository.findById(seriesId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Series $seriesId not found")
        }
        val league = leagueRepository.findById(request.leagueId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "League ${request.leagueId} not found")
        }
        val existing = seriesLeagueRepository.findBySeries_IdAndLeague_Id(seriesId, request.leagueId)
        val seriesLeague = existing ?: SeriesLeague(series = series, league = league)
        if (!request.enabled && fantasyTeamRepository.countBySeriesLeague_Id(seriesLeague.id ?: -1L) > 0) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Cannot disable league with submitted fantasy teams",
            )
        }
        seriesLeague.valueCapOverride = request.valueCapOverride
        seriesLeague.rewardScaleOverride = request.rewardScaleOverride
        seriesLeague.enabled = request.enabled
        return seriesLeagueRepository.save(seriesLeague).toAdminDto()
    }

    @Transactional
    fun disableSeriesLeague(seriesId: Long, leagueId: Long): SeriesLeagueAdminDto {
        val seriesLeague = seriesLeagueRepository.findBySeries_IdAndLeague_Id(seriesId, leagueId)
            ?: throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "League $leagueId is not configured for series $seriesId",
            )
        if (fantasyTeamRepository.countBySeriesLeague_Id(seriesLeague.id!!) > 0) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Cannot disable league with submitted fantasy teams",
            )
        }
        seriesLeague.enabled = false
        return seriesLeagueRepository.save(seriesLeague).toAdminDto()
    }

    private fun validateTeamSizeBounds(minTeamSize: Int, maxTeamSize: Int) {
        if (minTeamSize > maxTeamSize) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "minTeamSize must be <= maxTeamSize")
        }
    }

    private fun League.toAdminDto(): LeagueAdminDto = LeagueAdminDto(
        id = id!!,
        code = code,
        name = name,
        description = description,
        leagueType = leagueType,
        valueCap = valueCap,
        maxLegendaryCount = maxLegendaryCount,
        minTeamSize = minTeamSize,
        maxTeamSize = maxTeamSize,
        createdByTelegramUserId = createdByTelegramUser?.telegramId,
        visibility = visibility,
        entryFee = entryFee,
        createdAt = createdAt,
    )

    private fun SeriesLeague.toAdminDto(): SeriesLeagueAdminDto {
        val league = league!!
        return SeriesLeagueAdminDto(
            id = id!!,
            seriesId = series!!.id!!,
            leagueId = league.id!!,
            leagueCode = league.code,
            leagueName = league.name,
            valueCapOverride = valueCapOverride,
            rewardScaleOverride = rewardScaleOverride,
            enabled = enabled,
            effectiveValueCap = leagueService.getEffectiveValueCap(this),
            effectiveRewardScale = leagueService.getEffectiveRewardScale(this),
        )
    }
}
