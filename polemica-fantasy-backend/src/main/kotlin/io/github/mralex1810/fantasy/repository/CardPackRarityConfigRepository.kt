package io.github.mralex1810.fantasy.repository

import io.github.mralex1810.fantasy.entity.CardPackRarityConfig
import org.springframework.data.jpa.repository.JpaRepository

interface CardPackRarityConfigRepository : JpaRepository<CardPackRarityConfig, Long> {
    fun deleteAllByCardPack_Id(cardPackId: Long)
}
