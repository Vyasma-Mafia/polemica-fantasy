package io.github.mralex1810.fantasy.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.io.Serializable

data class UserProfileFeaturedAchievementId(
    var telegramUser: Long? = null,
    var achievement: Long? = null,
) : Serializable

@Entity
@IdClass(UserProfileFeaturedAchievementId::class)
@Table(name = "user_profile_featured_achievement")
class UserProfileFeaturedAchievement(
    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "telegram_user_id", nullable = false)
    var telegramUser: TelegramUser? = null,

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "achievement_id", nullable = false)
    var achievement: AchievementDefinition? = null,

    @Column(name = "display_order", nullable = false)
    var displayOrder: Int = 0,
)
