package io.github.mralex1810.fantasy.repository

import io.github.mralex1810.fantasy.entity.CardSkin
import org.springframework.data.jpa.repository.JpaRepository

interface CardSkinRepository : JpaRepository<CardSkin, Long> {
    fun findAllByOrderByIdAsc(): List<CardSkin>
}
