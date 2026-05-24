package io.github.mralex1810.fantasy.repository

import io.github.mralex1810.fantasy.entity.CardTemplatePerk
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface CardTemplatePerkRepository : JpaRepository<CardTemplatePerk, Long> {

    @Query("SELECT perk.id FROM CardTemplatePerk cta JOIN cta.perk perk WHERE cta.cardTemplate.id = :templateId")
    fun findPerkIdsByCardTemplateId(@Param("templateId") templateId: Long): List<String>

    fun existsByCardTemplate_IdAndPerk_Id(cardTemplateId: Long, perkId: String): Boolean
}
