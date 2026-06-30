package io.github.mralex1810.fantasy.controller.admin

import io.github.mralex1810.fantasy.dto.admin.request.AddFantasyPlayerAliasRequest
import io.github.mralex1810.fantasy.dto.admin.request.CreateFantasyPlayerAdminRequest
import io.github.mralex1810.fantasy.dto.admin.request.MergeFantasyPlayersRequest
import io.github.mralex1810.fantasy.dto.admin.request.UpdateFantasyPlayerAdminRequest
import io.github.mralex1810.fantasy.dto.admin.response.FantasyPlayerAdminDto
import io.github.mralex1810.fantasy.dto.admin.response.FantasyPlayerMergePreviewDto
import io.github.mralex1810.fantasy.dto.admin.response.FantasyPlayerMergeResultDto
import io.github.mralex1810.fantasy.service.FantasyPlayerAdminService
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/v1/admin/fantasy-players")
class FantasyPlayerAdminController(
    private val fantasyPlayerAdminService: FantasyPlayerAdminService,
) {

    @GetMapping
    fun listPlayers(@RequestParam(required = false) query: String?): List<FantasyPlayerAdminDto> =
        fantasyPlayerAdminService.listPlayers(query)

    @PostMapping
    fun createPlayer(
        @Valid @RequestBody body: CreateFantasyPlayerAdminRequest,
    ): FantasyPlayerAdminDto = fantasyPlayerAdminService.createPlayer(body)

    @PutMapping("/{id}")
    fun updatePlayer(
        @PathVariable id: Long,
        @Valid @RequestBody body: UpdateFantasyPlayerAdminRequest,
    ): FantasyPlayerAdminDto = fantasyPlayerAdminService.updatePlayer(id, body)

    @PostMapping("/{id}/photo", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun uploadPhoto(
        @PathVariable id: Long,
        @RequestPart("file") file: MultipartFile,
    ): FantasyPlayerAdminDto = fantasyPlayerAdminService.uploadPhoto(id, file)

    @PostMapping("/{id}/aliases")
    fun addAlias(
        @PathVariable id: Long,
        @Valid @RequestBody body: AddFantasyPlayerAliasRequest,
    ): FantasyPlayerAdminDto = fantasyPlayerAdminService.addAlias(id, body)

    @GetMapping("/{id}/merge-preview")
    fun mergePreview(
        @PathVariable id: Long,
        @RequestParam sourceFantasyPlayerId: Long,
    ): FantasyPlayerMergePreviewDto = fantasyPlayerAdminService.mergePreview(id, sourceFantasyPlayerId)

    @PostMapping("/{id}/merge")
    fun mergeConfirm(
        @PathVariable id: Long,
        @Valid @RequestBody body: MergeFantasyPlayersRequest,
    ): FantasyPlayerMergeResultDto = fantasyPlayerAdminService.mergeConfirm(id, body)
}
