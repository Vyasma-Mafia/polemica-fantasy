package io.github.mralex1810.fantasy.repository

import io.github.mralex1810.fantasy.entity.CardPackPerk
import io.github.mralex1810.fantasy.entity.CardPackPerkId
import org.springframework.data.jpa.repository.JpaRepository

interface CardPackPerkRepository : JpaRepository<CardPackPerk, CardPackPerkId> {
    fun findAllByCardPackId(cardPackId: Long): List<CardPackPerk>
    fun deleteAllByCardPackId(cardPackId: Long)
}
