package io.github.mralex1810.fantasy.repository

import io.github.mralex1810.fantasy.entity.FantasyTeamCard
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface FantasyTeamCardRepository : JpaRepository<FantasyTeamCard, Long> {
    fun findAllByFantasyTeam_Id(fantasyTeamId: Long): List<FantasyTeamCard>

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
