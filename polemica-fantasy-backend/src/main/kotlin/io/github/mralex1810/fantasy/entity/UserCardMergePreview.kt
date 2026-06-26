package io.github.mralex1810.fantasy.entity

import com.fasterxml.jackson.databind.JsonNode
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
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

@Entity
@Table(name = "user_card_merge_preview")
class UserCardMergePreview(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "telegram_user_id", nullable = false)
    var telegramUser: TelegramUser? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var operation: CardMergeOperation = CardMergeOperation.COMMON_TO_RARE,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_user_card_ids", nullable = false, columnDefinition = "jsonb")
    var inputUserCardIds: JsonNode? = null,

    @Column(name = "input_set_hash", nullable = false, length = 96)
    var inputSetHash: String = "",

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "fixed_perk_ids", nullable = false, columnDefinition = "jsonb")
    var fixedPerkIds: JsonNode? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "offered_perk_ids", nullable = false, columnDefinition = "jsonb")
    var offeredPerkIds: JsonNode? = null,

    @Column(name = "selected_skin_source_user_card_id")
    var selectedSkinSourceUserCardId: Long? = null,

    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant = Instant.now(),

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),

    @Column(name = "consumed_at")
    var consumedAt: Instant? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "result_user_card_id")
    var resultUserCard: UserCard? = null,
)
