package io.github.mralex1810.fantasy.repository

import io.github.mralex1810.fantasy.entity.CardPack
import org.springframework.data.jpa.repository.JpaRepository

interface CardPackRepository : JpaRepository<CardPack, Long> {
    fun findAllByOrderByIdAsc(): List<CardPack>

    fun findAllByTournament_IdOrderByIdAsc(tournamentId: Long): List<CardPack>
}
