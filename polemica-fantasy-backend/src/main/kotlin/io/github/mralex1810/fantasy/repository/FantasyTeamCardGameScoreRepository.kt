package io.github.mralex1810.fantasy.repository

import io.github.mralex1810.fantasy.entity.FantasyTeamCardGameScore
import org.springframework.data.jpa.repository.JpaRepository
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
}
