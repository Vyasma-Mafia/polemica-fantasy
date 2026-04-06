package io.github.mralex1810.fantasy.repository

import io.github.mralex1810.fantasy.entity.SeriesGame
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface SeriesGameRepository : JpaRepository<SeriesGame, Long> {
    fun findAllBySeries_Id(seriesId: Long): List<SeriesGame>

    fun findBySeries_IdAndPolemicaGameId(seriesId: Long, polemicaGameId: Long): SeriesGame?

    @Query("SELECT g.series.id, COUNT(g) FROM SeriesGame g WHERE g.series.id IN :ids GROUP BY g.series.id")
    fun countTotalBySeriesIdIn(@Param("ids") ids: Collection<Long>): List<Array<Any>>

    @Query(
        "SELECT g.series.id, COUNT(g) FROM SeriesGame g WHERE g.series.id IN :ids AND g.scored = true GROUP BY g.series.id",
    )
    fun countScoredBySeriesIdIn(@Param("ids") ids: Collection<Long>): List<Array<Any>>
}
