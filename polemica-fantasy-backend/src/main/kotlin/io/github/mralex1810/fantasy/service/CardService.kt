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
import io.github.mralex1810.fantasy.repository.CardPackRepository
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
import java.util.Random

@Service
class CardService(
    private val cardTemplateRepository: CardTemplateRepository,
    private val cardTemplateAchievementRepository: CardTemplateAchievementRepository,
    private val fantasyPlayerRepository: FantasyPlayerRepository,
    private val cardPackRepository: CardPackRepository,
    private val userCardRepository: UserCardRepository,
    private val userService: UserService,
    private val imageStorageService: ImageStorageService,
) {

    private val random = Random()

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
        cardTemplateAchievementRepository.save(
            CardTemplateAchievement(
                cardTemplate = ct,
                achievementType = request.achievementType,
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
                ),
            ).toDto()
        }
    }

    /**
     * @param telegramUserId Telegram platform user id (not internal DB id).
     */
    @Transactional
    fun openPack(telegramUserId: Long, packId: Long): OpenPackResultDto {
        val user = userService.getOrCreateAndUpdateProfile(telegramUserId, null, null)
        val pack = cardPackRepository.findById(packId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Card pack $packId not found")
        }
        if (!pack.active) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Card pack is not active")
        }
        val now = Instant.now()
        val drawn = mutableListOf<UserCard>()
        pack.rarityConfigs.forEach { cfg ->
            repeat(cfg.cardsCount) {
                val candidates = cardTemplateRepository.findAllByRarity(cfg.rarity)
                if (candidates.isEmpty()) {
                    throw ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "No card templates for rarity ${cfg.rarity}",
                    )
                }
                val template = candidates[random.nextInt(candidates.size)]
                drawn.add(
                    userCardRepository.save(
                        UserCard(
                            telegramUser = user,
                            cardTemplate = template,
                            acquiredAt = now,
                        ),
                    ),
                )
            }
        }
        return OpenPackResultDto(userCards = drawn.map { it.toDto() })
    }

    private fun CardTemplate.toDto(): CardTemplateDto {
        val ach = achievements.map { a ->
            CardTemplateAchievementDto(
                id = a.id!!,
                achievementType = a.achievementType,
                bonusPoints = a.bonusPoints,
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
    )
}
