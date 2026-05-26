package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.dto.user.response.CardOwnershipHistoryEntryDto
import io.github.mralex1810.fantasy.entity.CardAcquisitionType
import io.github.mralex1810.fantasy.entity.TelegramUser
import io.github.mralex1810.fantasy.entity.UserCard
import io.github.mralex1810.fantasy.entity.UserCardOwnershipHistory
import io.github.mralex1810.fantasy.repository.UserCardOwnershipHistoryRepository
import io.github.mralex1810.fantasy.repository.UserCardRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

@Service
class UserCardOwnershipService(
    private val userCardOwnershipHistoryRepository: UserCardOwnershipHistoryRepository,
    private val userCardRepository: UserCardRepository,
) {

    @Transactional(readOnly = true)
    fun listOwnershipHistory(userCardId: Long): List<CardOwnershipHistoryEntryDto> {
        if (!userCardRepository.existsById(userCardId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Card not found")
        }
        return userCardOwnershipHistoryRepository.findAllByUserCard_IdOrderByAcquiredAtAsc(userCardId).map { row ->
            CardOwnershipHistoryEntryDto(
                ownerDisplayName = row.telegramUser!!.publicDisplayName(),
                acquisitionType = row.acquisitionType,
                acquisitionLabel = acquisitionLabelRu(row.acquisitionType),
                acquiredAt = row.acquiredAt,
            )
        }
    }

    private fun acquisitionLabelRu(type: CardAcquisitionType): String =
        when (type) {
            CardAcquisitionType.PACK_OPENING -> "из пака"
            CardAcquisitionType.ADMIN_GRANT -> "выдача админом"
            CardAcquisitionType.MARKETPLACE_PURCHASE -> "куплена на маркетплейсе"
            CardAcquisitionType.ACHIEVEMENT_REWARD -> "награда за достижение"
        }

    @Transactional
    fun recordAcquisition(
        userCard: UserCard,
        telegramUser: TelegramUser,
        acquisitionType: CardAcquisitionType,
        acquiredAt: Instant = Instant.now(),
    ) {
        userCardOwnershipHistoryRepository.save(
            UserCardOwnershipHistory(
                userCard = userCard,
                telegramUser = telegramUser,
                acquiredAt = acquiredAt,
                acquisitionType = acquisitionType,
            ),
        )
    }
}
