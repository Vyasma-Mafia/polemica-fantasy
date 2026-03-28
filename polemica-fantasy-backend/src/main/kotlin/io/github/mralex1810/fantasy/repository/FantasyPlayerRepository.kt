package io.github.mralex1810.fantasy.repository

import io.github.mralex1810.fantasy.entity.FantasyPlayer
import org.springframework.data.jpa.repository.JpaRepository

interface FantasyPlayerRepository : JpaRepository<FantasyPlayer, Long> {
    fun findByPolemicaUserId(polemicaUserId: Long): FantasyPlayer?
}
