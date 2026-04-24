package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.dto.user.response.LegendaryUpgradeInfoDto
import io.github.mralex1810.fantasy.dto.user.response.UserCardItemDto
import io.github.mralex1810.fantasy.entity.FantikiTransactionReason
import io.github.mralex1810.fantasy.entity.MarketplaceListingStatus
import io.github.mralex1810.fantasy.entity.Rarity
import io.github.mralex1810.fantasy.entity.TelegramUser
import io.github.mralex1810.fantasy.repository.AchievementRepository
import io.github.mralex1810.fantasy.repository.CardTemplateRepository
import io.github.mralex1810.fantasy.repository.FantasyTeamCardRepository
import io.github.mralex1810.fantasy.repository.MarketplaceListingRepository
import io.github.mralex1810.fantasy.repository.UserCardRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class LegendaryUpgradeService(
    private val userCardRepository: UserCardRepository,
    private val fantasyTeamCardRepository: FantasyTeamCardRepository,
    private val achievementRepository: AchievementRepository,
    private val cardPackService: CardPackService,
    private val cardTemplateRepository: CardTemplateRepository,
    private val economyConfigService: EconomyConfigService,
    private val userService: UserService,
    private val marketplaceListingRepository: MarketplaceListingRepository,
    private val imageStorageService: ImageStorageService,
    private val cardValueService: CardValueService,
) {

    @Transactional(readOnly = true)
    fun getInfo(user: TelegramUser): LegendaryUpgradeInfoDto {
        val cost = economyConfigService.getLegendaryUpgradeCost()
        val internalId = user.id!!
        val balance = userService.getBalance(internalId)
        return LegendaryUpgradeInfoDto(
            cost = cost,
            balance = balance,
            canAfford = balance >= cost,
        )
    }

    @Transactional
    fun upgrade(user: TelegramUser, userCardId: Long, achievementId: String): UserCardItemDto {
        val uc = userCardRepository.findByIdAndTelegramUser_IdWithTemplateAchievements(userCardId, user.id!!)
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Card not found or not owned")
        if (uc.cardTemplate!!.rarity != Rarity.EPIC) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Only EPIC cards can be upgraded")
        }
        if (uc.usesRemaining <= 0) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Renew the card before upgrading")
        }
        if (fantasyTeamCardRepository.countInNonFinalizedSeries(userCardId) > 0) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot upgrade a card in an active team")
        }
        if (marketplaceListingRepository.existsByUserCard_IdAndStatus(userCardId, MarketplaceListingStatus.ACTIVE)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot upgrade a card that is listed on the marketplace")
        }

        val existingRows = uc.cardTemplate!!.achievements.distinctBy { it.achievement!!.id }
        if (existingRows.size != 2) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "EPIC card must have exactly 2 achievements to upgrade",
            )
        }
        val existingIds = existingRows.map { it.achievement!!.id }.toSet()
        if (achievementId in existingIds) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Achievement already on this card")
        }

        val extra = achievementRepository.findById(achievementId).orElseThrow {
            ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown achievement: $achievementId")
        }

        val cost = economyConfigService.getLegendaryUpgradeCost()
        val internalId = user.id!!
        try {
            userService.deductBalance(internalId, cost, FantikiTransactionReason.LEGENDARY_UPGRADE)
        } catch (e: ResponseStatusException) {
            if (e.statusCode == HttpStatus.BAD_REQUEST) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Insufficient balance")
            }
            throw e
        }

        val fantasyPlayer = uc.cardTemplate!!.fantasyPlayer!!
        val legendaryAchievements = existingRows.map { it.achievement!! } + extra
        val newTemplate = cardPackService.findOrCreateCardTemplateForAchievements(
            fantasyPlayer,
            Rarity.LEGENDARY,
            legendaryAchievements,
        )

        uc.cardTemplate = newTemplate
        uc.usesRemaining += 1
        uc.craftedBy = user
        userCardRepository.save(uc)

        val fresh = userCardRepository.findByIdAndTelegramUser_IdWithTemplateAchievements(uc.id!!, user.id!!)
            ?: throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to reload upgraded card")
        val tpl = cardTemplateRepository.findAllByIdWithAchievementsLoaded(listOf(fresh.cardTemplate!!.id!!))
            .firstOrNull() ?: fresh.cardTemplate!!
        return fresh.toUserCardItemDto(tpl, imageStorageService, cardValueService)
    }
}
