package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.dto.admin.response.AdminCardMergeDetailDto
import io.github.mralex1810.fantasy.dto.admin.response.AdminCardMergeInputDto
import io.github.mralex1810.fantasy.dto.admin.response.AdminCardMergeListItemDto
import io.github.mralex1810.fantasy.dto.admin.response.AdminCardMergePageDto
import io.github.mralex1810.fantasy.entity.UserCardMerge
import io.github.mralex1810.fantasy.entity.UserCardMergeInput
import io.github.mralex1810.fantasy.repository.UserCardMergeRepository
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class CardMergeAdminService(
    private val userCardMergeRepository: UserCardMergeRepository,
) {

    @Transactional(readOnly = true)
    fun list(telegramUserId: Long?, resultUserCardId: Long?, pageable: Pageable): AdminCardMergePageDto {
        val page = userCardMergeRepository.findAllFiltered(telegramUserId, resultUserCardId, pageable)
        return AdminCardMergePageDto(
            content = page.content.map { it.toListItemDto() },
            page = page.number,
            size = page.size,
            totalElements = page.totalElements,
            totalPages = page.totalPages,
        )
    }

    @Transactional(readOnly = true)
    fun detail(id: Long): AdminCardMergeDetailDto {
        val merge = userCardMergeRepository.findByIdWithDetails(id)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Card merge not found")
        return merge.toDetailDto()
    }

    private fun UserCardMerge.toListItemDto(): AdminCardMergeListItemDto {
        val user = telegramUser!!
        val fp = fantasyPlayer!!
        return AdminCardMergeListItemDto(
            id = id!!,
            telegramUserId = user.telegramId,
            internalTelegramUserId = user.id!!,
            resultUserCardId = resultUserCard!!.id!!,
            operation = operation,
            sourceRarity = sourceRarity,
            resultRarity = resultRarity,
            fantasyPlayerId = fp.id!!,
            fantasyPlayerNickname = fp.nickname,
            telegramUserDisplayName = user.displayName ?: user.firstName ?: user.username,
            selectedPerkIds = selectedPerkIds?.map { it.asText() } ?: emptyList(),
            costFantiki = costFantiki,
            previewId = preview?.id,
            createdAt = createdAt,
        )
    }

    private fun UserCardMerge.toDetailDto(): AdminCardMergeDetailDto =
        AdminCardMergeDetailDto(
            id = id!!,
            telegramUserId = telegramUser!!.telegramId,
            internalTelegramUserId = telegramUser!!.id!!,
            resultUserCardId = resultUserCard!!.id!!,
            operation = operation,
            sourceRarity = sourceRarity,
            resultRarity = resultRarity,
            fantasyPlayerId = fantasyPlayer!!.id!!,
            fantasyPlayerNickname = fantasyPlayer!!.nickname,
            telegramUserDisplayName = telegramUser!!.displayName ?: telegramUser!!.firstName ?: telegramUser!!.username,
            selectedPerkIds = selectedPerkIds?.map { it.asText() } ?: emptyList(),
            fixedPerkIds = fixedPerkIds?.map { it.asText() } ?: emptyList(),
            offeredPerkIds = offeredPerkIds?.map { it.asText() } ?: emptyList(),
            selectedSkinSourceUserCardId = selectedSkinSourceUserCardId,
            resultSkinCode = resultSkinCode,
            costFantiki = costFantiki,
            previewId = preview?.id,
            createdAt = createdAt,
            inputs = inputs.sortedBy { it.inputUserCard!!.id!! }.map { it.toDto() },
        )

    private fun UserCardMergeInput.toDto(): AdminCardMergeInputDto {
        val fp = inputCardTemplate!!.fantasyPlayer!!
        return AdminCardMergeInputDto(
            inputUserCardId = inputUserCard!!.id!!,
            inputCardTemplateId = inputCardTemplate!!.id!!,
            inputRarity = inputRarity,
            fantasyPlayerId = fp.id!!,
            fantasyPlayerNickname = fp.nickname,
            inputPerkIds = inputPerkIds?.map { it.asText() } ?: emptyList(),
            inputUsesRemaining = inputUsesRemaining,
            inputTimesRenewed = inputTimesRenewed,
            inputSkinCode = inputSkinCode,
        )
    }
}
