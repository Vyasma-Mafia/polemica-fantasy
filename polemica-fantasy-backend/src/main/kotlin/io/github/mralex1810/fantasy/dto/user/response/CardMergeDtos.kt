package io.github.mralex1810.fantasy.dto.user.response

import io.github.mralex1810.fantasy.entity.CardMergeOperation
import io.github.mralex1810.fantasy.entity.Rarity
import java.time.Instant

enum class CardMergeBlockReason {
    ACTIVE_TEAM,
    MARKETPLACE_ACTIVE,
    EXPIRED_CONTRACT,
}

data class CardMergeOptionsDto(
    val groups: List<CardMergePlayerGroupDto>,
)

data class CardMergePlayerGroupDto(
    val fantasyPlayerId: Long,
    val nickname: String,
    val photoUrl: String?,
    val operations: List<CardMergeOperationOptionDto>,
)

data class CardMergeOperationOptionDto(
    val operation: CardMergeOperation,
    val sourceRarity: Rarity,
    val resultRarity: Rarity,
    val availableCards: List<CardMergeMaterialDto>,
    val blockedCards: List<CardMergeMaterialDto>,
    val eligible: Boolean,
)

data class CardMergeMaterialDto(
    val userCard: UserCardItemDto,
    val blockReason: CardMergeBlockReason? = null,
    val listingId: Long? = null,
    val canCancelListing: Boolean = false,
)

data class CardMergePreviewResponseDto(
    val operation: CardMergeOperation,
    val previewId: Long,
    val expiresAt: Instant,
    val sameRollForInputSet: Boolean,
    val fixedPerkIds: List<String>,
    val selectablePerks: List<PerkCatalogItemDto>,
    val requiredSelections: Int,
    val result: CardMergeResultSummaryDto,
    val valueBefore: Long,
    val valueAfter: Long,
    val warnings: List<CardMergeWarningDto>,
    val materials: List<CardMergeMaterialDto> = emptyList(),
    val burnedSkins: List<String> = emptyList(),
)

data class CardMergeResultSummaryDto(
    val fantasyPlayerId: Long,
    val rarity: Rarity,
    val usesRemaining: Int,
    val baseUses: Int,
    val timesRenewed: Int,
    val maxRenewals: Int,
    val skinCode: String? = null,
    val marketplaceAvailable: Boolean,
    val marketplaceHint: String,
)

data class CardMergeWarningDto(
    val code: String,
    val message: String,
)

data class CardMergeConfirmResponseDto(
    val card: UserCardItemDto,
    val spentFantiki: Long = 0L,
    val newBalance: Long,
)
