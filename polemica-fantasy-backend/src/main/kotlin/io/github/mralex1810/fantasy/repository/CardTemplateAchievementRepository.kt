package io.github.mralex1810.fantasy.repository

import io.github.mralex1810.fantasy.entity.CardTemplateAchievement
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface CardTemplateAchievementRepository : JpaRepository<CardTemplateAchievement, Long> {

    @Query("SELECT ach.id FROM CardTemplateAchievement cta JOIN cta.achievement ach WHERE cta.cardTemplate.id = :templateId")
    fun findAchievementIdsByCardTemplateId(@Param("templateId") templateId: Long): List<String>

    fun existsByCardTemplate_IdAndAchievement_Id(cardTemplateId: Long, achievementId: String): Boolean
}
