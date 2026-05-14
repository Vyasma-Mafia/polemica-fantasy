package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.dto.admin.request.BanDuration
import io.github.mralex1810.fantasy.dto.admin.request.SanctionTransactionRequest
import io.github.mralex1810.fantasy.dto.admin.response.SanctionTransactionResultDto
import io.github.mralex1810.fantasy.entity.FantikiTransactionReason
import io.github.mralex1810.fantasy.entity.MarketplaceListingSanction
import io.github.mralex1810.fantasy.entity.MarketplaceListingStatus
import io.github.mralex1810.fantasy.event.ComplainantRewardInfo
import io.github.mralex1810.fantasy.event.MarketplaceSanctionAppliedEvent
import io.github.mralex1810.fantasy.repository.MarketplaceComplaintRepository
import io.github.mralex1810.fantasy.repository.MarketplaceListingRepository
import io.github.mralex1810.fantasy.repository.MarketplaceListingSanctionRepository
import io.github.mralex1810.fantasy.repository.TelegramUserRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
class MarketplaceSanctionService(
    private val marketplaceListingRepository: MarketplaceListingRepository,
    private val marketplaceListingSanctionRepository: MarketplaceListingSanctionRepository,
    private val marketplaceComplaintRepository: MarketplaceComplaintRepository,
    private val telegramUserRepository: TelegramUserRepository,
    private val userService: UserService,
    private val applicationEventPublisher: ApplicationEventPublisher,
) {
    @Transactional
    fun sanctionTransaction(
        listingId: Long,
        request: SanctionTransactionRequest,
        adminUsername: String,
    ): SanctionTransactionResultDto {
        val reason = request.reason?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "reason is required")
        val sellerFine = request.sellerFine ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "sellerFine is required")
        val buyerFine = request.buyerFine ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "buyerFine is required")
        val complainantReward = request.complainantReward
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "complainantReward is required")
        if (sellerFine < 0 || buyerFine < 0 || complainantReward < 0) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Fine and reward values must be non-negative")
        }

        val listing = marketplaceListingRepository.findSoldByIdWithTradeDetails(
            id = listingId,
            sold = MarketplaceListingStatus.SOLD,
        ) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found")
        if (marketplaceListingSanctionRepository.existsByListing_Id(listingId)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Transaction already sanctioned")
        }

        val seller = listing.seller ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Seller not found")
        val buyer = listing.buyer ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Buyer not found")
        val sellerId = seller.id ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Seller not found")
        val buyerId = buyer.id ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Buyer not found")

        if (sellerFine > 0) {
            userService.forceDeductBalance(
                internalUserId = sellerId,
                amount = sellerFine,
                reason = FantikiTransactionReason.MARKETPLACE_SANCTION_FINE,
            )
        }
        if (buyerFine > 0) {
            userService.forceDeductBalance(
                internalUserId = buyerId,
                amount = buyerFine,
                reason = FantikiTransactionReason.MARKETPLACE_SANCTION_FINE,
            )
        }

        val complaints = marketplaceComplaintRepository.findAllByListing_Id(listingId)
        val complainantInfos = mutableListOf<ComplainantRewardInfo>()
        if (complainantReward > 0) {
            for (complaint in complaints) {
                val complainant = complaint.telegramUser ?: continue
                val complainantId = complainant.id ?: continue
                userService.addBalance(
                    internalUserId = complainantId,
                    amount = complainantReward,
                    reason = FantikiTransactionReason.MARKETPLACE_COMPLAINT_REWARD,
                )
                complainantInfos += ComplainantRewardInfo(
                    telegramChatId = complainant.telegramId,
                    reward = complainantReward,
                    newBalance = userService.getBalance(complainantId),
                )
            }
        }

        val sellerBanUntil = applyBanIfRequested(sellerId, request.banSeller)
        val buyerBanUntil = applyBanIfRequested(buyerId, request.banBuyer)

        marketplaceListingSanctionRepository.save(
            MarketplaceListingSanction(
                listing = listing,
                reason = reason,
                sellerFine = sellerFine,
                buyerFine = buyerFine,
                complainantReward = complainantReward,
                adminUsername = adminUsername,
            ),
        )

        val effectiveTemplate = listing.soldCardTemplate ?: listing.userCard!!.cardTemplate!!
        val playerName = effectiveTemplate.fantasyPlayer?.nickname ?: "—"
        val rarity = effectiveTemplate.rarity
        val sellerNewBalance = userService.getBalance(sellerId)
        val buyerNewBalance = userService.getBalance(buyerId)

        applicationEventPublisher.publishEvent(
            MarketplaceSanctionAppliedEvent(
                listingId = listingId,
                playerName = playerName,
                rarity = rarity,
                price = listing.price,
                reason = reason,
                sellerTelegramChatId = seller.telegramId,
                sellerFine = sellerFine,
                sellerNewBalance = sellerNewBalance,
                buyerTelegramChatId = buyer.telegramId,
                buyerFine = buyerFine,
                buyerNewBalance = buyerNewBalance,
                complainants = complainantInfos,
            ),
        )

        return SanctionTransactionResultDto(
            listingId = listingId,
            sellerFined = sellerFine,
            sellerNewBalance = sellerNewBalance,
            sellerBannedUntil = sellerBanUntil,
            buyerFined = buyerFine,
            buyerNewBalance = buyerNewBalance,
            buyerBannedUntil = buyerBanUntil,
            complainantsRewarded = complaints.size,
            totalRewardPaid = complainantReward * complaints.size,
        )
    }

    private fun applyBanIfRequested(userId: Long, banDuration: BanDuration?): Instant? {
        if (banDuration == null) return null
        val days = banDuration.days ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Ban days must be positive")
        if (days <= 0) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Ban days must be positive")
        }
        val user = telegramUserRepository.findById(userId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")
        }
        val until = Instant.now().plus(days.toLong(), ChronoUnit.DAYS)
        user.marketplaceBannedUntil = until
        telegramUserRepository.save(user)
        return until
    }
}
