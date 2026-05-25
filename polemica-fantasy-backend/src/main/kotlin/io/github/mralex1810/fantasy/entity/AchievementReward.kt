package io.github.mralex1810.fantasy.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Entity
@Table(name = "achievement_reward")
class AchievementReward(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "achievement_id", nullable = false)
    var achievement: AchievementDefinition? = null,

    @Column(name = "reward_type", nullable = false, length = 48)
    var rewardType: String = "",

    @Column
    var amount: Long? = null,

    @Column(name = "reward_code", length = 96)
    var rewardCode: String? = null,

    @Column(name = "metadata", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    var metadata: String? = null,

    @Column(name = "display_order", nullable = false)
    var displayOrder: Int = 0,
)
