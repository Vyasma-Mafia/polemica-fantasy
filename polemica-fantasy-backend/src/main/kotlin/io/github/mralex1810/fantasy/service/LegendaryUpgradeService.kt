package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.config.EasterEggProperties
import io.github.mralex1810.fantasy.dto.user.response.LegendaryEasterEggDto
import io.github.mralex1810.fantasy.dto.user.response.LegendaryUpgradeInfoDto
import io.github.mralex1810.fantasy.dto.user.response.LegendaryUpgradeResponseDto
import io.github.mralex1810.fantasy.entity.FantikiTransactionReason
import io.github.mralex1810.fantasy.entity.MarketplaceListingStatus
import io.github.mralex1810.fantasy.entity.Rarity
import io.github.mralex1810.fantasy.entity.TelegramUser
import io.github.mralex1810.fantasy.repository.PerkRepository
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
    private val perkRepository: PerkRepository,
    private val cardPackService: CardPackService,
    private val cardTemplateRepository: CardTemplateRepository,
    private val economyConfigService: EconomyConfigService,
    private val userService: UserService,
    private val marketplaceListingRepository: MarketplaceListingRepository,
    private val imageStorageService: ImageStorageService,
    private val cardValueService: CardValueService,
    private val easterEggProperties: EasterEggProperties,
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
    fun upgrade(user: TelegramUser, userCardId: Long, perkId: String): LegendaryUpgradeResponseDto {
        val uc = userCardRepository.findByIdAndTelegramUser_IdWithTemplatePerks(userCardId, user.id!!)
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

        val existingRows = uc.cardTemplate!!.perks.distinctBy { it.perk!!.id }
        if (existingRows.size != 2) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "EPIC card must have exactly 2 perks to upgrade",
            )
        }
        val existingIds = existingRows.map { it.perk!!.id }.toSet()
        if (perkId in existingIds) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Perk already on this card")
        }

        val extra = perkRepository.findById(perkId).orElseThrow {
            ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown perk: $perkId")
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
        val fantasyPlayerId = fantasyPlayer.id
            ?: throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Card fantasy player is not persisted")
        val legendaryPerks = existingRows.map { it.perk!! } + extra
        val newTemplate = cardPackService.findOrCreateCardTemplateForPerks(
            fantasyPlayer,
            Rarity.LEGENDARY,
            legendaryPerks,
        )

        uc.cardTemplate = newTemplate
        uc.usesRemaining += 1
        uc.craftedBy = user
        userCardRepository.save(uc)

        val easterEgg = buildLegendaryEasterEggIfEligible(
            fantasyPlayerId = fantasyPlayerId,
            internalUserId = internalId,
        )

        val fresh = userCardRepository.findByIdAndTelegramUser_IdWithTemplatePerks(uc.id!!, user.id!!)
            ?: throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to reload upgraded card")
        val tpl = cardTemplateRepository.findAllByIdWithPerksLoaded(listOf(fresh.cardTemplate!!.id!!))
            .firstOrNull() ?: fresh.cardTemplate!!
        return LegendaryUpgradeResponseDto(
            card = fresh.toUserCardItemDto(tpl, imageStorageService, cardValueService),
            easterEgg = easterEgg,
        )
    }

    private fun buildLegendaryEasterEggIfEligible(
        fantasyPlayerId: Long,
        internalUserId: Long,
    ): LegendaryEasterEggDto? {
        val configuredDeveloperPlayerId = economyConfigService.getEasterEggDeveloperFantasyPlayerId()
        if (configuredDeveloperPlayerId <= 0 || fantasyPlayerId != configuredDeveloperPlayerId) {
            return null
        }

        val bonusFantiki = economyConfigService.getEasterEggDeveloperBonusFantiki()
        if (bonusFantiki <= 0L) {
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Invalid easter egg bonus config")
        }
        userService.addBalance(internalUserId, bonusFantiki, FantikiTransactionReason.EASTER_EGG_BONUS)

        val companionImageUrl = easterEggProperties.tyulenchikImageUrl.trim().takeIf { it.isNotEmpty() }
        return LegendaryEasterEggDto(
            message = "Вместе с легендарным Велосипедостроителем вы нашли легендарного Тюленчика. Вам начислено +$bonusFantiki фантиков",
            bonusFantiki = bonusFantiki,
            companionCardName = "Тюленчик",
            companionCardImageUrl = companionImageUrl,
        )
    }
}
