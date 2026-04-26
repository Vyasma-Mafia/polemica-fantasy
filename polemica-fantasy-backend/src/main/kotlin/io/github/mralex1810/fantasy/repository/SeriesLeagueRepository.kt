package io.github.mralex1810.fantasy.repository

import io.github.mralex1810.fantasy.entity.LeagueType
import io.github.mralex1810.fantasy.entity.SeriesLeague
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface SeriesLeagueRepository : JpaRepository<SeriesLeague, Long> {
    fun findAllBySeries_IdAndEnabledTrue(seriesId: Long): List<SeriesLeague>

    fun findBySeries_IdAndLeague_CodeAndEnabledTrue(seriesId: Long, leagueCode: String): SeriesLeague?

    fun findBySeries_IdAndLeague_Id(seriesId: Long, leagueId: Long): SeriesLeague?

    fun findAllBySeries_IdAndLeague_LeagueTypeAndEnabledTrue(seriesId: Long, leagueType: LeagueType): List<SeriesLeague>

    fun findBySeries_IdAndLeague_IdAndEnabledTrue(seriesId: Long, leagueId: Long): SeriesLeague?

    @Query(
        """
        SELECT sl FROM SeriesLeague sl
        JOIN FETCH sl.league l
        WHERE sl.series.id = :seriesId
          AND sl.enabled = true
        ORDER BY l.id ASC
        """,
    )
    fun findAllEnabledBySeriesIdWithLeague(@Param("seriesId") seriesId: Long): List<SeriesLeague>
}
