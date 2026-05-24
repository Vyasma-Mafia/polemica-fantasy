package io.github.mralex1810.fantasy.entity

import com.fasterxml.jackson.databind.JsonNode
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
import java.time.Instant

@Entity
@Table(name = "product_event")
class ProductEvent(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "telegram_user_id")
    var telegramUser: TelegramUser? = null,

    @Column(name = "event_type", nullable = false, length = 64)
    var eventType: String = "",

    @Column(name = "campaign_id")
    var campaignId: Long? = null,

    @Column(name = "release_note_id")
    var releaseNoteId: Long? = null,

    @Column(name = "subject_type", length = 64)
    var subjectType: String? = null,

    @Column(name = "subject_id")
    var subjectId: Long? = null,

    @Column(nullable = false, length = 64)
    var source: String = "TMA",

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    var metadata: JsonNode? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
)
