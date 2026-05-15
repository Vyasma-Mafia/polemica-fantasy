package io.github.mralex1810.fantasy.repository

import io.github.mralex1810.fantasy.entity.CardPack
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface CardPackRepository : JpaRepository<CardPack, Long> {
    fun findAllByOrderByIdAsc(): List<CardPack>

    fun findAllByTournament_IdOrderByIdAsc(tournamentId: Long): List<CardPack>

    fun findAllByActiveTrueAndPriceFantikiGreaterThanEqualOrderByIdAsc(minPriceFantiki: Long): List<CardPack>

    @Query("SELECT DISTINCT p FROM CardPack p JOIN FETCH p.rarityConfigs LEFT JOIN FETCH p.cardSkin WHERE p.id = :id")
    fun findByIdWithRarityConfigs(@Param("id") id: Long): CardPack?
}
