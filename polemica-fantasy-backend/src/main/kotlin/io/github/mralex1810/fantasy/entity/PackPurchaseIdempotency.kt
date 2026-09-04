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
import java.time.Instant
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Entity
@Table(name = "pack_purchase_idempotency")
class PackPurchaseIdempotency(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "telegram_user_id", nullable = false)
    var telegramUser: TelegramUser? = null,

    @Column(name = "key_hash", nullable = false, length = 64)
    var keyHash: String = "",

    @Column(name = "canonical_request_hash", nullable = false, length = 64)
    var canonicalRequestHash: String = "",

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "card_pack_id", nullable = false)
    var cardPack: CardPack? = null,

    @Column(name = "response_kind", nullable = false, length = 24)
    var responseKind: String = "OPENED",

    @Column(name = "balance_after", nullable = false)
    var balanceAfter: Long = 0,

    @Column(name = "pack_choice_id")
    var packChoiceId: Long? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "user_card_ids", nullable = false, columnDefinition = "jsonb")
    var userCardIds: String = "[]",

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_json", nullable = false, columnDefinition = "jsonb")
    var responseJson: String = "{}",

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
)
