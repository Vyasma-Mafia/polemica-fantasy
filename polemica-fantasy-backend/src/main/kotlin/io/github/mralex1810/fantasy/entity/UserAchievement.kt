package io.github.mralex1810.fantasy.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.io.Serializable
import java.time.Instant

data class UserAchievementId(
    var telegramUser: Long? = null,
    var achievement: Long? = null,
) : Serializable

@Entity
@IdClass(UserAchievementId::class)
@Table(name = "user_achievement")
class UserAchievement(
    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "telegram_user_id", nullable = false)
    var telegramUser: TelegramUser? = null,

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "achievement_id", nullable = false)
    var achievement: AchievementDefinition? = null,

    @Column(name = "progress_value", nullable = false)
    var progressValue: Long = 0L,

    @Column(name = "completed_at")
    var completedAt: Instant? = null,

    @Column(name = "claimed_at")
    var claimedAt: Instant? = null,

    @Column(name = "reward_snapshot", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    var rewardSnapshot: String? = null,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)
