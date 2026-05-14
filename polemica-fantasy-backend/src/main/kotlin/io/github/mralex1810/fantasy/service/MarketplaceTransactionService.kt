package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.dto.user.response.MarketplaceCardAchievementDto
import io.github.mralex1810.fantasy.dto.user.response.MarketplaceTransactionDetailDto
import io.github.mralex1810.fantasy.dto.user.response.TransactionCardDto
import io.github.mralex1810.fantasy.dto.user.response.TransactionComplaintInfoDto
import io.github.mralex1810.fantasy.dto.user.response.TransactionParticipantDto
import io.github.mralex1810.fantasy.dto.user.response.TransactionSanctionInfoDto
import io.github.mralex1810.fantasy.entity.CardTemplate
import io.github.mralex1810.fantasy.entity.MarketplaceListingStatus
import io.github.mralex1810.fantasy.entity.TelegramUser
import io.github.mralex1810.fantasy.repository.CardTemplateRepository
import io.github.mralex1810.fantasy.repository.MarketplaceComplaintRepository
import io.github.mralex1810.fantasy.repository.MarketplaceListingRepository
import io.github.mralex1810.fantasy.repository.MarketplaceListingSanctionRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
@Transactional(readOnly = true)
class MarketplaceTransactionService(
    private val marketplaceListingRepository: MarketplaceListingRepository,
    private val marketplaceComplaintRepository: MarketplaceComplaintRepository,
    private val marketplaceListingSanctionRepository: MarketplaceListingSanctionRepository,
    private val economyConfigService: EconomyConfigService,
    private val cardTemplateRepository: CardTemplateRepository,
    private val imageStorageService: ImageStorageService,
) {
    fun getTransactionDetail(user: TelegramUser, listingId: Long): MarketplaceTransactionDetailDto {
        val userId = user.id ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")
        val listing = marketplaceListingRepository.findSoldByIdWithTradeDetails(
            id = listingId,
            sold = MarketplaceListingStatus.SOLD,
        ) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found")

        val template = resolveTemplateForListing(listingId, listing.soldCardTemplate ?: listing.userCard!!.cardTemplate!!)
        val commissionPercent = economyConfigService.getMarketplaceCommissionPercent()
        val commission = listing.price * commissionPercent / 100
        val sellerReceived = listing.price - commission
        val totalComplaints = marketplaceComplaintRepository.countByListing_Id(listingId)
        val userAlreadyComplained = marketplaceComplaintRepository.existsByListing_IdAndTelegramUser_Id(listingId, userId)
        val sanction = marketplaceListingSanctionRepository.findByListing_Id(listingId)

        return MarketplaceTransactionDetailDto(
            listingId = listing.id!!,
            price = listing.price,
            soldAt = listing.soldAt!!,
            commission = commission,
            sellerReceived = sellerReceived,
            seller = listing.seller!!.toParticipantDto(),
            buyer = listing.buyer!!.toParticipantDto(),
            card = template.toTransactionCardDto(),
            complaint = TransactionComplaintInfoDto(
                totalComplaints = totalComplaints,
                userAlreadyComplained = userAlreadyComplained,
            ),
            sanction = sanction?.let {
                TransactionSanctionInfoDto(
                    sanctionedAt = it.createdAt,
                    reason = it.reason,
                )
            },
        )
    }

    private fun resolveTemplateForListing(listingId: Long, template: CardTemplate): CardTemplate {
        val templateId = template.id ?: throw ResponseStatusException(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Card template is missing for transaction $listingId",
        )
        return cardTemplateRepository.findAllByIdWithAchievementsLoaded(listOf(templateId)).firstOrNull() ?: template
    }

    private fun TelegramUser.toParticipantDto(): TransactionParticipantDto =
        TransactionParticipantDto(
            telegramId = telegramId,
            displayName = publicDisplayName(),
        )

    private fun CardTemplate.toTransactionCardDto(): TransactionCardDto {
        val fantasyPlayer = fantasyPlayer!!
        return TransactionCardDto(
            fantasyPlayerId = fantasyPlayer.id!!,
            playerName = fantasyPlayer.nickname,
            playerPhotoUrl = imageStorageService.publicObjectUrl(fantasyPlayer.photoUrl),
            rarity = rarity,
            achievements = achievements.distinctBy { it.achievement!!.id }.map { ach ->
                val def = ach.achievement!!
                MarketplaceCardAchievementDto(
                    achievementId = def.id,
                    name = def.name,
                    bonusPoints = ach.bonusPoints ?: def.bonusPoints,
                )
            },
        )
    }
}
