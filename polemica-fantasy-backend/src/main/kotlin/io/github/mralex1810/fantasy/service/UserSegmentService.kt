package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.entity.NotificationCategory
import io.github.mralex1810.fantasy.entity.ProductAudience
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.Instant

data class AudienceCounts(
    val rawCount: Int,
    val eligibleCount: Int,
)

@Service
class UserSegmentService(
    private val entityManager: EntityManager,
) {
    @Transactional(readOnly = true)
    fun parseAudience(raw: String): ProductAudience = try {
        ProductAudience.valueOf(raw.trim().uppercase())
    } catch (_: IllegalArgumentException) {
        throw org.springframework.web.server.ResponseStatusException(
            org.springframework.http.HttpStatus.BAD_REQUEST,
            "Unknown audience: $raw",
        )
    }

    @Transactional(readOnly = true)
    fun audienceForUser(userId: Long): ProductAudience {
        val row = entityManager.createNativeQuery(
            """
            WITH action_events AS (
              SELECT telegram_user_id AS user_id, submitted_at::timestamptz AS ts FROM fantasy_team
              UNION ALL SELECT telegram_user_id, acquired_at::timestamptz FROM user_card_ownership_history WHERE acquisition_type = 'PACK_OPENING'
              UNION ALL SELECT telegram_user_id, created_at::timestamptz FROM fantiki_transaction
                WHERE reason IN ('PACK_PURCHASE','CARD_RECYCLE','CARD_RENEWAL','LEGENDARY_UPGRADE','MARKETPLACE_PURCHASE','MARKETPLACE_SALE','MARKETPLACE_COMPLAINT_REWARD')
              UNION ALL SELECT seller_id, created_at::timestamptz FROM marketplace_listing
              UNION ALL SELECT buyer_id, sold_at::timestamptz FROM marketplace_listing WHERE buyer_id IS NOT NULL AND sold_at IS NOT NULL
              UNION ALL SELECT telegram_user_id, created_at::timestamptz FROM marketplace_complaint
              UNION ALL SELECT telegram_user_id, created_at::timestamptz FROM marketplace_watch_filter
            )
            SELECT COUNT(a.ts), MAX(a.ts), COUNT(DISTINCT ft.series_id)
            FROM telegram_user u
            LEFT JOIN action_events a ON a.user_id = u.id
            LEFT JOIN fantasy_team ft ON ft.telegram_user_id = u.id
            WHERE u.id = :userId
            GROUP BY u.id
            """.trimIndent(),
        ).setParameter("userId", userId)
            .singleResult as Array<*>

        val actionCount = (row[0] as Number).toLong()
        val lastActionAt = (row[1] as? Timestamp)?.toInstant()
        val teamSeries = (row[2] as Number).toLong()
        if (actionCount == 0L) return ProductAudience.NEVER_ACTIVATED
        if (teamSeries == 0L) return ProductAudience.ACTION_NO_TEAM
        if (lastActionAt != null) {
            val now = Instant.now()
            if (lastActionAt.isBefore(now.minusSeconds(14 * 24 * 60 * 60)) &&
                lastActionAt.isAfter(now.minusSeconds(31 * 24 * 60 * 60))
            ) {
                return ProductAudience.AT_RISK
            }
        }
        if (teamSeries >= 5L) return ProductAudience.ACTIVE_CORE
        return ProductAudience.ALL
    }

    @Transactional(readOnly = true)
    fun counts(audience: ProductAudience): AudienceCounts = AudienceCounts(
        rawCount = recipients(audience, eligibleOnly = false).size,
        eligibleCount = recipients(audience, eligibleOnly = true).size,
    )

    @Transactional(readOnly = true)
    fun eligibleTelegramIds(audience: ProductAudience): List<Long> = recipients(audience, eligibleOnly = true)

    private fun recipients(audience: ProductAudience, eligibleOnly: Boolean): List<Long> {
        val sql =
            """
            WITH action_events AS (
              SELECT telegram_user_id AS user_id, submitted_at::timestamptz AS ts FROM fantasy_team
              UNION ALL SELECT telegram_user_id, acquired_at::timestamptz FROM user_card_ownership_history WHERE acquisition_type = 'PACK_OPENING'
              UNION ALL SELECT telegram_user_id, created_at::timestamptz FROM fantiki_transaction
                WHERE reason IN ('PACK_PURCHASE','CARD_RECYCLE','CARD_RENEWAL','LEGENDARY_UPGRADE','MARKETPLACE_PURCHASE','MARKETPLACE_SALE','MARKETPLACE_COMPLAINT_REWARD')
              UNION ALL SELECT seller_id, created_at::timestamptz FROM marketplace_listing
              UNION ALL SELECT buyer_id, sold_at::timestamptz FROM marketplace_listing WHERE buyer_id IS NOT NULL AND sold_at IS NOT NULL
              UNION ALL SELECT telegram_user_id, created_at::timestamptz FROM marketplace_complaint
              UNION ALL SELECT telegram_user_id, created_at::timestamptz FROM marketplace_watch_filter
            ),
            action_stats AS (
              SELECT user_id, COUNT(*) AS action_count, MAX(ts) AS last_action_at
              FROM action_events
              GROUP BY user_id
            ),
            team_stats AS (
              SELECT telegram_user_id AS user_id, COUNT(DISTINCT series_id) AS team_series
              FROM fantasy_team
              GROUP BY telegram_user_id
            )
            SELECT u.telegram_id
            FROM telegram_user u
            LEFT JOIN action_stats a ON a.user_id = u.id
            LEFT JOIN team_stats t ON t.user_id = u.id
            WHERE ${audienceCondition(audience)}
              ${if (eligibleOnly) eligibleCondition() else ""}
            ORDER BY u.id
            """.trimIndent()
        return entityManager.createNativeQuery(sql)
            .resultList
            .map { (it as Number).toLong() }
    }

    private fun audienceCondition(audience: ProductAudience): String = when (audience) {
        ProductAudience.ALL -> "TRUE"
        ProductAudience.NEVER_ACTIVATED -> "COALESCE(a.action_count, 0) = 0"
        ProductAudience.ACTION_NO_TEAM -> "COALESCE(a.action_count, 0) > 0 AND COALESCE(t.team_series, 0) = 0"
        ProductAudience.AT_RISK ->
            "a.last_action_at < now() - interval '14 days' AND a.last_action_at >= now() - interval '31 days'"
        ProductAudience.ACTIVE_CORE -> "COALESCE(t.team_series, 0) >= 5 AND a.last_action_at >= now() - interval '30 days'"
    }

    private fun eligibleCondition(): String =
        """
        AND u.bot_blocked = FALSE
        AND NOT EXISTS (
          SELECT 1 FROM notification_preference np
          WHERE np.telegram_user_id = u.id
            AND np.category = '${NotificationCategory.ONBOARDING_TIPS.name}'
            AND np.enabled = FALSE
        )
        """.trimIndent()
}
