package io.github.mralex1810.fantasy.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.MapsId
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "user_profile_customization")
class UserProfileCustomization(
    @Id
    @Column(name = "telegram_user_id")
    var telegramUserId: Long? = null,

    @MapsId
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "telegram_user_id", nullable = false)
    var telegramUser: TelegramUser? = null,

    @Column(name = "profile_frame_code", length = 96)
    var profileFrameCode: String? = null,

    @Column(name = "favorite_badge_fantasy_player_id")
    var favoriteBadgeFantasyPlayerId: Long? = null,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)
