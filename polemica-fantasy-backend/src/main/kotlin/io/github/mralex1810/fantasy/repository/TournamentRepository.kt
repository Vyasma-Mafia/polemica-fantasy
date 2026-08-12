package io.github.mralex1810.fantasy.repository

import io.github.mralex1810.fantasy.entity.Tournament
import io.github.mralex1810.fantasy.entity.TournamentStatus
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface TournamentRepository : JpaRepository<Tournament, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Tournament t WHERE t.id = :id")
    fun findByIdForUpdate(@Param("id") id: Long): Tournament?

    fun findAllByStatusOrderByIdAsc(status: TournamentStatus): List<Tournament>

    fun findAllByStatusOrderByIdDesc(status: TournamentStatus): List<Tournament>

    fun existsByPolemicaCompetitionId(polemicaCompetitionId: Long): Boolean

    fun existsByPolemicaCompetitionIdAndIdNot(polemicaCompetitionId: Long, id: Long): Boolean
}
