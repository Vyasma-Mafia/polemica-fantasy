package io.github.mralex1810.fantasy.repository

import io.github.mralex1810.fantasy.entity.FantasyTeamCard
import org.springframework.data.jpa.repository.JpaRepository

interface FantasyTeamCardRepository : JpaRepository<FantasyTeamCard, Long> {
    fun findAllByFantasyTeam_Id(fantasyTeamId: Long): List<FantasyTeamCard>
}
