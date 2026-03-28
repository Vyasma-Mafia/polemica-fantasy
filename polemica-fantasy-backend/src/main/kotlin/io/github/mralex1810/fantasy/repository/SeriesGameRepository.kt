package io.github.mralex1810.fantasy.repository

import io.github.mralex1810.fantasy.entity.SeriesGame
import org.springframework.data.jpa.repository.JpaRepository

interface SeriesGameRepository : JpaRepository<SeriesGame, Long> {
    fun findAllBySeries_Id(seriesId: Long): List<SeriesGame>

    fun findBySeries_IdAndPolemicaGameId(seriesId: Long, polemicaGameId: Long): SeriesGame?
}
