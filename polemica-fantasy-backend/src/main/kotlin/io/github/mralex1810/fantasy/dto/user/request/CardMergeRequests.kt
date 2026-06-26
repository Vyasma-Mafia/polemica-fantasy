package io.github.mralex1810.fantasy.dto.user.request

import io.github.mralex1810.fantasy.entity.CardMergeOperation
import jakarta.validation.constraints.NotNull

data class CardMergePreviewRequest(
    @field:NotNull
    val operation: CardMergeOperation,
    val inputUserCardIds: List<Long> = emptyList(),
    val selectedSkinSourceUserCardId: Long? = null,
)

data class CardMergeConfirmRequest(
    @field:NotNull
    val operation: CardMergeOperation,
    val inputUserCardIds: List<Long> = emptyList(),
    val selectedPerkIds: List<String> = emptyList(),
    val selectedSkinSourceUserCardId: Long? = null,
    @field:NotNull
    val previewId: Long,
)
