package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.dto.user.response.CardAchievementBriefDto
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
        return rows.map { uc ->
            val ct = uc.cardTemplate!!
            val fp = ct.fantasyPlayer!!
            UserCardItemDto(
                id = uc.id!!,
                acquiredAt = uc.acquiredAt,
                cardTemplateId = ct.id!!,
                fantasyPlayerId = fp.id!!,
                rarity = ct.rarity,
                imageUrl = ct.imageUrl,
                description = ct.description,
                playerNickname = fp.nickname,
                playerPhotoUrl = fp.photoUrl,
                achievements = ct.achievements.map { a ->
                    CardAchievementBriefDto(
                        achievementType = a.achievementType,
                        bonusPoints = a.bonusPoints,
                    )
                },
            )
        }
    }
}
