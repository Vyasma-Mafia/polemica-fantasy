package io.github.mralex1810.fantasy.repository

import io.github.mralex1810.fantasy.entity.FantasyTeamCard
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface FantasyTeamCardRepository : JpaRepository<FantasyTeamCard, Long> {
    fun findAllByFantasyTeam_Id(fantasyTeamId: Long): List<FantasyTeamCard>

    @Query(
        """
        SELECT COUNT(DISTINCT ft.seriesLeague.id)
        FROM FantasyTeamCard ftc
        JOIN ftc.fantasyTeam ft
        WHERE ftc.userCard.id = :userCardId
          AND ft.series.id = :seriesId
          AND ft.seriesLeague.id <> :excludeSeriesLeagueId
        """,
    )
    fun countLeaguesInSeriesForCard(
        @Param("userCardId") userCardId: Long,
        @Param("seriesId") seriesId: Long,
        @Param("excludeSeriesLeagueId") excludeSeriesLeagueId: Long,
    ): Int

    @Query(
        """
        SELECT COUNT(DISTINCT ft.seriesLeague.id)
        FROM FantasyTeamCard ftc
        JOIN ftc.fantasyTeam ft
        JOIN ft.series s
        WHERE ftc.userCard.id = :userCardId
          AND s.finalized = false
          AND ft.seriesLeague.id <> :excludeSeriesLeagueId
        """,
    )
    fun countReservedLeaguesForCard(
        @Param("userCardId") userCardId: Long,
        @Param("excludeSeriesLeagueId") excludeSeriesLeagueId: Long,
    ): Int

    @Query(
        """
        SELECT ftc.userCard.id, COUNT(DISTINCT ft.seriesLeague.id)
        FROM FantasyTeamCard ftc
        JOIN ftc.fantasyTeam ft
        JOIN ft.series s
        WHERE ftc.userCard.id IN :userCardIds
          AND s.finalized = false
        GROUP BY ftc.userCard.id
        """,
    )
    fun countReservedLeaguesByUserCardIds(
        @Param("userCardIds") userCardIds: Collection<Long>,
    ): List<Array<Any>>

    @Query(
        """
        SELECT ftc.userCard.id, l.code
        FROM FantasyTeamCard ftc
        JOIN ftc.fantasyTeam ft
        JOIN ft.seriesLeague sl
        JOIN sl.league l
        WHERE ft.series.id = :seriesId
          AND ftc.userCard.id IN :userCardIds
        """,
    )
    fun findLeagueCodesBySeriesAndUserCardIds(
        @Param("seriesId") seriesId: Long,
        @Param("userCardIds") userCardIds: Collection<Long>,
    ): List<Array<Any>>

    @Query(
        """
        SELECT ftc.userCard.id, COUNT(DISTINCT ft.seriesLeague.id)
        FROM FantasyTeamCard ftc
        JOIN ftc.fantasyTeam ft
        WHERE ft.series.id = :seriesId
        GROUP BY ftc.userCard.id
        """,
    )
    fun countDistinctLeaguesByUserCardForSeries(@Param("seriesId") seriesId: Long): List<Array<Any>>

    @Query(
        """
        SELECT COUNT(ftc) FROM FantasyTeamCard ftc
        JOIN ftc.fantasyTeam ft
        JOIN ft.series s
        WHERE ftc.userCard.id = :userCardId AND s.finalized = false
        """,
    )
    fun countInNonFinalizedSeries(@Param("userCardId") userCardId: Long): Long

    fun deleteAllByUserCard_Id(userCardId: Long)
}
