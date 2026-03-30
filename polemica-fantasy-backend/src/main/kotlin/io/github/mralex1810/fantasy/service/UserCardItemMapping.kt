package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.dto.user.response.CardAchievementBriefDto
import io.github.mralex1810.fantasy.dto.user.response.UserCardItemDto
import io.github.mralex1810.fantasy.entity.CardTemplate
import io.github.mralex1810.fantasy.entity.UserCard

/**
 * @param templateOverride optional template loaded in the same transaction with achievements FETCH
 * (see [io.github.mralex1810.fantasy.repository.CardTemplateRepository.findAllByIdWithAchievementsLoaded]).
 */
fun UserCard.toUserCardItemDto(templateOverride: CardTemplate? = null): UserCardItemDto {
    val ct = templateOverride ?: cardTemplate!!
    val fp = ct.fantasyPlayer!!
    return UserCardItemDto(
        id = id!!,
        acquiredAt = acquiredAt,
        cardTemplateId = ct.id!!,
        fantasyPlayerId = fp.id!!,
        rarity = ct.rarity,
        imageUrl = ct.imageUrl,
        description = ct.description,
        playerNickname = fp.nickname,
        playerPhotoUrl = fp.photoUrl,
        achievements = ct.achievements.map { a ->
            val def = a.achievement!!
            CardAchievementBriefDto(
                achievementId = def.id,
                achievementName = def.name,
                bonusPoints = a.bonusPoints ?: def.bonusPoints,
            )
        },
    )
}
