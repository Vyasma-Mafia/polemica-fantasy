package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.dto.user.response.CardAchievementBriefDto
import io.github.mralex1810.fantasy.dto.user.response.UserCardItemDto
import io.github.mralex1810.fantasy.entity.UserCard

fun UserCard.toUserCardItemDto(): UserCardItemDto {
    val ct = cardTemplate!!
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
