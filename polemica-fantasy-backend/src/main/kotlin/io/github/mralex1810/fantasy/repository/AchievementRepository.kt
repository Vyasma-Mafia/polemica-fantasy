package io.github.mralex1810.fantasy.repository

import io.github.mralex1810.fantasy.entity.Achievement
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface AchievementRepository : JpaRepository<Achievement, String> {
    fun findAllByCanAppearOnRandomCardsTrueOrderById(): List<Achievement>

    fun findAllByIdIn(ids: Collection<String>): List<Achievement>

    @Query("SELECT DISTINCT a FROM Achievement a LEFT JOIN FETCH a.applicableRoles")
    fun findAllWithApplicableRoles(): List<Achievement>

    @Query("SELECT DISTINCT a FROM Achievement a LEFT JOIN FETCH a.applicableRoles WHERE a.id = :id")
    fun findWithApplicableRolesById(id: String): Achievement?
}
