package io.github.mralex1810.fantasy.repository

import io.github.mralex1810.fantasy.entity.AchievementDefinition
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface AchievementAdminStatsProjection {
    fun getAchievementId(): Long
    fun getCompletedUsers(): Long
    fun getClaimedUsers(): Long
    fun getUnclaimedUsers(): Long
    fun getTotalProgress(): Long
    fun getAverageProgress(): Double
    fun getNearCompletionUsers(): Long
    fun getLastCompletedAt(): Instant?
}

interface AchievementDefinitionRepository : JpaRepository<AchievementDefinition, Long> {
    fun findByCode(code: String): AchievementDefinition?

    @Query(
        """
        SELECT DISTINCT d FROM AchievementDefinition d
        LEFT JOIN FETCH d.rewards r
        WHERE d.enabled = TRUE
        ORDER BY d.displayOrder ASC, d.id ASC
        """,
    )
    fun findAllEnabledWithRewards(): List<AchievementDefinition>

    @Query(
        """
        SELECT DISTINCT d FROM AchievementDefinition d
        LEFT JOIN FETCH d.rewards r
        WHERE d.enabled = TRUE
          AND d.visibility = 'PUBLIC'
        ORDER BY d.displayOrder ASC, d.id ASC
        """,
    )
    fun findAllEnabledPublicWithRewards(): List<AchievementDefinition>

    @Query(
        """
        SELECT DISTINCT d FROM AchievementDefinition d
        LEFT JOIN FETCH d.rewards r
        WHERE d.code = :code
        """,
    )
    fun findByCodeWithRewards(@Param("code") code: String): AchievementDefinition?

    @Query(
        """
        SELECT DISTINCT d FROM AchievementDefinition d
        LEFT JOIN FETCH d.rewards r
        ORDER BY d.displayOrder ASC, d.id ASC
        """,
    )
    fun findAllWithRewards(): List<AchievementDefinition>

    @Query(
        value = """
        SELECT
            d.id::bigint AS "achievementId",
            COUNT(*) FILTER (WHERE ua.completed_at IS NOT NULL)::bigint AS "completedUsers",
            COUNT(*) FILTER (WHERE ua.claimed_at IS NOT NULL)::bigint AS "claimedUsers",
            COUNT(*) FILTER (WHERE ua.completed_at IS NOT NULL AND ua.claimed_at IS NULL)::bigint AS "unclaimedUsers",
            COALESCE(SUM(ua.progress_value), 0)::bigint AS "totalProgress",
            COALESCE(AVG(ua.progress_value)::double precision, 0.0)::double precision AS "averageProgress",
            COUNT(*) FILTER (
                WHERE ua.completed_at IS NULL
                  AND ua.progress_value > 0
                  AND ua.progress_value >= CEIL(d.target_value * 0.8)
                  AND ua.progress_value < d.target_value
            )::bigint AS "nearCompletionUsers",
            MAX(ua.completed_at) AS "lastCompletedAt"
        FROM achievement_definition d
        LEFT JOIN user_achievement ua ON ua.achievement_id = d.id
        GROUP BY d.id
        """,
        nativeQuery = true,
    )
    fun aggregateAdminStats(): List<AchievementAdminStatsProjection>
}
