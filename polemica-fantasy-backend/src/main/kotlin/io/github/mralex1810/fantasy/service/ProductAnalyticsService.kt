package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.dto.admin.response.ProductAnalyticsSummaryDto
import io.github.mralex1810.fantasy.dto.admin.response.ProductCampaignAnalyticsDto
import io.github.mralex1810.fantasy.dto.admin.response.ReleaseNoteAnalyticsDto
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ProductAnalyticsService(
    private val entityManager: EntityManager,
) {
    @Transactional(readOnly = true)
    fun summary(): ProductAnalyticsSummaryDto {
        val totalUsers = longScalar("SELECT COUNT(*) FROM telegram_user")
        val botBlockedUsers = longScalar("SELECT COUNT(*) FROM telegram_user WHERE bot_blocked = TRUE")
        val startToFirstAction24h = longScalar(
            """
            WITH action_events AS ($ACTION_EVENTS_SQL),
            first_action AS (
              SELECT user_id, MIN(ts) AS first_action_at
              FROM action_events
              GROUP BY user_id
            )
            SELECT COUNT(*)
            FROM telegram_user u
            JOIN first_action a ON a.user_id = u.id
            WHERE a.first_action_at <= u.created_at + interval '24 hours'
            """.trimIndent(),
        )
        val startToFirstTeam7d = longScalar(
            """
            WITH first_team AS (
              SELECT telegram_user_id AS user_id, MIN(submitted_at) AS first_team_at
              FROM fantasy_team
              GROUP BY telegram_user_id
            )
            SELECT COUNT(*)
            FROM telegram_user u
            JOIN first_team ft ON ft.user_id = u.id
            WHERE ft.first_team_at <= u.created_at + interval '7 days'
            """.trimIndent(),
        )
        val actionNoTeamUsers = longScalar(
            """
            WITH action_events AS ($ACTION_EVENTS_SQL),
            action_stats AS (
              SELECT user_id, COUNT(*) AS action_count
              FROM action_events
              GROUP BY user_id
            ),
            team_stats AS (
              SELECT telegram_user_id AS user_id, COUNT(*) AS team_count
              FROM fantasy_team
              GROUP BY telegram_user_id
            )
            SELECT COUNT(*)
            FROM telegram_user u
            LEFT JOIN action_stats a ON a.user_id = u.id
            LEFT JOIN team_stats t ON t.user_id = u.id
            WHERE COALESCE(a.action_count, 0) > 0
              AND COALESCE(t.team_count, 0) = 0
            """.trimIndent(),
        )
        val actionNoTeamTeamSubmit7d = longScalar(
            """
            WITH action_events AS ($ACTION_EVENTS_SQL),
            first_action AS (
              SELECT user_id, MIN(ts) AS first_action_at
              FROM action_events
              GROUP BY user_id
            ),
            first_team AS (
              SELECT telegram_user_id AS user_id, MIN(submitted_at) AS first_team_at
              FROM fantasy_team
              GROUP BY telegram_user_id
            )
            SELECT COUNT(*)
            FROM first_action a
            JOIN first_team ft ON ft.user_id = a.user_id
            WHERE ft.first_team_at > a.first_action_at
              AND ft.first_team_at <= a.first_action_at + interval '7 days'
            """.trimIndent(),
        )
        val checklistCompletedUsers = longScalar(
            """
            SELECT COUNT(*)
            FROM telegram_user u
            LEFT JOIN onboarding_progress op ON op.telegram_user_id = u.id
            WHERE (op.first_pack_opened_at IS NOT NULL OR u.pack_opens_count > 0)
              AND op.collection_viewed_at IS NOT NULL
              AND (op.first_team_submitted_at IS NOT NULL OR EXISTS (
                SELECT 1 FROM fantasy_team ft WHERE ft.telegram_user_id = u.id
              ))
              AND op.notifications_viewed_at IS NOT NULL
              AND op.results_viewed_at IS NOT NULL
            """.trimIndent(),
        )
        return ProductAnalyticsSummaryDto(
            totalUsers = totalUsers,
            botBlockedUsers = botBlockedUsers,
            botBlockedPercent = percent(botBlockedUsers, totalUsers),
            startToFirstAction24h = startToFirstAction24h,
            startToFirstTeam7d = startToFirstTeam7d,
            actionNoTeamUsers = actionNoTeamUsers,
            actionNoTeamTeamSubmit7d = actionNoTeamTeamSubmit7d,
            checklistCompletedUsers = checklistCompletedUsers,
            checklistCompletedPercent = percent(checklistCompletedUsers, totalUsers),
        )
    }

    @Transactional(readOnly = true)
    fun campaigns(): List<ProductCampaignAnalyticsDto> =
        entityManager.createNativeQuery(
            """
            SELECT pc.id, pc.title, pc.audience, pc.sent_count,
              COUNT(DISTINCT CASE WHEN pe.event_type = 'CAMPAIGN_OPEN' THEN pe.telegram_user_id END) AS opened_count,
              COUNT(DISTINCT CASE WHEN pe.event_type = 'CAMPAIGN_CLICK' THEN pe.telegram_user_id END) AS clicked_count,
              COUNT(DISTINCT CASE WHEN pe.event_type IN ('CAMPAIGN_ACTION','FEATURE_USED','ONBOARDING_STEP_COMPLETED') THEN pe.telegram_user_id END) AS acted_count
            FROM product_campaign pc
            LEFT JOIN product_event pe ON pe.campaign_id = pc.id
            GROUP BY pc.id, pc.title, pc.audience, pc.sent_count
            ORDER BY pc.created_at DESC
            LIMIT 50
            """.trimIndent(),
        ).resultList.map {
            val row = it as Array<*>
            ProductCampaignAnalyticsDto(
                campaignId = (row[0] as Number).toLong(),
                title = row[1] as String,
                audience = row[2] as String,
                sentCount = (row[3] as Number).toInt(),
                openedCount = (row[4] as Number).toLong(),
                clickedCount = (row[5] as Number).toLong(),
                actedCount = (row[6] as Number).toLong(),
            )
        }

    @Transactional(readOnly = true)
    fun releaseNotes(): List<ReleaseNoteAnalyticsDto> =
        entityManager.createNativeQuery(
            """
            SELECT rn.id, rn.title, rn.audience,
              COUNT(DISTINCT rnv.telegram_user_id) AS seen_count,
              COUNT(DISTINCT CASE WHEN pe.event_type = 'FEATURE_USED' THEN pe.telegram_user_id END) AS feature_used_count
            FROM release_note rn
            LEFT JOIN release_note_view rnv ON rnv.release_note_id = rn.id
            LEFT JOIN product_event pe ON pe.release_note_id = rn.id
            GROUP BY rn.id, rn.title, rn.audience
            ORDER BY rn.published_at DESC
            LIMIT 50
            """.trimIndent(),
        ).resultList.map {
            val row = it as Array<*>
            ReleaseNoteAnalyticsDto(
                releaseNoteId = (row[0] as Number).toLong(),
                title = row[1] as String,
                audience = row[2] as String,
                seenCount = (row[3] as Number).toLong(),
                featureUsedCount = (row[4] as Number).toLong(),
            )
        }

    private fun longScalar(sql: String): Long =
        (entityManager.createNativeQuery(sql).singleResult as Number).toLong()

    private fun percent(part: Long, total: Long): Double =
        if (total <= 0) 0.0 else (part.toDouble() * 100.0) / total.toDouble()

    companion object {
        private const val ACTION_EVENTS_SQL = """
          SELECT telegram_user_id AS user_id, submitted_at::timestamptz AS ts FROM fantasy_team
          UNION ALL SELECT telegram_user_id, acquired_at::timestamptz FROM user_card_ownership_history WHERE acquisition_type = 'PACK_OPENING'
          UNION ALL SELECT telegram_user_id, created_at::timestamptz FROM fantiki_transaction
            WHERE reason IN ('PACK_PURCHASE','CARD_RECYCLE','CARD_RENEWAL','LEGENDARY_UPGRADE','MARKETPLACE_PURCHASE','MARKETPLACE_SALE','MARKETPLACE_COMPLAINT_REWARD')
          UNION ALL SELECT seller_id, created_at::timestamptz FROM marketplace_listing
          UNION ALL SELECT buyer_id, sold_at::timestamptz FROM marketplace_listing WHERE buyer_id IS NOT NULL AND sold_at IS NOT NULL
          UNION ALL SELECT telegram_user_id, created_at::timestamptz FROM marketplace_complaint
          UNION ALL SELECT telegram_user_id, created_at::timestamptz FROM marketplace_watch_filter
        """
    }
}
