package io.github.mralex1810.fantasy.repository

import io.github.mralex1810.fantasy.entity.FantasyTeam
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface FantasyTeamRepository : JpaRepository<FantasyTeam, Long> {

    @Query(
        """
        SELECT DISTINCT ft FROM FantasyTeam ft
        JOIN FETCH ft.cards c
        JOIN FETCH c.userCard uc
        JOIN FETCH uc.cardTemplate ct
        JOIN FETCH ct.fantasyPlayer fp
        LEFT JOIN FETCH ct.achievements
        WHERE ft.series.id = :seriesId
        """,
    )
    fun findAllWithCardsForScoring(@Param("seriesId") seriesId: Long): List<FantasyTeam>

    fun findByTelegramUser_IdAndSeries_Id(telegramUserId: Long, seriesId: Long): FantasyTeam?

    @Query(
        """
        SELECT DISTINCT ft FROM FantasyTeam ft
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
        LEFT JOIN FETCH ft.cards c
        LEFT JOIN FETCH c.userCard uc
        LEFT JOIN FETCH uc.cardTemplate ct
        LEFT JOIN FETCH ct.fantasyPlayer fp
        WHERE ft.telegramUser.id = :telegramUserId AND ft.series.id = :seriesId
        """,
    )
    fun findByUserAndSeriesWithCards(
        @Param("telegramUserId") telegramUserId: Long,
        @Param("seriesId") seriesId: Long,
    ): FantasyTeam?

    @Query(
        """
        SELECT ft FROM FantasyTeam ft
        JOIN FETCH ft.telegramUser u
        WHERE ft.series.id = :seriesId
        ORDER BY ft.totalScore DESC NULLS LAST, ft.id ASC
        """,
    )
    fun findLeaderboardForSeries(@Param("seriesId") seriesId: Long): List<FantasyTeam>
}
