package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.dto.user.response.ComplainResultDto
import io.github.mralex1810.fantasy.entity.MarketplaceComplaint
import io.github.mralex1810.fantasy.entity.MarketplaceListingStatus
import io.github.mralex1810.fantasy.entity.TelegramUser
import io.github.mralex1810.fantasy.repository.MarketplaceComplaintRepository
import io.github.mralex1810.fantasy.repository.MarketplaceListingRepository
import io.github.mralex1810.fantasy.repository.MarketplaceListingSanctionRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
class MarketplaceComplaintService(
    private val marketplaceComplaintRepository: MarketplaceComplaintRepository,
    private val marketplaceListingRepository: MarketplaceListingRepository,
    private val marketplaceListingSanctionRepository: MarketplaceListingSanctionRepository,
    private val economyConfigService: EconomyConfigService,
) {
    @Transactional
    fun complain(user: TelegramUser, listingId: Long): ComplainResultDto {
        val userId = user.id ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")
        val listing = marketplaceListingRepository.findById(listingId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found")
        }
        if (listing.status != MarketplaceListingStatus.SOLD || listing.soldAt == null) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Can only complain about completed transactions")
        }
        if (listing.seller?.id == userId || listing.buyer?.id == userId) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot complain about your own transaction")
        }
        if (marketplaceListingSanctionRepository.existsByListing_Id(listingId)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Transaction already sanctioned")
        }
        if (marketplaceComplaintRepository.existsByListing_IdAndTelegramUser_Id(listingId, userId)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Already complained")
        }

        val dailyLimit = economyConfigService.getInt("marketplace.daily_complaint_limit")
        val since = Instant.now().minus(24, ChronoUnit.HOURS)
        val recentComplaints = marketplaceComplaintRepository.countByTelegramUserIdSince(userId, since)
        if (recentComplaints >= dailyLimit) {
            throw ResponseStatusException(
                HttpStatus.TOO_MANY_REQUESTS,
                "Daily complaint limit reached ($dailyLimit)",
            )
        }

        marketplaceComplaintRepository.save(
            MarketplaceComplaint(
                listing = listing,
                telegramUser = user,
            ),
        )
        val totalComplaints = marketplaceComplaintRepository.countByListing_Id(listingId)
        return ComplainResultDto(
            listingId = listingId,
            totalComplaints = totalComplaints,
            remainingToday = (dailyLimit - recentComplaints - 1).coerceAtLeast(0),
        )
    }
}
