package io.github.mralex1810.fantasy.repository

import io.github.mralex1810.fantasy.entity.Series
import io.github.mralex1810.fantasy.entity.SeriesStatus
import io.github.mralex1810.fantasy.entity.TournamentStatus
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface SeriesRepository : JpaRepository<Series, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Series s WHERE s.id = :id")
    fun findByIdForUpdate(@Param("id") id: Long): Series?

    fun findByIdAndTournament_Id(id: Long, tournamentId: Long): Series?

    fun findAllByTournament_IdOrderByIdDesc(tournamentId: Long): List<Series>

    fun findAllByTournament_IdInAndStatusNot(
        tournamentIds: Collection<Long>,
        status: SeriesStatus,
    ): List<Series>

    fun countByTournament_Id(tournamentId: Long): Long

    fun existsByTournament_IdAndPublicNumber(tournamentId: Long, publicNumber: Long): Boolean

    fun existsByTournament_IdAndPublicNumberAndIdNot(tournamentId: Long, publicNumber: Long, id: Long): Boolean

    fun findAllByStatusInAndFinalizedIsFalse(statuses: Collection<SeriesStatus>): List<Series>

    @Query(
        """
        SELECT s FROM Series s
        JOIN FETCH s.tournament t
        WHERE t.status = :tournamentStatus
        AND s.status IN :statuses
        ORDER BY s.startsAt ASC, s.id ASC
        """,
    )
    fun findAllActiveForHome(
        @Param("tournamentStatus") tournamentStatus: TournamentStatus,
        @Param("statuses") statuses: Collection<SeriesStatus>,
    ): List<Series>

    @Query(
        """
        SELECT s FROM Series s
        JOIN FETCH s.tournament
        WHERE s.id = :id
        """,
    )
    fun findByIdWithTournament(@Param("id") id: Long): Series?

    @Query(
        """
        SELECT s FROM Series s
        JOIN FETCH s.tournament t
        WHERE t.status = :tournamentStatus
        AND s.teamDeadline > :now
        AND s.status <> :finishedStatus
        ORDER BY s.teamDeadline ASC
        """,
    )
    fun findAllOpenForTeamSubmission(
        @Param("tournamentStatus") tournamentStatus: TournamentStatus,
        @Param("finishedStatus") finishedStatus: SeriesStatus,
        @Param("now") now: Instant,
    ): List<Series>
}
