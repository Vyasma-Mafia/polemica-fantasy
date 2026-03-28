package io.github.mralex1810.fantasy.repository

import io.github.mralex1810.fantasy.entity.FantasyTeamCard
import org.springframework.data.jpa.repository.JpaRepository

interface FantasyTeamCardRepository : JpaRepository<FantasyTeamCard, Long> {
    fun deleteAllByFantasyTeam_Id(fantasyTeamId: Long)
}
