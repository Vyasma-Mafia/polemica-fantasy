package io.github.mralex1810.fantasy.repository

import io.github.mralex1810.fantasy.entity.TournamentPlayer
import org.springframework.data.jpa.repository.JpaRepository

interface TournamentPlayerRepository : JpaRepository<TournamentPlayer, Long> {
    fun findByIdAndTournament_Id(id: Long, tournamentId: Long): TournamentPlayer?

    fun existsByTournament_IdAndFantasyPlayer_Id(tournamentId: Long, fantasyPlayerId: Long): Boolean

    fun findAllByTournament_IdOrderById(tournamentId: Long): List<TournamentPlayer>
}
