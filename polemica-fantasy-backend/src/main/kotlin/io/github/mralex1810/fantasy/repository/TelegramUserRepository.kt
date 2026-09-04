package io.github.mralex1810.fantasy.repository

import io.github.mralex1810.fantasy.entity.TelegramUser
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface TelegramUserRepository : JpaRepository<TelegramUser, Long> {
    @Query("SELECT u.telegramId FROM TelegramUser u WHERE u.isAutomatedAgent = false ORDER BY u.id")
    fun findAllTelegramIds(): List<Long>

    fun findByTelegramId(telegramId: Long): TelegramUser?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM TelegramUser u WHERE u.id = :id")
    fun findByIdForUpdate(@Param("id") id: Long): TelegramUser?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM TelegramUser u WHERE u.telegramId = :telegramId")
    fun findByTelegramIdForUpdate(@Param("telegramId") telegramId: Long): TelegramUser?

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE TelegramUser u SET u.fantiki = u.fantiki + :amount WHERE u.id = :id")
    fun addFantiki(@Param("id") id: Long, @Param("amount") amount: Long): Int

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        "UPDATE TelegramUser u SET u.fantiki = u.fantiki - :amount WHERE u.id = :id AND u.fantiki >= :amount",
    )
    fun deductFantikiIfSufficient(@Param("id") id: Long, @Param("amount") amount: Long): Int

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE TelegramUser u SET u.fantiki = u.fantiki - :amount WHERE u.id = :id")
    fun forceDeductFantiki(@Param("id") id: Long, @Param("amount") amount: Long): Int

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE TelegramUser u SET u.packOpensCount = u.packOpensCount + 1 WHERE u.id = :id")
    fun incrementPackOpensCount(@Param("id") id: Long): Int

    /**
     * All telegram users with count of user_card rows whose template's fantasy_player
     * is on this series (same rule as [io.github.mralex1810.fantasy.repository.UserCardRepository.findAllForUserFiltered]).
     */
    @Query(
        value =
            """
            SELECT tu.id, tu.telegram_id, tu.username, tu.display_name, tu.fantiki, tu.bot_blocked, COUNT(uc.id)
            FROM telegram_user tu
            LEFT JOIN user_card uc ON uc.telegram_user_id = tu.id
              AND uc.deleted_at IS NULL
              AND EXISTS (
                SELECT 1 FROM card_template ct
                INNER JOIN series_player sp ON sp.series_id = :seriesId
                INNER JOIN tournament_player tp ON tp.id = sp.tournament_player_id
                WHERE ct.id = uc.card_template_id
                  AND tp.fantasy_player_id = ct.fantasy_player_id
              )
            GROUP BY tu.id, tu.telegram_id, tu.username, tu.display_name, tu.fantiki, tu.bot_blocked
            ORDER BY tu.id
            """,
        nativeQuery = true,
    )
    fun findAllWithCardsInSeriesCount(@Param("seriesId") seriesId: Long): List<Array<Any>>

    @Query(
        value =
            """
            SELECT u.id, u.telegram_id, u.username, u.first_name, u.display_name, u.created_at, u.fantiki,
              u.pack_opens_count, u.marketplace_banned, u.marketplace_banned_until, u.bot_blocked,
              u.is_automated_agent
            FROM telegram_user u
            WHERE
              (u.username IS NOT NULL AND u.username ILIKE :pattern ESCAPE '!')
              OR (u.display_name IS NOT NULL AND u.display_name ILIKE :pattern ESCAPE '!')
              OR (CAST(u.telegram_id AS text) ILIKE :pattern ESCAPE '!')
            ORDER BY u.id
            """,
        nativeQuery = true,
    )
    fun findAllMatchingQOrderedById(@Param("pattern") pattern: String): List<TelegramUser>

    @Query(
        value =
            """
            SELECT tu.id, tu.telegram_id, tu.username, tu.display_name, tu.fantiki, tu.bot_blocked, COUNT(uc.id)
            FROM telegram_user tu
            LEFT JOIN user_card uc ON uc.telegram_user_id = tu.id
              AND uc.deleted_at IS NULL
              AND EXISTS (
                SELECT 1 FROM card_template ct
                INNER JOIN series_player sp ON sp.series_id = :seriesId
                INNER JOIN tournament_player tp ON tp.id = sp.tournament_player_id
                WHERE ct.id = uc.card_template_id
                  AND tp.fantasy_player_id = ct.fantasy_player_id
              )
            WHERE
              (tu.username IS NOT NULL AND tu.username ILIKE :pattern ESCAPE '!')
              OR (tu.display_name IS NOT NULL AND tu.display_name ILIKE :pattern ESCAPE '!')
              OR (CAST(tu.telegram_id AS text) ILIKE :pattern ESCAPE '!')
            GROUP BY tu.id, tu.telegram_id, tu.username, tu.display_name, tu.fantiki, tu.bot_blocked
            ORDER BY tu.id
            """,
        nativeQuery = true,
    )
    fun findAllWithCardsInSeriesCountMatching(
        @Param("seriesId") seriesId: Long,
        @Param("pattern") pattern: String,
    ): List<Array<Any>>

    @Query(
        value =
            """
            SELECT tu.*
            FROM telegram_user tu
            WHERE tu.bot_blocked = FALSE
              AND tu.is_automated_agent = FALSE
              AND NOT EXISTS (
                  SELECT 1 FROM notification_preference np
                  WHERE np.telegram_user_id = tu.id
                    AND np.category = 'SERIES_START'
                    AND np.enabled = FALSE
              )
              AND (
                  NOT EXISTS (
                      SELECT 1 FROM tournament_subscription ts WHERE ts.telegram_user_id = tu.id
                  )
                  OR EXISTS (
                      SELECT 1 FROM tournament_subscription ts
                      WHERE ts.telegram_user_id = tu.id AND ts.tournament_id IN (:tournamentIds)
                  )
              )
            ORDER BY tu.id
            """,
        nativeQuery = true,
    )
    fun findAllEligibleForSeriesStart(@Param("tournamentIds") tournamentIds: Collection<Long>): List<TelegramUser>

    @Query(
        value =
            """
            SELECT tu.telegram_id
            FROM telegram_user tu
            WHERE tu.bot_blocked = FALSE
              AND tu.is_automated_agent = FALSE
              AND NOT EXISTS (
                  SELECT 1 FROM notification_preference np
                  WHERE np.telegram_user_id = tu.id
                    AND np.category = 'TEAM_DEADLINE_REMINDER'
                    AND np.enabled = FALSE
              )
              AND NOT EXISTS (
                  SELECT 1 FROM fantasy_team ft
                  WHERE ft.telegram_user_id = tu.id AND ft.series_id = :seriesId
              )
              AND (
                  NOT EXISTS (
                      SELECT 1 FROM tournament_subscription ts WHERE ts.telegram_user_id = tu.id
                  )
                  OR EXISTS (
                      SELECT 1 FROM tournament_subscription ts
                      WHERE ts.telegram_user_id = tu.id AND ts.tournament_id = :tournamentId
                  )
              )
            ORDER BY tu.id
            """,
        nativeQuery = true,
    )
    fun findDeadlineReminderRecipients(
        @Param("seriesId") seriesId: Long,
        @Param("tournamentId") tournamentId: Long,
    ): List<Long>
}
