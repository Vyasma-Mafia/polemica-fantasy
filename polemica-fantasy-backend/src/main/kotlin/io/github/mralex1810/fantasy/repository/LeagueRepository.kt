package io.github.mralex1810.fantasy.repository

import io.github.mralex1810.fantasy.entity.League
import io.github.mralex1810.fantasy.entity.LeagueType
import org.springframework.data.jpa.repository.JpaRepository

interface LeagueRepository : JpaRepository<League, Long> {
    fun findByCode(code: String): League?

    fun findAllByLeagueType(type: LeagueType): List<League>
}
