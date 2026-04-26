package io.github.mralex1810.fantasy.controller.admin

import io.github.mralex1810.fantasy.dto.admin.request.UpdateLeagueRequest
import io.github.mralex1810.fantasy.dto.admin.request.UpsertSeriesLeagueRequest
import io.github.mralex1810.fantasy.dto.admin.response.LeagueAdminDto
import io.github.mralex1810.fantasy.dto.admin.response.SeriesLeagueAdminDto
import io.github.mralex1810.fantasy.service.LeagueAdminService
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
class LeagueAdminController(
    private val leagueAdminService: LeagueAdminService,
) {
    @GetMapping("/leagues")
    fun listLeagues(): List<LeagueAdminDto> = leagueAdminService.listLeagues()

    @PutMapping("/leagues/{id}")
    fun updateLeague(
        @PathVariable id: Long,
        @Valid @RequestBody body: UpdateLeagueRequest,
    ): LeagueAdminDto = leagueAdminService.updateLeague(id, body)

    @PostMapping("/series/{id}/leagues")
    fun upsertSeriesLeague(
        @PathVariable id: Long,
        @Valid @RequestBody body: UpsertSeriesLeagueRequest,
    ): SeriesLeagueAdminDto = leagueAdminService.upsertSeriesLeague(id, body)

    @DeleteMapping("/series/{seriesId}/leagues/{leagueId}")
    fun disableSeriesLeague(
        @PathVariable seriesId: Long,
        @PathVariable leagueId: Long,
    ): SeriesLeagueAdminDto = leagueAdminService.disableSeriesLeague(seriesId, leagueId)
}
