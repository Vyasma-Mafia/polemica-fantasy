package io.github.mralex1810.fantasy.repository

import io.github.mralex1810.fantasy.entity.UserProfileFeaturedAchievement
import io.github.mralex1810.fantasy.entity.UserProfileFeaturedAchievementId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface UserProfileFeaturedAchievementRepository :
    JpaRepository<UserProfileFeaturedAchievement, UserProfileFeaturedAchievementId> {

    @Query(
        """
        SELECT f FROM UserProfileFeaturedAchievement f
        JOIN FETCH f.achievement d
        WHERE f.telegramUser.id = :telegramUserId
        ORDER BY f.displayOrder ASC
        """,
    )
    fun findAllByTelegramUserIdOrdered(@Param("telegramUserId") telegramUserId: Long): List<UserProfileFeaturedAchievement>

    @Modifying
    @Query("DELETE FROM UserProfileFeaturedAchievement f WHERE f.telegramUser.id = :telegramUserId")
    fun deleteAllByTelegramUserId(@Param("telegramUserId") telegramUserId: Long): Int
}
