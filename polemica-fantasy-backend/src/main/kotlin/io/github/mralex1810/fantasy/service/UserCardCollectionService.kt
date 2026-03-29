package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.dto.user.response.UserCardItemDto
import io.github.mralex1810.fantasy.entity.Rarity
import io.github.mralex1810.fantasy.entity.TelegramUser
import io.github.mralex1810.fantasy.repository.UserCardRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserCardCollectionService(
    private val userCardRepository: UserCardRepository,
) {

    @Transactional(readOnly = true)
    fun listCards(user: TelegramUser, tournamentId: Long?, rarity: Rarity?): List<UserCardItemDto> {
        val rows = userCardRepository.findAllForUserFiltered(
            telegramUserId = user.id!!,
            tournamentId = tournamentId,
            rarity = rarity,
        )
        return rows.map { it.toUserCardItemDto() }
    }
}
