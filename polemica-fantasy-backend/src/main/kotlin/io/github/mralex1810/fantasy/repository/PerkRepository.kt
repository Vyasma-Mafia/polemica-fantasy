package io.github.mralex1810.fantasy.repository

import io.github.mralex1810.fantasy.entity.Perk
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface PerkRepository : JpaRepository<Perk, String> {
    fun findAllByCanAppearOnRandomCardsTrueOrderById(): List<Perk>

    fun findAllByIdIn(ids: Collection<String>): List<Perk>

    @Query("SELECT DISTINCT a FROM Perk a LEFT JOIN FETCH a.applicableRoles")
    fun findAllWithApplicableRoles(): List<Perk>

    @Query("SELECT DISTINCT a FROM Perk a LEFT JOIN FETCH a.applicableRoles WHERE a.id = :id")
    fun findWithApplicableRolesById(id: String): Perk?
}
