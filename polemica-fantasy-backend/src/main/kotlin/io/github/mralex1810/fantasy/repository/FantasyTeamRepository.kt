package io.github.mralex1810.fantasy.repository

import io.github.mralex1810.fantasy.entity.FantasyTeam
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface FantasyTeamRepository : JpaRepository<FantasyTeam, Long> {

    /**
     * Teams with cards/templates for scoring. Does not JOIN FETCH template achievements: Hibernate
     * forbids multiple bag FETCH joins (`FantasyTeam.cards` + `CardTemplate.achievements`).
     * Achievements load lazily in the same transaction; `CardTemplate.achievements` is `@BatchSize`.
     */
    @Query(
        """
        SELECT DISTINCT ft FROM FantasyTeam ft
        JOIN FETCH ft.cards c
        JOIN FETCH c.userCard uc
        JOIN FETCH uc.cardTemplate ct
        JOIN FETCH ct.fantasyPlayer fp
        WHERE ft.series.id = :seriesId
        """,
    )
    fun findAllWithCardsForScoring(@Param("seriesId") seriesId: Long): List<FantasyTeam>

    fun findByTelegramUser_IdAndSeries_Id(telegramUserId: Long, seriesId: Long): FantasyTeam?

    fun findByTelegramUser_IdAndSeriesLeague_Id(telegramUserId: Long, seriesLeagueId: Long): FantasyTeam?

    @Query(
        """
        SELECT DISTINCT ft FROM FantasyTeam ft
        JOIN FETCH ft.seriesLeague sl
        JOIN FETCH sl.league
        LEFT JOIN FETCH ft.cards c
        LEFT JOIN FETCH c.userCard uc
        LEFT JOIN FETCH uc.cardTemplate ct
        LEFT JOIN FETCH ct.fantasyPlayer fp
        WHERE ft.telegramUser.id = :telegramUserId
        ORDER BY ft.submittedAt DESC
        """,
    )
    fun findAllByTelegramUser_IdWithCards(@Param("telegramUserId") telegramUserId: Long): List<FantasyTeam>

    @Query(
        """
        SELECT DISTINCT ft FROM FantasyTeam ft
        JOIN FETCH ft.seriesLeague sl
        JOIN FETCH sl.league l
        LEFT JOIN FETCH ft.cards c
        LEFT JOIN FETCH c.userCard uc
        LEFT JOIN FETCH uc.cardTemplate ct
        LEFT JOIN FETCH ct.fantasyPlayer fp
        WHERE ft.telegramUser.id = :telegramUserId
          AND ft.series.id = :seriesId
          AND l.code = :leagueCode
        """,
    )
    fun findByUserAndSeriesAndLeagueCodeWithCards(
        @Param("telegramUserId") telegramUserId: Long,
        @Param("seriesId") seriesId: Long,
        @Param("leagueCode") leagueCode: String,
    ): FantasyTeam?

    @Query(
        """
        SELECT DISTINCT ft FROM FantasyTeam ft
        JOIN FETCH ft.telegramUser u
        JOIN FETCH ft.cards c
        WHERE ft.series.id = :seriesId
        ORDER BY ft.totalScore DESC NULLS LAST, ft.id ASC
        """,
    )
    fun findLeaderboardForSeries(@Param("seriesId") seriesId: Long): List<FantasyTeam>

    @Query(
        """
        SELECT DISTINCT ft FROM FantasyTeam ft
        JOIN FETCH ft.telegramUser u
        JOIN FETCH ft.cards c
        WHERE ft.seriesLeague.id = :seriesLeagueId
        ORDER BY ft.totalScore DESC NULLS LAST, ft.id ASC
        """,
    )
    fun findLeaderboardForSeriesLeague(@Param("seriesLeagueId") seriesLeagueId: Long): List<FantasyTeam>

    @Query(
        """
        SELECT DISTINCT ft FROM FantasyTeam ft
        JOIN FETCH ft.seriesLeague sl
        JOIN FETCH sl.league
        JOIN FETCH ft.telegramUser
        LEFT JOIN FETCH ft.cards c
        LEFT JOIN FETCH c.userCard uc
        LEFT JOIN FETCH uc.cardTemplate ct
        LEFT JOIN FETCH ct.fantasyPlayer fp
        WHERE ft.series.id = :seriesId
        """,
    )
    fun findAllBySeries_IdWithCards(@Param("seriesId") seriesId: Long): List<FantasyTeam>

    fun countBySeriesLeague_Id(seriesLeagueId: Long): Long
}
