package io.github.mralex1810.fantasy.controller.admin

import io.github.mralex1810.fantasy.dto.admin.request.AddTournamentPlayerRequest
import io.github.mralex1810.fantasy.dto.admin.request.CreateTournamentRequest
import io.github.mralex1810.fantasy.dto.admin.request.UpdateTournamentRequest
import io.github.mralex1810.fantasy.dto.admin.response.TournamentDetailDto
import io.github.mralex1810.fantasy.dto.admin.response.TournamentDto
import io.github.mralex1810.fantasy.dto.admin.response.TournamentPlayerDto
import io.github.mralex1810.fantasy.service.TournamentService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/v1/admin/tournaments")
class TournamentAdminController(
    private val tournamentService: TournamentService,
) {

    @PostMapping
    fun createTournament(@Valid @RequestBody body: CreateTournamentRequest): TournamentDto =
        tournamentService.createTournament(body)

    @PutMapping("/{id}")
    fun updateTournament(
        @PathVariable id: Long,
        @Valid @RequestBody body: UpdateTournamentRequest,
    ): TournamentDto = tournamentService.updateTournament(id, body)

    @GetMapping
    fun listTournaments(): List<TournamentDto> = tournamentService.listTournaments()

    @GetMapping("/{id}")
    fun getTournament(@PathVariable id: Long): TournamentDetailDto = tournamentService.getTournament(id)

    @PostMapping("/{id}/players")
    fun addPlayer(
        @PathVariable id: Long,
        @Valid @RequestBody body: AddTournamentPlayerRequest,
    ): TournamentPlayerDto = tournamentService.addPlayer(id, body)

    @DeleteMapping("/{id}/players/{playerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun removePlayer(
        @PathVariable id: Long,
        @PathVariable playerId: Long,
    ) {
        tournamentService.removePlayer(id, playerId)
    }

    @PostMapping("/{id}/players/{playerId}/photo", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun uploadPlayerPhoto(
        @PathVariable id: Long,
        @PathVariable playerId: Long,
        @RequestPart("file") file: MultipartFile,
    ): TournamentPlayerDto = tournamentService.uploadPlayerPhoto(id, playerId, file)
}
