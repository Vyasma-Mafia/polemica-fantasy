package io.github.mralex1810.fantasy.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "card_template_achievement")
class CardTemplateAchievement(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "card_template_id", nullable = false)
    var cardTemplate: CardTemplate? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "achievement_type", nullable = false, length = 64)
    var achievementType: AchievementType = AchievementType.WON_GAME,

    @Column(name = "bonus_points", nullable = false)
    var bonusPoints: Double = 0.0,
)
