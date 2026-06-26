package io.github.mralex1810.fantasy.dto.admin.response

import io.github.mralex1810.fantasy.entity.CardMergeOperation
import io.github.mralex1810.fantasy.entity.Rarity
import java.time.Instant

data class AdminCardMergePageDto(
    val content: List<AdminCardMergeListItemDto>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
)

data class AdminCardMergeListItemDto(
    val id: Long,
    val telegramUserId: Long,
    val internalTelegramUserId: Long,
    val resultUserCardId: Long,
    val operation: CardMergeOperation,
    val sourceRarity: Rarity,
    val resultRarity: Rarity,
    val fantasyPlayerId: Long,
    val fantasyPlayerNickname: String,
    val telegramUserDisplayName: String?,
    val selectedPerkIds: List<String>,
    val costFantiki: Long,
    val previewId: Long?,
    val createdAt: Instant,
)

data class AdminCardMergeDetailDto(
    val id: Long,
    val telegramUserId: Long,
    val internalTelegramUserId: Long,
    val resultUserCardId: Long,
    val operation: CardMergeOperation,
    val sourceRarity: Rarity,
    val resultRarity: Rarity,
    val fantasyPlayerId: Long,
    val fantasyPlayerNickname: String,
    val telegramUserDisplayName: String?,
    val selectedPerkIds: List<String>,
    val fixedPerkIds: List<String>,
    val offeredPerkIds: List<String>,
    val selectedSkinSourceUserCardId: Long?,
    val resultSkinCode: String?,
    val costFantiki: Long,
    val previewId: Long?,
    val createdAt: Instant,
    val inputs: List<AdminCardMergeInputDto>,
)

data class AdminCardMergeInputDto(
    val inputUserCardId: Long,
    val inputCardTemplateId: Long,
    val inputRarity: Rarity,
    val fantasyPlayerId: Long,
    val fantasyPlayerNickname: String,
    val inputPerkIds: List<String>,
    val inputUsesRemaining: Int,
    val inputTimesRenewed: Int,
    val inputSkinCode: String?,
)
