package io.github.mralex1810.fantasy.controller.admin

import io.github.mralex1810.fantasy.dto.admin.request.AssignSeriesPlayersRequest
import io.github.mralex1810.fantasy.dto.admin.request.AddSeriesGameRequest
import io.github.mralex1810.fantasy.dto.admin.request.BatchStartSeriesRequest
import io.github.mralex1810.fantasy.dto.admin.request.CreateSeriesRequest
import io.github.mralex1810.fantasy.dto.admin.request.FinalizeSeriesRequest
import io.github.mralex1810.fantasy.dto.admin.request.LinkLegacySeriesSourcesRequest
import io.github.mralex1810.fantasy.dto.admin.request.UpdateSeriesRequest
import io.github.mralex1810.fantasy.dto.admin.response.AdminSeriesGameDto
import io.github.mralex1810.fantasy.dto.admin.response.SeriesResultsResponseDto
import io.github.mralex1810.fantasy.dto.admin.response.BatchStartSeriesResponse
import io.github.mralex1810.fantasy.dto.admin.response.SeriesDto
import io.github.mralex1810.fantasy.dto.admin.response.SeriesCompletionPreviewDto
import io.github.mralex1810.fantasy.dto.admin.response.LegacySeriesSourcesLinkDto
import io.github.mralex1810.fantasy.dto.admin.response.SeriesPlayerMarketplaceUnlistResultDto
import io.github.mralex1810.fantasy.dto.user.response.SeriesFinalizationResultDto
import io.github.mralex1810.fantasy.service.SeriesFinalizationService
import io.github.mralex1810.fantasy.service.SeriesCompletionService
import io.github.mralex1810.fantasy.service.SeriesService
import io.github.mralex1810.fantasy.service.SeriesResultsService
import io.github.mralex1810.fantasy.service.leagueimport.LeagueImportLegacyLinkService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.DeleteMapping
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
    private val seriesCompletionService: SeriesCompletionService,
    private val seriesResultsService: SeriesResultsService,
    private val leagueImportLegacyLinkService: LeagueImportLegacyLinkService,
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

    @PostMapping("/series/{id}/players/{tournamentPlayerId}/unlist-marketplace")
    fun unlistPlayerFromMarketplace(
        @PathVariable id: Long,
        @PathVariable tournamentPlayerId: Long,
    ): SeriesPlayerMarketplaceUnlistResultDto = seriesService.unlistMarketplaceListingsForTournamentPlayer(id, tournamentPlayerId)

    @PostMapping("/series/{id}/sync-games")
    fun syncGames(@PathVariable id: Long) {
        seriesService.syncGames(id)
    }

    @PostMapping("/series/{id}/calculate-scores")
    fun calculateScores(@PathVariable id: Long) {
        seriesService.calculateScores(id)
    }

    @GetMapping("/series/{id}/games")
    fun listSeriesGames(@PathVariable id: Long): List<AdminSeriesGameDto> =
        seriesService.listSeriesGames(id)

    @GetMapping("/series/{id}/results")
    fun getSeriesResults(@PathVariable id: Long): SeriesResultsResponseDto =
        seriesResultsService.getResults(id)

    @PostMapping("/series/{id}/games")
    fun addSeriesGame(
        @PathVariable id: Long,
        @Valid @RequestBody body: AddSeriesGameRequest,
    ): AdminSeriesGameDto = seriesService.addSeriesGame(id, body)

    @DeleteMapping("/series/{id}/games/{gameId}")
    fun deleteSeriesGame(
        @PathVariable id: Long,
        @PathVariable gameId: Long,
    ) {
        seriesService.deleteSeriesGame(id, gameId)
    }

    @GetMapping("/series/{id}/completion-preview")
    fun getCompletionPreview(@PathVariable id: Long): SeriesCompletionPreviewDto =
        seriesCompletionService.evaluate(id).let {
            SeriesCompletionPreviewDto(
                ready = it.ready,
                readinessChecksum = it.checksum,
                reason = it.reason,
            )
        }

    @PostMapping("/series/{id}/finalize")
    fun finalizeSeries(
        @PathVariable id: Long,
        @Valid @RequestBody body: FinalizeSeriesRequest,
    ): SeriesFinalizationResultDto =
        seriesFinalizationService.finalizeSeries(id, body.readinessChecksum)

    @PostMapping("/series/{id}/telegram-sources/legacy-link")
    fun linkLegacyTelegramSources(
        @PathVariable id: Long,
        @Valid @RequestBody body: LinkLegacySeriesSourcesRequest,
    ): LegacySeriesSourcesLinkDto =
        leagueImportLegacyLinkService.link(id, body.announcementMessageId, body.resultMessageId)
}
