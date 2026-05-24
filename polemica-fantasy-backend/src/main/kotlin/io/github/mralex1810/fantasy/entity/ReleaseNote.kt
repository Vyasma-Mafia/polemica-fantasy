package io.github.mralex1810.fantasy.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant

enum class ProductAudience {
    ALL,
    NEVER_ACTIVATED,
    ACTION_NO_TEAM,
    AT_RISK,
    ACTIVE_CORE,
}

@Entity
@Table(name = "release_note")
class ReleaseNote(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false, length = 255)
    var title: String = "",

    @Column(nullable = false, columnDefinition = "TEXT")
    var body: String = "",

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    var audience: ProductAudience = ProductAudience.ALL,

    @Column(name = "min_app_version", length = 64)
    var minAppVersion: String? = null,

    @Column(nullable = false)
    var active: Boolean = true,

    @Column(name = "published_at", nullable = false)
    var publishedAt: Instant = Instant.now(),

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
)

@Entity
@Table(name = "release_note_view")
@IdClass(ReleaseNoteViewId::class)
class ReleaseNoteView(
    @Id
    @Column(name = "telegram_user_id")
    var telegramUserId: Long? = null,

    @Id
    @Column(name = "release_note_id")
    var releaseNoteId: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "telegram_user_id", nullable = false, insertable = false, updatable = false)
    var telegramUser: TelegramUser? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "release_note_id", nullable = false, insertable = false, updatable = false)
    var releaseNote: ReleaseNote? = null,

    @Column(name = "seen_at", nullable = false)
    var seenAt: Instant = Instant.now(),
)

data class ReleaseNoteViewId(
    var telegramUserId: Long? = null,
    var releaseNoteId: Long? = null,
) : Serializable
