package io.github.mralex1810.fantasy.repository

import io.github.mralex1810.fantasy.entity.MarketplaceWatchPending
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface MarketplaceWatchPendingRepository : JpaRepository<MarketplaceWatchPending, Long> {
    fun findAllByTelegramUser_Id(telegramUserId: Long): List<MarketplaceWatchPending>

    fun deleteAllByTelegramUser_Id(telegramUserId: Long)

    @Query("SELECT DISTINCT p.telegramUser.id FROM MarketplaceWatchPending p")
    fun findDistinctUserIds(): List<Long>
}
