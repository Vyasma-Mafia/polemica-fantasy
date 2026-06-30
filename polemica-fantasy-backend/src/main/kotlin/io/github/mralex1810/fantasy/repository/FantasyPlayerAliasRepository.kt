package io.github.mralex1810.fantasy.repository

import io.github.mralex1810.fantasy.entity.FantasyPlayerAlias
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface FantasyPlayerAliasRepository : JpaRepository<FantasyPlayerAlias, Long> {
    fun findByPolemicaUserId(polemicaUserId: Long): FantasyPlayerAlias?

    @Query(
        """
        SELECT a FROM FantasyPlayerAlias a
        JOIN FETCH a.fantasyPlayer
        WHERE a.polemicaUserId = :polemicaUserId
        """,
    )
    fun findByPolemicaUserIdWithFantasyPlayer(@Param("polemicaUserId") polemicaUserId: Long): FantasyPlayerAlias?

    fun existsByPolemicaUserId(polemicaUserId: Long): Boolean

    fun findAllByFantasyPlayer_IdOrderByPrimaryAliasDescPolemicaUserIdAsc(fantasyPlayerId: Long): List<FantasyPlayerAlias>

    @Query(
        """
        SELECT a FROM FantasyPlayerAlias a
        JOIN FETCH a.fantasyPlayer fp
        WHERE fp.id IN :fantasyPlayerIds
        ORDER BY fp.id, a.primaryAlias DESC, a.polemicaUserId ASC
        """,
    )
    fun findAllByFantasyPlayerIds(@Param("fantasyPlayerIds") fantasyPlayerIds: Collection<Long>): List<FantasyPlayerAlias>

    @Query(
        """
        SELECT a.polemicaUserId FROM FantasyPlayerAlias a
        WHERE a.fantasyPlayer.id = :fantasyPlayerId
        ORDER BY a.primaryAlias DESC, a.polemicaUserId ASC
        """,
    )
    fun findPolemicaUserIdsByFantasyPlayerId(@Param("fantasyPlayerId") fantasyPlayerId: Long): List<Long>

    @Modifying
    @Query("UPDATE FantasyPlayerAlias a SET a.primaryAlias = false WHERE a.fantasyPlayer.id = :fantasyPlayerId")
    fun clearPrimaryAlias(@Param("fantasyPlayerId") fantasyPlayerId: Long): Int
}
