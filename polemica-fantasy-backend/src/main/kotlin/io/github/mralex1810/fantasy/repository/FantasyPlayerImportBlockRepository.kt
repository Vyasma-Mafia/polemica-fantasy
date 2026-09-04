package io.github.mralex1810.fantasy.repository

import io.github.mralex1810.fantasy.entity.FantasyPlayerImportBlock
import org.springframework.data.jpa.repository.JpaRepository

interface FantasyPlayerImportBlockRepository : JpaRepository<FantasyPlayerImportBlock, Long> {
    fun findFirstByPolemicaUserIdIn(polemicaUserIds: Collection<Long>): FantasyPlayerImportBlock?
}
