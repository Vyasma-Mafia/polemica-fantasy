package io.github.mralex1810.fantasy.repository

import io.github.mralex1810.fantasy.entity.TelegramUser
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface TelegramUserRepository : JpaRepository<TelegramUser, Long> {
    fun findByTelegramId(telegramId: Long): TelegramUser?

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE TelegramUser u SET u.fantiki = u.fantiki + :amount WHERE u.id = :id")
    fun addFantiki(@Param("id") id: Long, @Param("amount") amount: Long): Int

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        "UPDATE TelegramUser u SET u.fantiki = u.fantiki - :amount WHERE u.id = :id AND u.fantiki >= :amount",
    )
    fun deductFantikiIfSufficient(@Param("id") id: Long, @Param("amount") amount: Long): Int

    /**
     * All telegram users with count of user_card rows whose template's fantasy_player
     * is on this series (same rule as [io.github.mralex1810.fantasy.repository.UserCardRepository.findAllForUserFiltered]).
     */
    @Query(
        value =
            """
            SELECT tu.id, tu.telegram_id, tu.username, tu.display_name, COUNT(uc.id)
            FROM telegram_user tu
            LEFT JOIN user_card uc ON uc.telegram_user_id = tu.id
              AND EXISTS (
                SELECT 1 FROM card_template ct
                INNER JOIN series_player sp ON sp.series_id = :seriesId
                INNER JOIN tournament_player tp ON tp.id = sp.tournament_player_id
                WHERE ct.id = uc.card_template_id
                  AND tp.fantasy_player_id = ct.fantasy_player_id
              )
            GROUP BY tu.id, tu.telegram_id, tu.username, tu.display_name
            ORDER BY tu.id
            """,
        nativeQuery = true,
    )
    fun findAllWithCardsInSeriesCount(@Param("seriesId") seriesId: Long): List<Array<Any>>
}
