package io.github.mralex1810.fantasy.repository

import io.github.mralex1810.fantasy.entity.SeriesPlayer
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface SeriesPlayerRepository : JpaRepository<SeriesPlayer, Long> {
    fun findAllBySeries_Id(seriesId: Long): List<SeriesPlayer>

    fun findAllBySeries_IdIn(seriesIds: Collection<Long>): List<SeriesPlayer>

    @Query(
        """
        SELECT sp FROM SeriesPlayer sp
        JOIN FETCH sp.tournamentPlayer tp
        JOIN FETCH tp.fantasyPlayer
        WHERE sp.series.id = :seriesId
        ORDER BY tp.id
        """,
    )
    fun findAllBySeries_IdWithTournamentPlayers(@Param("seriesId") seriesId: Long): List<SeriesPlayer>

    fun existsBySeries_IdAndTournamentPlayer_Id(seriesId: Long, tournamentPlayerId: Long): Boolean

    fun existsByTournamentPlayer_Id(tournamentPlayerId: Long): Boolean

    fun existsBySeries_IdAndTournamentPlayer_FantasyPlayer_Id(seriesId: Long, fantasyPlayerId: Long): Boolean

    fun deleteAllBySeries_Id(seriesId: Long)
}
