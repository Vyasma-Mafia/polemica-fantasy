package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.dto.user.response.RecycleResultDto
import io.github.mralex1810.fantasy.dto.user.response.RenewResultDto
import io.github.mralex1810.fantasy.entity.FantikiTransactionReason
import io.github.mralex1810.fantasy.entity.MarketplaceListingStatus
import io.github.mralex1810.fantasy.entity.TelegramUser
import io.github.mralex1810.fantasy.event.AchievementProgressEvent
import io.github.mralex1810.fantasy.event.AchievementProgressEventType
import io.github.mralex1810.fantasy.repository.FantasyTeamCardRepository
import io.github.mralex1810.fantasy.repository.MarketplaceListingRepository
import io.github.mralex1810.fantasy.repository.UserCardRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

@Service
class CardLifecycleService(
    private val userCardRepository: UserCardRepository,
    private val fantasyTeamCardRepository: FantasyTeamCardRepository,
    private val economyConfigService: EconomyConfigService,
    private val userService: UserService,
    private val marketplaceListingRepository: MarketplaceListingRepository,
    private val applicationEventPublisher: ApplicationEventPublisher,
) {

    @Transactional
    fun recycleCard(user: TelegramUser, userCardId: Long): RecycleResultDto {
        val uc = userCardRepository.findByIdAndTelegramUser_Id(userCardId, user.id!!)
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Card not found or not owned by user")
        if (fantasyTeamCardRepository.countInNonFinalizedSeries(userCardId) > 0) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Cannot recycle a card that is in a team for a series that is not finalized yet",
            )
        }
        val rarity = uc.cardTemplate!!.rarity
        val earned = economyConfigService.getRecycleValue(rarity)
        val internalId = user.id!!
        marketplaceListingRepository.cancelAllActiveByUserCardId(
            userCardId = userCardId,
            active = MarketplaceListingStatus.ACTIVE,
            cancelled = MarketplaceListingStatus.CANCELLED,
        )
        uc.deletedAt = Instant.now()
        userCardRepository.save(uc)
        userService.addBalance(internalId, earned, FantikiTransactionReason.CARD_RECYCLE)
        val balance = userService.getBalance(internalId)
        applicationEventPublisher.publishEvent(
            AchievementProgressEvent(AchievementProgressEventType.COLLECTION_CHANGED, setOf(internalId)),
        )
        return RecycleResultDto(fantikiEarned = earned, newBalance = balance)
    }

    @Transactional
    fun renewCard(user: TelegramUser, userCardId: Long): RenewResultDto {
        val uc = userCardRepository.findByIdAndTelegramUser_Id(userCardId, user.id!!)
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Card not found or not owned by user")
        if (uc.usesRemaining > 0) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Card still has uses remaining")
        }
        val maxR = economyConfigService.getMaxRenewals()
        if (uc.timesRenewed >= maxR) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Maximum renewals reached for this card")
        }
        if (marketplaceListingRepository.existsByUserCard_IdAndStatus(userCardId, MarketplaceListingStatus.ACTIVE)) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Cannot renew a card that is listed on the marketplace",
            )
        }
        val rarity = uc.cardTemplate!!.rarity
        val cost = economyConfigService.getRenewalCost(rarity)
        val internalId = user.id!!
        try {
            userService.deductBalance(internalId, cost, FantikiTransactionReason.CARD_RENEWAL)
        } catch (e: ResponseStatusException) {
            if (e.statusCode == HttpStatus.BAD_REQUEST) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Insufficient fantiki balance")
            }
            throw e
        }
        uc.timesRenewed += 1
        uc.usesRemaining = economyConfigService.getUsesForRarity(rarity)
        userCardRepository.save(uc)
        val balance = userService.getBalance(internalId)
        return RenewResultDto(
            cost = cost,
            newBalance = balance,
            newUsesRemaining = uc.usesRemaining,
        )
    }
}
