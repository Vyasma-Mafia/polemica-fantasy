package io.github.mralex1810.fantasy.repository

import io.github.mralex1810.fantasy.entity.TournamentStreamLink
import org.springframework.data.jpa.repository.JpaRepository

interface TournamentStreamLinkRepository : JpaRepository<TournamentStreamLink, Long> {
    fun findAllByTournament_IdOrderByDisplayOrderAscIdAsc(tournamentId: Long): List<TournamentStreamLink>

    fun findAllByTournament_IdInOrderByTournament_IdAscDisplayOrderAscIdAsc(
        tournamentIds: Collection<Long>,
    ): List<TournamentStreamLink>

    fun deleteAllByTournament_Id(tournamentId: Long)
}
