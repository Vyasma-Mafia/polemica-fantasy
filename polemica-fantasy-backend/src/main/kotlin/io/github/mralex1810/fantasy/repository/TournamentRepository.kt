package io.github.mralex1810.fantasy.repository

import io.github.mralex1810.fantasy.entity.Tournament
import io.github.mralex1810.fantasy.entity.TournamentStatus
import org.springframework.data.jpa.repository.JpaRepository

interface TournamentRepository : JpaRepository<Tournament, Long> {
    fun findAllByStatusOrderByIdAsc(status: TournamentStatus): List<Tournament>

    fun findAllByStatusOrderByIdDesc(status: TournamentStatus): List<Tournament>

    fun existsByPolemicaCompetitionId(polemicaCompetitionId: Long): Boolean

    fun existsByPolemicaCompetitionIdAndIdNot(polemicaCompetitionId: Long, id: Long): Boolean
}
