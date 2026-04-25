package io.github.mralex1810.fantasy.repository

import io.github.mralex1810.fantasy.entity.MarketplaceWatchFilter
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface MarketplaceWatchFilterRepository : JpaRepository<MarketplaceWatchFilter, Long> {
    fun findAllByTelegramUser_IdOrderByCreatedAtDesc(telegramUserId: Long): List<MarketplaceWatchFilter>

    fun countByTelegramUser_Id(telegramUserId: Long): Int

    fun deleteByIdAndTelegramUser_Id(id: Long, telegramUserId: Long): Int

    @Query(
        nativeQuery = true,
        value =
            """
            SELECT DISTINCT mwf.telegram_user_id
            FROM marketplace_watch_filter mwf
            JOIN telegram_user tu ON tu.id = mwf.telegram_user_id
            WHERE tu.bot_blocked = FALSE
              AND mwf.telegram_user_id != :sellerId
              AND (mwf.fantasy_player_id IS NULL OR mwf.fantasy_player_id = :fantasyPlayerId)
              AND (mwf.rarity IS NULL OR mwf.rarity = :rarity)
              AND (mwf.max_price IS NULL OR mwf.max_price >= :price)
              AND (mwf.tournament_id IS NULL OR mwf.tournament_id IN (:tournamentIds))
              AND NOT EXISTS (
                  SELECT 1 FROM notification_preference np
                  WHERE np.telegram_user_id = mwf.telegram_user_id
                    AND np.category = 'MARKETPLACE_WATCH'
                    AND np.enabled = FALSE
              )
            """,
    )
    fun findMatchingUserIds(
        @Param("sellerId") sellerId: Long,
        @Param("fantasyPlayerId") fantasyPlayerId: Long,
        @Param("rarity") rarity: String,
        @Param("price") price: Long,
        @Param("tournamentIds") tournamentIds: List<Long>,
    ): List<Long>
}
