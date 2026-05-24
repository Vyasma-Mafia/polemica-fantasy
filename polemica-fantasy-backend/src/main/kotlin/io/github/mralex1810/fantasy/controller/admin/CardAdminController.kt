package io.github.mralex1810.fantasy.controller.admin

import io.github.mralex1810.fantasy.dto.admin.request.AddCardTemplatePerkRequest
import io.github.mralex1810.fantasy.dto.admin.request.CreateCardPackRequest
import io.github.mralex1810.fantasy.dto.admin.request.CreateCardTemplateRequest
import io.github.mralex1810.fantasy.dto.admin.request.GiveCardsRequest
import io.github.mralex1810.fantasy.dto.admin.request.UpdateCardPackPlayersRequest
import io.github.mralex1810.fantasy.dto.admin.request.UpdateCardPackRequest
import io.github.mralex1810.fantasy.dto.admin.request.UpdateCardTemplateRequest
import io.github.mralex1810.fantasy.dto.admin.response.CardPackDto
import io.github.mralex1810.fantasy.dto.admin.response.CardSkinDto
import io.github.mralex1810.fantasy.dto.admin.response.CardTemplateDto
import io.github.mralex1810.fantasy.dto.admin.response.OpenPackResultDto
import io.github.mralex1810.fantasy.dto.admin.response.UserCardDto
import io.github.mralex1810.fantasy.entity.Rarity
import io.github.mralex1810.fantasy.service.CardPackService
import io.github.mralex1810.fantasy.service.CardService
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
@RequestMapping("/api/v1/admin")
class CardAdminController(
    private val cardService: CardService,
    private val cardPackService: CardPackService,
) {

    @GetMapping("/card-skins")
    fun listCardSkins(): List<CardSkinDto> = cardPackService.listSkins()

    @GetMapping("/card-packs")
    fun listCardPacks(@RequestParam(required = false) tournamentId: Long?): List<CardPackDto> =
        cardPackService.listPacks(tournamentId)

    @GetMapping("/card-packs/{id}")
    fun getCardPack(@PathVariable id: Long): CardPackDto = cardPackService.getPack(id)

    @PostMapping("/card-templates")
    fun createCardTemplate(@Valid @RequestBody body: CreateCardTemplateRequest): CardTemplateDto =
        cardService.createTemplate(body)

    @PutMapping("/card-templates/{id}")
    fun updateCardTemplate(
        @PathVariable id: Long,
        @Valid @RequestBody body: UpdateCardTemplateRequest,
    ): CardTemplateDto = cardService.updateTemplate(id, body)

    @GetMapping("/card-templates")
    fun listCardTemplates(
        @RequestParam(required = false) tournamentId: Long?,
        @RequestParam(required = false) fantasyPlayerId: Long?,
        @RequestParam(required = false) rarity: Rarity?,
    ): List<CardTemplateDto> = cardService.listTemplates(tournamentId, fantasyPlayerId, rarity)

    @PostMapping("/card-templates/{id}/perks")
    fun addPerk(
        @PathVariable id: Long,
        @Valid @RequestBody body: AddCardTemplatePerkRequest,
    ): CardTemplateDto = cardService.addPerk(id, body)

    @PostMapping("/card-templates/{id}/image", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun uploadCardImage(
        @PathVariable id: Long,
        @RequestPart("file") file: MultipartFile,
    ): CardTemplateDto = cardService.uploadCardImage(id, file)

    @PostMapping("/card-packs")
    fun createCardPack(@Valid @RequestBody body: CreateCardPackRequest): CardPackDto =
        cardPackService.createPack(body)

    @PutMapping("/card-packs/{id}")
    fun updateCardPack(
        @PathVariable id: Long,
        @Valid @RequestBody body: UpdateCardPackRequest,
    ): CardPackDto = cardPackService.updatePack(id, body)

    @PutMapping("/card-packs/{id}/players")
    fun updateCardPackPlayers(
        @PathVariable id: Long,
        @Valid @RequestBody body: UpdateCardPackPlayersRequest,
    ): CardPackDto = cardPackService.replacePackPlayers(id, body.playerIds)

    @PostMapping("/users/{telegramUserId}/give-cards")
    fun giveCards(
        @PathVariable telegramUserId: Long,
        @Valid @RequestBody body: GiveCardsRequest,
    ): List<UserCardDto> = cardService.giveCards(telegramUserId, body)

    @PostMapping("/users/{telegramUserId}/open-pack/{packId}")
    fun openPack(
        @PathVariable telegramUserId: Long,
        @PathVariable packId: Long,
    ): OpenPackResultDto = cardService.openPack(telegramUserId, packId)
}
