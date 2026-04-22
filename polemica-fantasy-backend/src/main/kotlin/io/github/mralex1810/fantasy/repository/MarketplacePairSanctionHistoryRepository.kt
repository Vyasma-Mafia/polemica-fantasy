package io.github.mralex1810.fantasy.repository

import io.github.mralex1810.fantasy.entity.MarketplacePairSanctionHistory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface MarketplacePairSanctionHistoryRepository : JpaRepository<MarketplacePairSanctionHistory, Long> {
    fun findAllByOrderByCreatedAtDesc(pageable: Pageable): Page<MarketplacePairSanctionHistory>
}
