package io.github.mralex1810.fantasy.repository

import io.github.mralex1810.fantasy.entity.CardPackPlayer
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository

interface CardPackPlayerRepository : JpaRepository<CardPackPlayer, Long> {
    fun deleteAllByCardPack_Id(cardPackId: Long)

    @EntityGraph(attributePaths = ["fantasyPlayer"])
    fun findAllByCardPack_Id(cardPackId: Long): List<CardPackPlayer>
}
