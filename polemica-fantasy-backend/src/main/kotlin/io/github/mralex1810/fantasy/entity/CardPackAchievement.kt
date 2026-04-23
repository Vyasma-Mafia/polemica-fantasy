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

data class CardPackAchievementId(
    var cardPackId: Long? = null,
    var achievementId: String? = null,
) : Serializable

/** Optional per-pack pool for pack RNG: which achievements may appear on generated cards. */
@Entity
@IdClass(CardPackAchievementId::class)
@Table(name = "card_pack_achievement")
class CardPackAchievement(
    @Id
    @Column(name = "card_pack_id", nullable = false)
    var cardPackId: Long? = null,

    @Id
    @Column(name = "achievement_id", nullable = false, length = 64)
    var achievementId: String? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "card_pack_id", insertable = false, updatable = false)
    var cardPack: CardPack? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "achievement_id", insertable = false, updatable = false)
    var achievement: Achievement? = null,
)
