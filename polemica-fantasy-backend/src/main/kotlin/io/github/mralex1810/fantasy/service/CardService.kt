package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.dto.admin.request.AddCardTemplateAchievementRequest
import io.github.mralex1810.fantasy.dto.admin.request.CreateCardTemplateRequest
import io.github.mralex1810.fantasy.dto.admin.request.GiveCardsRequest
import io.github.mralex1810.fantasy.dto.admin.request.UpdateCardTemplateRequest
import io.github.mralex1810.fantasy.dto.admin.response.CardTemplateAchievementDto
import io.github.mralex1810.fantasy.dto.admin.response.CardTemplateDto
import io.github.mralex1810.fantasy.dto.admin.response.OpenPackResultDto
import io.github.mralex1810.fantasy.dto.admin.response.UserCardDto
import io.github.mralex1810.fantasy.entity.CardTemplate
import io.github.mralex1810.fantasy.entity.CardTemplateAchievement
import io.github.mralex1810.fantasy.entity.Rarity
import io.github.mralex1810.fantasy.entity.UserCard
import io.github.mralex1810.fantasy.repository.AchievementRepository
import io.github.mralex1810.fantasy.repository.CardTemplateAchievementRepository
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
    private val achievementRepository: AchievementRepository,
    private val cardTemplateAchievementRepository: CardTemplateAchievementRepository,
    private val fantasyPlayerRepository: FantasyPlayerRepository,
    private val userCardRepository: UserCardRepository,
    private val userService: UserService,
    private val imageStorageService: ImageStorageService,
    private val cardPackService: CardPackService,
    private val economyConfigService: EconomyConfigService,
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
    fun addAchievement(cardTemplateId: Long, request: AddCardTemplateAchievementRequest): CardTemplateDto {
        val ct = cardTemplateRepository.findById(cardTemplateId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Card template $cardTemplateId not found")
        }
        val achievement = achievementRepository.findById(request.achievementId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Achievement ${request.achievementId} not found")
        }
        if (cardTemplateAchievementRepository.existsByCardTemplate_IdAndAchievement_Id(cardTemplateId, request.achievementId)) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Achievement ${request.achievementId} is already linked to card template $cardTemplateId",
            )
        }
        cardTemplateAchievementRepository.save(
            CardTemplateAchievement(
                cardTemplate = ct,
                achievement = achievement,
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
        return request.cardTemplateIds.map { templateId ->
            val template = cardTemplateRepository.findById(templateId).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Card template $templateId not found")
            }
            userCardRepository.save(
                UserCard(
                    telegramUser = user,
                    cardTemplate = template,
                    acquiredAt = now,
                    usesRemaining = economyConfigService.getUsesForRarity(template.rarity),
                    timesRenewed = 0,
                ),
            ).toDto()
        }
    }

    /**
     * @param telegramUserId Telegram platform user id (not internal DB id).
     */
    @Transactional
    fun openPack(telegramUserId: Long, packId: Long): OpenPackResultDto =
        cardPackService.openPack(telegramUserId, packId)

    private fun CardTemplate.toDto(): CardTemplateDto {
        val ach = achievements.map { a ->
            val def = a.achievement!!
            CardTemplateAchievementDto(
                id = a.id!!,
                achievementId = def.id,
                achievementName = def.name,
                bonusPoints = a.bonusPoints ?: def.bonusPoints,
            )
        }
        return CardTemplateDto(
            id = id!!,
            fantasyPlayerId = fantasyPlayer!!.id!!,
            rarity = rarity,
            imageUrl = imageUrl,
            description = description,
            achievements = ach,
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
