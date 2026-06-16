package io.github.mralex1810.fantasy.repository

import io.github.mralex1810.fantasy.entity.FantasyTeamCardGameScore
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface FantasyTeamCardGameScoreRepository : JpaRepository<FantasyTeamCardGameScore, Long> {

    @Query(
        """
        SELECT gs FROM FantasyTeamCardGameScore gs
        JOIN FETCH gs.seriesGame
        JOIN FETCH gs.fantasyTeamCard ftc
        WHERE ftc.fantasyTeam.id = :teamId
        """,
    )
    fun findAllByFantasyTeamId(@Param("teamId") teamId: Long): List<FantasyTeamCardGameScore>

    @Modifying
    @Query("DELETE FROM FantasyTeamCardGameScore gs WHERE gs.seriesGame.id = :seriesGameId")
    fun deleteAllBySeriesGameId(@Param("seriesGameId") seriesGameId: Long): Int

    @Query(
        """
        SELECT gs.fantasyTeamCard.id, SUM(gs.totalScore)
        FROM FantasyTeamCardGameScore gs
        WHERE gs.fantasyTeamCard.id IN :cardIds
        GROUP BY gs.fantasyTeamCard.id
        """,
    )
    fun sumTotalScoreByFantasyTeamCardIdIn(@Param("cardIds") cardIds: Collection<Long>): List<Array<Any>>
}
