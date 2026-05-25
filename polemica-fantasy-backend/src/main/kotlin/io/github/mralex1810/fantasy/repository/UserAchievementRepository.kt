package io.github.mralex1810.fantasy.repository

import io.github.mralex1810.fantasy.entity.UserAchievement
import io.github.mralex1810.fantasy.entity.UserAchievementId
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface UserAchievementRepository : JpaRepository<UserAchievement, UserAchievementId> {
    fun findAllByTelegramUser_Id(telegramUserId: Long): List<UserAchievement>

    fun findByTelegramUser_IdAndAchievement_Id(telegramUserId: Long, achievementId: Long): UserAchievement?

    @Query(
        """
        SELECT ua FROM UserAchievement ua
        JOIN FETCH ua.achievement d
        WHERE ua.telegramUser.id = :telegramUserId
          AND ua.claimedAt IS NOT NULL
          AND d.visibility = 'PUBLIC'
        ORDER BY d.displayOrder ASC, d.id ASC
        """,
    )
    fun findClaimedWithDefinitions(@Param("telegramUserId") telegramUserId: Long): List<UserAchievement>

    @Query(
        """
        SELECT ua FROM UserAchievement ua
        JOIN FETCH ua.achievement d
        WHERE ua.telegramUser.id = :telegramUserId
        """,
    )
    fun findAllWithDefinitionsByTelegramUserId(@Param("telegramUserId") telegramUserId: Long): List<UserAchievement>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
        SELECT ua FROM UserAchievement ua
        JOIN FETCH ua.achievement d
        LEFT JOIN FETCH d.rewards
        WHERE ua.telegramUser.id = :telegramUserId
          AND ua.achievement.id = :achievementId
        """,
    )
    fun findForUpdate(
        @Param("telegramUserId") telegramUserId: Long,
        @Param("achievementId") achievementId: Long,
    ): UserAchievement?

    @Modifying
    @Query(
        value = """
        INSERT INTO user_achievement (telegram_user_id, achievement_id, progress_value, updated_at)
        VALUES (:telegramUserId, :achievementId, 0, now())
        ON CONFLICT (telegram_user_id, achievement_id) DO NOTHING
        """,
        nativeQuery = true,
    )
    fun ensureRow(
        @Param("telegramUserId") telegramUserId: Long,
        @Param("achievementId") achievementId: Long,
    ): Int
}
