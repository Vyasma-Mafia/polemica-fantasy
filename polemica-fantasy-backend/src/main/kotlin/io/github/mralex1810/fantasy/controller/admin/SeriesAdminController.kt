package io.github.mralex1810.fantasy.controller.admin

import io.github.mralex1810.fantasy.dto.admin.request.AssignSeriesPlayersRequest
import io.github.mralex1810.fantasy.dto.admin.request.BatchStartSeriesRequest
import io.github.mralex1810.fantasy.dto.admin.request.CreateSeriesRequest
import io.github.mralex1810.fantasy.dto.admin.request.UpdateSeriesRequest
import io.github.mralex1810.fantasy.dto.admin.response.BatchStartSeriesResponse
import io.github.mralex1810.fantasy.dto.admin.response.SeriesDto
import io.github.mralex1810.fantasy.dto.user.response.SeriesFinalizationResultDto
import io.github.mralex1810.fantasy.service.SeriesFinalizationService
import io.github.mralex1810.fantasy.service.SeriesService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/admin")
class SeriesAdminController(
    private val seriesService: SeriesService,
    private val seriesFinalizationService: SeriesFinalizationService,
) {

    @GetMapping("/tournaments/{tournamentId}/series")
    fun listSeriesByTournament(@PathVariable tournamentId: Long): List<SeriesDto> =
        seriesService.listSeriesByTournament(tournamentId)

    @GetMapping("/series/{id}")
    fun getSeries(@PathVariable id: Long): SeriesDto = seriesService.getSeries(id)

    @PostMapping("/tournaments/{tournamentId}/series")
    fun createSeries(
        @PathVariable tournamentId: Long,
        @Valid @RequestBody body: CreateSeriesRequest,
    ): SeriesDto = seriesService.createSeries(tournamentId, body)

    @PutMapping("/series/{id}")
    fun updateSeries(
        @PathVariable id: Long,
        @Valid @RequestBody body: UpdateSeriesRequest,
    ): SeriesDto = seriesService.updateSeries(id, body)

    @PostMapping("/series/batch-start")
    fun batchStartSeries(
        @RequestBody body: BatchStartSeriesRequest,
    ): BatchStartSeriesResponse = seriesService.batchStartSeries(body.seriesIds)

    @PostMapping("/series/{id}/players")
    fun assignPlayers(
        @PathVariable id: Long,
        @Valid @RequestBody body: AssignSeriesPlayersRequest,
    ): SeriesDto = seriesService.assignPlayers(id, body)

    @PostMapping("/series/{id}/sync-games")
    fun syncGames(@PathVariable id: Long) {
        seriesService.syncGames(id)
    }

    @PostMapping("/series/{id}/calculate-scores")
    fun calculateScores(@PathVariable id: Long) {
        seriesService.calculateScores(id)
    }

    @PostMapping("/series/{id}/finalize")
    fun finalizeSeries(@PathVariable id: Long): SeriesFinalizationResultDto =
        seriesFinalizationService.finalizeSeries(id)
}
