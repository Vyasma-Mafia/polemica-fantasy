package io.github.mralex1810.fantasy.dto.user.response

import io.github.mralex1810.fantasy.entity.CardAcquisitionType
import java.time.Instant

data class CardOwnershipHistoryEntryDto(
    val ownerDisplayName: String,
    val acquisitionType: CardAcquisitionType,
    val acquisitionLabel: String,
    val acquiredAt: Instant,
)
