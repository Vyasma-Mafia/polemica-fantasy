package io.github.mralex1810.fantasy.service.achievement

import io.github.mralex1810.fantasy.entity.AchievementDefinition
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.sql.Timestamp

@Component
class AchievementProgressCalculator(
    private val jdbcTemplate: JdbcTemplate,
) {
    fun currentProgress(internalTelegramUserId: Long, definition: AchievementDefinition): Long {
        val startedAt = definition.trackingStartedAt ?: return 0L
        if (!definition.enabled) return 0L
        val ts = Timestamp.from(startedAt)
        return when (definition.conditionType) {
            "TEAMS_SUBMITTED" -> queryLong(
                "SELECT COUNT(*) FROM fantasy_team WHERE telegram_user_id = ? AND created_at >= ?",
                internalTelegramUserId,
                ts,
            )
            "BUDGET_TEAMS_SUBMITTED" -> queryLong(
                """
                SELECT COUNT(*)
                FROM fantasy_team ft
                JOIN series_league sl ON sl.id = ft.series_league_id
                JOIN league l ON l.id = sl.league_id
                WHERE ft.telegram_user_id = ?
                  AND ft.created_at >= ?
                  AND l.code = 'BUDGET'
                """,
                internalTelegramUserId,
                ts,
            )
            "DUAL_LEAGUE_SERIES" -> queryLong(
                """
                SELECT COUNT(*)
                FROM (
                    SELECT ft.series_id
                    FROM fantasy_team ft
                    JOIN series_league sl ON sl.id = ft.series_league_id
                    JOIN league l ON l.id = sl.league_id
                    WHERE ft.telegram_user_id = ?
                      AND ft.created_at >= ?
                      AND l.code IN ('MAIN', 'BUDGET')
                    GROUP BY ft.series_id
                    HAVING COUNT(DISTINCT l.code) = 2
                ) x
                """,
                internalTelegramUserId,
                ts,
            )
            "SERIES_WINS" -> rankedCount(internalTelegramUserId, ts, "winner_rank = 1")
            "BUDGET_WINS" -> rankedCount(internalTelegramUserId, ts, "winner_rank = 1 AND league_code = 'BUDGET'")
            "TOP3_FINISHES" -> rankedCount(internalTelegramUserId, ts, "winner_rank <= 3")
            "BUDGET_TOP10" -> rankedCount(internalTelegramUserId, ts, "league_code = 'BUDGET' AND winner_rank <= 10")
            "TOP10_OR_HALF_FINISHES" -> rankedCount(
                internalTelegramUserId,
                ts,
                "winner_rank <= CASE WHEN participants < 10 THEN CEIL(participants / 2.0) ELSE 10 END",
            )
            "TOP_QUARTER_FINISHES" -> rankedCount(
                internalTelegramUserId,
                ts,
                "winner_rank <= CEIL(participants * 0.25)",
            )
            "CARDS_OWNED_TOTAL" -> activeCardsCount(internalTelegramUserId, ts, null)
            "FIRST_EPIC_CARD" -> (
                activeCardsCount(internalTelegramUserId, ts, "EPIC") +
                    activePackRarityCount(internalTelegramUserId, ts, "EPIC")
                ).coerceAtMost(1L)
            "FIRST_LEGENDARY_CARD" -> activeCardsCount(internalTelegramUserId, ts, "LEGENDARY").coerceAtMost(1L)
            "FIRST_SKIN_CARD" -> queryLong(
                """
                SELECT COUNT(DISTINCT uc.id)
                FROM user_card uc
                LEFT JOIN user_card_ownership_history h ON h.user_card_id = uc.id AND h.telegram_user_id = uc.telegram_user_id
                WHERE uc.telegram_user_id = ?
                  AND uc.deleted_at IS NULL
                  AND uc.card_skin_id IS NOT NULL
                  AND (
                    h.acquired_at >= (?::timestamptz AT TIME ZONE 'UTC')
                    OR uc.acquired_at >= (?::timestamptz AT TIME ZONE 'UTC')
                  )
                """,
                internalTelegramUserId,
                ts,
                ts,
            ).coerceAtMost(1L)
            "SAME_PLAYER_3_RARITIES" -> samePlayerRarityCollectorCandidates(internalTelegramUserId, ts, 3)
                .firstOrNull()
                .completedFlag()
            "SAME_PLAYER_4_RARITIES" -> samePlayerRarityCollectorCandidates(internalTelegramUserId, ts, 4)
                .firstOrNull()
                .completedFlag()
            "PACKS_OPENED" -> queryLong(
                "SELECT COUNT(*) FROM user_card_pack_open_event WHERE telegram_user_id = ? AND opened_at >= ?",
                internalTelegramUserId,
                ts,
            )
            "PACK_EPIC_DROP" -> activePackRarityCount(internalTelegramUserId, ts, "EPIC").coerceAtMost(1L)
            "MARKETPLACE_PURCHASES" -> marketplaceTradesCount(internalTelegramUserId, ts, "buyer_id")
            "MARKETPLACE_SALES" -> marketplaceTradesCount(internalTelegramUserId, ts, "seller_id")
            "MARKETPLACE_WATCHES" -> queryLong(
                "SELECT COUNT(*) FROM marketplace_watch_filter WHERE telegram_user_id = ? AND created_at >= ?",
                internalTelegramUserId,
                ts,
            )
            "MARKETPLACE_UNIQUE_COUNTERPARTIES" -> queryLong(
                """
                SELECT COUNT(DISTINCT CASE WHEN seller_id = ? THEN buyer_id ELSE seller_id END)
                FROM marketplace_listing ml
                WHERE ml.status = 'SOLD'
                  AND ml.sold_at >= (?::timestamptz AT TIME ZONE 'UTC')
                  AND (ml.seller_id = ? OR ml.buyer_id = ?)
                  AND ml.buyer_id IS NOT NULL
                  AND NOT EXISTS (
                      SELECT 1
                      FROM marketplace_listing_sanction s
                      WHERE s.listing_id = ml.id
                  )
                """,
                internalTelegramUserId,
                ts,
                internalTelegramUserId,
                internalTelegramUserId,
            )
            "SHARE_PROFILE" -> productEventCount(internalTelegramUserId, ts, "SHARE_PROFILE").coerceAtMost(1L)
            "SHARE_TEAM" -> productEventCount(internalTelegramUserId, ts, "SHARE_TEAM").coerceAtMost(1L)
            "COMPARE_OPEN" -> productEventCount(internalTelegramUserId, ts, "COMPARE_OPEN").coerceAtMost(1L)
            "PUBLIC_PROFILE_VIEWS" -> queryLong(
                """
                SELECT COUNT(DISTINCT pe.subject_id)
                FROM product_event pe
                JOIN telegram_user u ON u.id = pe.telegram_user_id
                WHERE pe.telegram_user_id = ?
                  AND pe.event_type = 'PUBLIC_PROFILE_VIEW'
                  AND pe.subject_type = 'PROFILE'
                  AND pe.subject_id IS NOT NULL
                  AND pe.subject_id <> u.telegram_id
                  AND pe.created_at >= ?
                """,
                internalTelegramUserId,
                ts,
            )
            "LEGENDARY_UPGRADES" -> queryLong(
                "SELECT COUNT(*) FROM user_legendary_upgrade_event WHERE telegram_user_id = ? AND upgraded_at >= ?",
                internalTelegramUserId,
                ts,
            )
            "CARD_MERGES" -> queryLong(
                "SELECT COUNT(*) FROM user_card_merge WHERE telegram_user_id = ? AND created_at >= ?",
                internalTelegramUserId,
                ts,
            )
            "CARD_MERGE_EPIC_RESULTS" -> queryLong(
                """
                SELECT COUNT(*)
                FROM user_card_merge
                WHERE telegram_user_id = ?
                  AND result_rarity = 'EPIC'
                  AND created_at >= ?
                """,
                internalTelegramUserId,
                ts,
            )
            "CARD_MERGE_UNIQUE_PLAYERS" -> queryLong(
                """
                SELECT COUNT(DISTINCT fantasy_player_id)
                FROM user_card_merge
                WHERE telegram_user_id = ?
                  AND created_at >= ?
                """,
                internalTelegramUserId,
                ts,
            )
            else -> 0L
        }
    }

    private fun marketplaceTradesCount(internalTelegramUserId: Long, trackingStartedAt: Timestamp, userColumn: String): Long =
        queryLong(
            """
            SELECT COUNT(*)
            FROM marketplace_listing ml
            WHERE ml.status = 'SOLD'
              AND ml.$userColumn = ?
              AND ml.sold_at >= (?::timestamptz AT TIME ZONE 'UTC')
              AND ml.buyer_id IS NOT NULL
              AND NOT EXISTS (
                  SELECT 1
                  FROM marketplace_listing_sanction s
                  WHERE s.listing_id = ml.id
              )
            """,
            internalTelegramUserId,
            trackingStartedAt,
        )

    private fun productEventCount(internalTelegramUserId: Long, trackingStartedAt: Timestamp, eventType: String): Long =
        queryLong(
            """
            SELECT COUNT(*)
            FROM product_event
            WHERE telegram_user_id = ?
              AND event_type = ?
              AND created_at >= ?
            """,
            internalTelegramUserId,
            eventType,
            trackingStartedAt,
        )

    private fun activeCardsCount(internalTelegramUserId: Long, trackingStartedAt: Timestamp, rarity: String?): Long {
        val rarityClause = if (rarity == null) "" else "AND ct.rarity = ?"
        val args =
            if (rarity == null) {
                arrayOf<Any>(internalTelegramUserId, trackingStartedAt, trackingStartedAt)
            } else {
                arrayOf<Any>(internalTelegramUserId, trackingStartedAt, trackingStartedAt, rarity)
            }
        return queryLong(
            """
            SELECT COUNT(DISTINCT uc.id)
            FROM user_card uc
            JOIN card_template ct ON ct.id = uc.card_template_id
            LEFT JOIN user_card_ownership_history h ON h.user_card_id = uc.id AND h.telegram_user_id = uc.telegram_user_id
            WHERE uc.telegram_user_id = ?
              AND uc.deleted_at IS NULL
              AND (
                h.acquired_at >= (?::timestamptz AT TIME ZONE 'UTC')
                OR uc.acquired_at >= (?::timestamptz AT TIME ZONE 'UTC')
              )
              $rarityClause
            """,
            *args,
        )
    }

    private fun activePackRarityCount(internalTelegramUserId: Long, trackingStartedAt: Timestamp, rarity: String): Long =
        queryLong(
            """
            SELECT COUNT(DISTINCT uc.id)
            FROM user_card uc
            JOIN card_template ct ON ct.id = uc.card_template_id
            LEFT JOIN user_card_ownership_history h ON h.user_card_id = uc.id AND h.telegram_user_id = uc.telegram_user_id
            WHERE uc.telegram_user_id = ?
              AND uc.deleted_at IS NULL
              AND uc.source_card_pack_id IS NOT NULL
              AND ct.rarity = ?
              AND (
                h.acquired_at >= (?::timestamptz AT TIME ZONE 'UTC')
                OR uc.acquired_at >= (?::timestamptz AT TIME ZONE 'UTC')
              )
              AND EXISTS (
                  SELECT 1
                  FROM user_card_pack_open_event e
                  WHERE e.telegram_user_id = uc.telegram_user_id
                    AND e.card_pack_id = uc.source_card_pack_id
                    AND e.opened_at >= ?
              )
            """,
            internalTelegramUserId,
            rarity,
            trackingStartedAt,
            trackingStartedAt,
            trackingStartedAt,
        )

    fun samePlayerRarityCollectorNickname(
        internalTelegramUserId: Long,
        definition: AchievementDefinition,
        preferredFantasyPlayerId: Long? = null,
    ): String? {
        val candidates = samePlayerRarityCollectorCandidates(internalTelegramUserId, definition)
        return (
            preferredFantasyPlayerId
                ?.let { preferredId -> candidates.firstOrNull { it.fantasyPlayerId == preferredId } }
                ?: candidates.firstOrNull()
            )?.nickname
    }

    fun samePlayerRarityCollectorCandidates(
        internalTelegramUserId: Long,
        definition: AchievementDefinition,
    ): List<SamePlayerRarityCandidate> {
        val startedAt = definition.trackingStartedAt ?: return emptyList()
        if (!definition.enabled) return emptyList()
        val requiredRarities = when (definition.conditionType) {
            "SAME_PLAYER_3_RARITIES" -> 3
            "SAME_PLAYER_4_RARITIES" -> 4
            else -> return emptyList()
        }
        return samePlayerRarityCollectorCandidates(internalTelegramUserId, Timestamp.from(startedAt), requiredRarities)
    }

    private fun samePlayerRarityCollectorCandidates(
        internalTelegramUserId: Long,
        trackingStartedAt: Timestamp,
        requiredRarities: Int,
    ): List<SamePlayerRarityCandidate> =
        jdbcTemplate.query(
            """
            SELECT fp.id, fp.nickname, COUNT(DISTINCT ct.rarity) AS rarity_count
            FROM user_card uc
            JOIN card_template ct ON ct.id = uc.card_template_id
            JOIN fantasy_player fp ON fp.id = ct.fantasy_player_id
            LEFT JOIN user_card_ownership_history h ON h.user_card_id = uc.id AND h.telegram_user_id = uc.telegram_user_id
            WHERE uc.telegram_user_id = ?
              AND uc.deleted_at IS NULL
              AND (
                h.acquired_at >= (?::timestamptz AT TIME ZONE 'UTC')
                OR uc.acquired_at >= (?::timestamptz AT TIME ZONE 'UTC')
              )
            GROUP BY fp.id, fp.nickname
            HAVING COUNT(DISTINCT ct.rarity) >= ?
            ORDER BY COUNT(DISTINCT ct.rarity) DESC, fp.nickname ASC, fp.id ASC
            """,
            { rs, _ ->
                SamePlayerRarityCandidate(
                    fantasyPlayerId = rs.getLong("id"),
                    nickname = rs.getString("nickname"),
                    rarityCount = rs.getInt("rarity_count"),
                )
            },
            internalTelegramUserId,
            trackingStartedAt,
            trackingStartedAt,
            requiredRarities,
        )

    private fun rankedCount(internalTelegramUserId: Long, trackingStartedAt: Timestamp, predicate: String): Long =
        queryLong(
            """
            SELECT COUNT(*)
            FROM (
                SELECT
                    ft.telegram_user_id,
                    l.code AS league_code,
                    ROW_NUMBER() OVER (
                        PARTITION BY ft.series_league_id
                        ORDER BY ft.total_score DESC NULLS LAST, ft.id ASC
                    ) AS winner_rank,
                    COUNT(*) OVER (PARTITION BY ft.series_league_id) AS participants
                FROM fantasy_team ft
                JOIN series s ON s.id = ft.series_id
                JOIN series_league sl ON sl.id = ft.series_league_id
                JOIN league l ON l.id = sl.league_id
                WHERE s.status = 'FINISHED'
                  AND s.finalized_at >= ?
                  AND ft.created_at >= ?
                  AND ft.total_score IS NOT NULL
            ) ranked
            WHERE ranked.telegram_user_id = ?
              AND $predicate
            """,
            trackingStartedAt,
            trackingStartedAt,
            internalTelegramUserId,
        )

    private fun queryLong(sql: String, vararg args: Any): Long =
        jdbcTemplate.queryForObject(sql, Long::class.java, *args) ?: 0L

    private fun SamePlayerRarityCandidate?.completedFlag(): Long = if (this == null) 0L else 1L
}

data class SamePlayerRarityCandidate(
    val fantasyPlayerId: Long,
    val nickname: String,
    val rarityCount: Int,
)
