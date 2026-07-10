package io.github.mralex1810.fantasy.repository

import io.github.mralex1810.fantasy.entity.FantasyTeam
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface FantasyTeamRepository : JpaRepository<FantasyTeam, Long> {

    /**
     * Teams with cards/templates for scoring. Does not JOIN FETCH template perks: Hibernate
     * forbids multiple bag FETCH joins (`FantasyTeam.cards` + `CardTemplate.perks`).
     * Perks load lazily in the same transaction; `CardTemplate.perks` is `@BatchSize`.
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

    fun existsByTelegramUser_IdAndSeries_Id(telegramUserId: Long, seriesId: Long): Boolean

    fun findByTelegramUser_IdAndSeriesLeague_Id(telegramUserId: Long, seriesLeagueId: Long): FantasyTeam?

    fun countByTelegramUser_Id(telegramUserId: Long): Long

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
        JOIN FETCH ft.series s
        JOIN FETCH s.tournament
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
        LEFT JOIN FETCH c.userCard uc
        LEFT JOIN FETCH uc.cardTemplate ct
        LEFT JOIN FETCH ct.fantasyPlayer fp
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

    @Query(
        """
        SELECT DISTINCT ft FROM FantasyTeam ft
        JOIN FETCH ft.series s
        JOIN FETCH s.tournament t
        JOIN FETCH ft.seriesLeague sl
        JOIN FETCH sl.league l
        WHERE ft.telegramUser.id = :userId
        ORDER BY s.startsAt DESC
        """,
    )
    fun findAllByUserIdWithSeriesAndLeague(@Param("userId") userId: Long): List<FantasyTeam>

    @Query(
        value = """
        SELECT ranked.league_code, ranked.league_name, COUNT(*) AS wins_count
        FROM (
            SELECT
                ft.telegram_user_id,
                l.code AS league_code,
                l.name AS league_name,
                ROW_NUMBER() OVER (
                    PARTITION BY ft.series_league_id
                    ORDER BY ft.total_score DESC NULLS LAST, ft.id ASC
                ) AS winner_rank
            FROM fantasy_team ft
            JOIN series s ON s.id = ft.series_id
            JOIN series_league sl ON sl.id = ft.series_league_id
            JOIN league l ON l.id = sl.league_id
            WHERE s.status = 'FINISHED'
              AND ft.total_score IS NOT NULL
        ) ranked
        WHERE ranked.telegram_user_id = :userId
          AND ranked.winner_rank = 1
        GROUP BY ranked.league_code, ranked.league_name
        ORDER BY ranked.league_code
        """,
        nativeQuery = true,
    )
    fun countFinishedSeriesWinsByUserGroupedByLeague(@Param("userId") userId: Long): List<Array<Any>>
}
