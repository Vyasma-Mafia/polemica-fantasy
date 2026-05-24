package io.github.mralex1810.fantasy.dto.user.response

import io.github.mralex1810.fantasy.entity.Rarity
import java.time.Instant

data class MarketplaceTransactionDetailDto(
    val listingId: Long,
    val price: Long,
    val soldAt: Instant,
    val commission: Long,
    val sellerReceived: Long,
    val seller: TransactionParticipantDto,
    val buyer: TransactionParticipantDto,
    val card: TransactionCardDto,
    val complaint: TransactionComplaintInfoDto,
    val sanction: TransactionSanctionInfoDto?,
)

data class TransactionParticipantDto(
    val telegramId: Long,
    val displayName: String,
)

data class TransactionCardDto(
    val fantasyPlayerId: Long,
    val playerName: String,
    val playerPhotoUrl: String?,
    val rarity: Rarity,
    val perks: List<MarketplaceCardPerkDto>,
    val skinCode: String?,
)

data class TransactionComplaintInfoDto(
    val totalComplaints: Int,
    val userAlreadyComplained: Boolean,
)

data class TransactionSanctionInfoDto(
    val sanctionedAt: Instant,
    val reason: String,
)

data class ComplainResultDto(
    val listingId: Long,
    val totalComplaints: Int,
    val remainingToday: Int,
)
