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
import java.time.Instant

data class UserCosmeticUnlockId(
    var telegramUser: Long? = null,
    var cosmeticType: String? = null,
    var cosmeticCode: String? = null,
) : Serializable

@Entity
@IdClass(UserCosmeticUnlockId::class)
@Table(name = "user_cosmetic_unlock")
class UserCosmeticUnlock(
    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "telegram_user_id", nullable = false)
    var telegramUser: TelegramUser? = null,

    @Id
    @Column(name = "cosmetic_type", nullable = false, length = 48)
    var cosmeticType: String = "",

    @Id
    @Column(name = "cosmetic_code", nullable = false, length = 96)
    var cosmeticCode: String = "",

    @Column(name = "source_type", nullable = false, length = 48)
    var sourceType: String = "ACHIEVEMENT",

    @Column(name = "source_code", length = 96)
    var sourceCode: String? = null,

    @Column(name = "unlocked_at", nullable = false)
    var unlockedAt: Instant = Instant.now(),
)
