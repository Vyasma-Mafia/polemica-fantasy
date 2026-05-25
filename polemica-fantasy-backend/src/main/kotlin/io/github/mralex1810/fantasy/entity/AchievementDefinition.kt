package io.github.mralex1810.fantasy.entity

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "achievement_definition")
class AchievementDefinition(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false, unique = true, length = 96)
    var code: String = "",

    @Column(nullable = false, length = 48)
    var category: String = "",

    @Column(name = "condition_type", nullable = false, length = 96)
    var conditionType: String = "",

    @Column(name = "history_policy", nullable = false, length = 48)
    var historyPolicy: String = "FROM_ACHIEVEMENTS_LAUNCH",

    @Column(name = "target_value", nullable = false)
    var targetValue: Long = 1L,

    @Column(name = "chain_group", length = 96)
    var chainGroup: String? = null,

    @Column(name = "chain_level")
    var chainLevel: Int? = null,

    @Column(nullable = false)
    var title: String = "",

    @Column
    var description: String? = null,

    @Column(name = "icon_url")
    var iconUrl: String? = null,

    @Column(name = "accent_color", length = 32)
    var accentColor: String? = null,

    @Column(nullable = false, length = 32)
    var rarity: String = "COMMON",

    @Column(nullable = false, length = 32)
    var visibility: String = "PUBLIC",

    @Column(nullable = false)
    var enabled: Boolean = true,

    @Column(name = "tracking_started_at")
    var trackingStartedAt: Instant? = null,

    @Column(name = "display_order", nullable = false)
    var displayOrder: Int = 0,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),

    @OneToMany(mappedBy = "achievement", fetch = FetchType.LAZY, cascade = [CascadeType.ALL])
    var rewards: MutableList<AchievementReward> = mutableListOf(),
)
