package io.github.mralex1810.fantasy.repository

import io.github.mralex1810.fantasy.entity.CardPackAchievement
import io.github.mralex1810.fantasy.entity.CardPackAchievementId
import org.springframework.data.jpa.repository.JpaRepository

interface CardPackAchievementRepository : JpaRepository<CardPackAchievement, CardPackAchievementId> {
    fun findAllByCardPackId(cardPackId: Long): List<CardPackAchievement>
    fun deleteAllByCardPackId(cardPackId: Long)
}
