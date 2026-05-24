package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.dto.admin.request.AddCardTemplatePerkRequest
import io.github.mralex1810.fantasy.dto.admin.request.CreateCardTemplateRequest
import io.github.mralex1810.fantasy.dto.admin.request.GiveCardsRequest
import io.github.mralex1810.fantasy.dto.admin.request.UpdateCardTemplateRequest
import io.github.mralex1810.fantasy.dto.admin.response.CardTemplatePerkDto
import io.github.mralex1810.fantasy.dto.admin.response.CardTemplateDto
import io.github.mralex1810.fantasy.dto.admin.response.OpenPackResultDto
import io.github.mralex1810.fantasy.dto.admin.response.UserCardDto
import io.github.mralex1810.fantasy.entity.CardAcquisitionType
import io.github.mralex1810.fantasy.entity.CardTemplate
import io.github.mralex1810.fantasy.entity.CardTemplatePerk
import io.github.mralex1810.fantasy.entity.Rarity
import io.github.mralex1810.fantasy.entity.UserCard
import io.github.mralex1810.fantasy.repository.PerkRepository
import io.github.mralex1810.fantasy.repository.CardSkinRepository
import io.github.mralex1810.fantasy.repository.CardTemplatePerkRepository
import io.github.mralex1810.fantasy.repository.CardTemplateRepository
import io.github.mralex1810.fantasy.repository.FantasyPlayerRepository
import io.github.mralex1810.fantasy.repository.UserCardRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

@Service
class CardService(
    private val cardTemplateRepository: CardTemplateRepository,
    private val perkRepository: PerkRepository,
    private val cardSkinRepository: CardSkinRepository,
    private val cardTemplatePerkRepository: CardTemplatePerkRepository,
    private val fantasyPlayerRepository: FantasyPlayerRepository,
    private val userCardRepository: UserCardRepository,
    private val userService: UserService,
    private val imageStorageService: ImageStorageService,
    private val cardPackService: CardPackService,
    private val economyConfigService: EconomyConfigService,
    private val userCardOwnershipService: UserCardOwnershipService,
) {

    @Transactional
    fun createTemplate(request: CreateCardTemplateRequest): CardTemplateDto {
        val fp = fantasyPlayerRepository.findById(request.fantasyPlayerId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Fantasy player ${request.fantasyPlayerId} not found")
        }
        val ct = cardTemplateRepository.save(
            CardTemplate(
                fantasyPlayer = fp,
                rarity = request.rarity,
                description = request.description?.trim()?.takeIf { it.isNotEmpty() },
            ),
        )
        return ct.toDto()
    }

    @Transactional
    fun updateTemplate(id: Long, request: UpdateCardTemplateRequest): CardTemplateDto {
        val ct = cardTemplateRepository.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Card template $id not found")
        }
        request.rarity?.let { ct.rarity = it }
        request.description?.let { ct.description = it.trim().takeIf { s -> s.isNotEmpty() } }
        return cardTemplateRepository.save(ct).toDto()
    }

    @Transactional(readOnly = true)
    fun listTemplates(tournamentId: Long?, fantasyPlayerId: Long?, rarity: Rarity?): List<CardTemplateDto> =
        cardTemplateRepository.findAllFiltered(tournamentId, fantasyPlayerId, rarity).map { it.toDto() }

    @Transactional
    fun addPerk(cardTemplateId: Long, request: AddCardTemplatePerkRequest): CardTemplateDto {
        val ct = cardTemplateRepository.findById(cardTemplateId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Card template $cardTemplateId not found")
        }
        val perk = perkRepository.findById(request.perkId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Perk ${request.perkId} not found")
        }
        if (cardTemplatePerkRepository.existsByCardTemplate_IdAndPerk_Id(cardTemplateId, request.perkId)) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Perk ${request.perkId} is already linked to card template $cardTemplateId",
            )
        }
        cardTemplatePerkRepository.save(
            CardTemplatePerk(
                cardTemplate = ct,
                perk = perk,
                bonusPoints = request.bonusPoints,
            ),
        )
        return cardTemplateRepository.findById(cardTemplateId).get().toDto()
    }

    @Transactional
    fun uploadCardImage(cardTemplateId: Long, file: MultipartFile): CardTemplateDto {
        file.validateImageUpload()
        val ct = cardTemplateRepository.findById(cardTemplateId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Card template $cardTemplateId not found")
        }
        val ext = file.imageExtension()
        val key = imageStorageService.cardImageKey(cardTemplateId, ext)
        ct.imageUrl?.let { prev ->
            runCatching { imageStorageService.delete(imageStorageService.keyFromUrlOrKey(prev)) }
        }
        val url = imageStorageService.upload(key, file.bytes, file.contentType!!)
        ct.imageUrl = url
        return cardTemplateRepository.save(ct).toDto()
    }

    /**
     * @param telegramUserId Telegram platform user id (not internal DB id).
     */
    @Transactional
    fun giveCards(telegramUserId: Long, request: GiveCardsRequest): List<UserCardDto> {
        val user = userService.getOrCreateAndUpdateProfile(telegramUserId, null, null)
        val now = Instant.now()
        val skin = request.skinId?.let { skinId ->
            cardSkinRepository.findById(skinId).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Card skin $skinId not found")
            }
        }
        return request.cardTemplateIds.map { templateId ->
            val template = cardTemplateRepository.findById(templateId).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Card template $templateId not found")
            }
            val saved = userCardRepository.save(
                UserCard(
                    telegramUser = user,
                    cardTemplate = template,
                    cardSkin = skin,
                    acquiredAt = now,
                    usesRemaining = economyConfigService.getUsesForRarity(template.rarity),
                    timesRenewed = 0,
                ),
            )
            userCardOwnershipService.recordAcquisition(saved, user, CardAcquisitionType.ADMIN_GRANT, now)
            saved.toDto()
        }
    }

    /**
     * @param telegramUserId Telegram platform user id (not internal DB id).
     */
    @Transactional
    fun openPack(telegramUserId: Long, packId: Long): OpenPackResultDto =
        cardPackService.openPack(telegramUserId, packId)

    private fun CardTemplate.toDto(): CardTemplateDto {
        val perk = perks.map { a ->
            val def = a.perk!!
            CardTemplatePerkDto(
                id = a.id!!,
                perkId = def.id,
                perkName = def.name,
                bonusPoints = a.bonusPoints ?: def.bonusPoints,
            )
        }
        return CardTemplateDto(
            id = id!!,
            fantasyPlayerId = fantasyPlayer!!.id!!,
            rarity = rarity,
            imageUrl = imageStorageService.publicObjectUrl(imageUrl),
            description = description,
            perks = perk,
        )
    }

    private fun UserCard.toDto() = UserCardDto(
        id = id!!,
        telegramUserId = telegramUser!!.telegramId,
        cardTemplateId = cardTemplate!!.id!!,
        acquiredAt = acquiredAt,
        sourceCardPackId = sourceCardPack?.id,
    )
}
