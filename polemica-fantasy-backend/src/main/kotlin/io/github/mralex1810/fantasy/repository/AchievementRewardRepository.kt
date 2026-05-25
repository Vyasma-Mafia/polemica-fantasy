package io.github.mralex1810.fantasy.repository

import io.github.mralex1810.fantasy.entity.AchievementReward
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface AchievementRewardRepository : JpaRepository<AchievementReward, Long> {
    @Modifying
    @Query("DELETE FROM AchievementReward r WHERE r.achievement.id = :achievementId")
    fun deleteByAchievementId(@Param("achievementId") achievementId: Long): Int
}
