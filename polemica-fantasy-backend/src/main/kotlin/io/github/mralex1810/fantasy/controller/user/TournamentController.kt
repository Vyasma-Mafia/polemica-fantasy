package io.github.mralex1810.fantasy.controller.user

import io.github.mralex1810.fantasy.dto.user.response.SeriesPlayerEntryDto
import io.github.mralex1810.fantasy.dto.user.response.UserTournamentDetailDto
import io.github.mralex1810.fantasy.dto.user.response.UserTournamentDto
import io.github.mralex1810.fantasy.service.UserTournamentService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/tournaments")
class TournamentController(
    private val userTournamentService: UserTournamentService,
) {

    @GetMapping
    fun list(): List<UserTournamentDto> = userTournamentService.listActiveTournaments()

    @GetMapping("/{id}")
    fun get(@PathVariable id: Long): UserTournamentDetailDto = userTournamentService.getTournament(id)

    @GetMapping("/{id}/participants")
    fun participants(@PathVariable id: Long): List<SeriesPlayerEntryDto> = userTournamentService.listParticipants(id)
}
