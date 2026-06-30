package io.github.mralex1810.fantasy.repository

import io.github.mralex1810.fantasy.entity.FantasyPlayer
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional

interface FantasyPlayerRepository : JpaRepository<FantasyPlayer, Long> {
    fun findByPolemicaUserId(polemicaUserId: Long): FantasyPlayer?

    @Query("SELECT a.polemicaUserId FROM FantasyPlayerAlias a")
    fun findAllPolemicaUserIds(): List<Long>

    @Query(
        """
        SELECT DISTINCT f FROM FantasyPlayer f
        LEFT JOIN FETCH f.aliases
        ORDER BY f.nickname ASC
        """,
    )
    fun findAllWithAliases(): List<FantasyPlayer>

    @Query("SELECT f FROM FantasyPlayer f LEFT JOIN FETCH f.aliases WHERE f.id = :id")
    fun findByIdWithAliases(@Param("id") id: Long): FantasyPlayer?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM FantasyPlayer f WHERE f.id = :id")
    fun findByIdForUpdate(@Param("id") id: Long): Optional<FantasyPlayer>
}
