package io.github.mralex1810.fantasy.repository

import io.github.mralex1810.fantasy.entity.FantasyPlayer
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface FantasyPlayerRepository : JpaRepository<FantasyPlayer, Long> {
    fun findByPolemicaUserId(polemicaUserId: Long): FantasyPlayer?

    @Query("SELECT f.polemicaUserId FROM FantasyPlayer f")
    fun findAllPolemicaUserIds(): List<Long>
}
