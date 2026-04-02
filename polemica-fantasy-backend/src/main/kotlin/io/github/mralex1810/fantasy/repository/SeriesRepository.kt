package io.github.mralex1810.fantasy.repository

import io.github.mralex1810.fantasy.entity.Series
import io.github.mralex1810.fantasy.entity.SeriesStatus
import org.springframework.data.jpa.repository.JpaRepository

interface SeriesRepository : JpaRepository<Series, Long> {
    fun findByIdAndTournament_Id(id: Long, tournamentId: Long): Series?

    fun findAllByTournament_IdOrderByIdDesc(tournamentId: Long): List<Series>

    fun countByTournament_Id(tournamentId: Long): Long

    fun findAllByStatusInAndFinalizedIsFalse(statuses: Collection<SeriesStatus>): List<Series>
}
