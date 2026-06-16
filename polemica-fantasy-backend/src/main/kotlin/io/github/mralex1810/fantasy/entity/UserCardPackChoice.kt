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
import java.time.Instant

@Entity
@Table(name = "user_card_pack_choice")
class UserCardPackChoice(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "telegram_user_id", nullable = false)
    var telegramUser: TelegramUser? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "card_pack_id", nullable = false)
    var cardPack: CardPack? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "options", nullable = false, columnDefinition = "jsonb")
    var options: String = "[]",

    @Column(name = "selected_option_id")
    var selectedOptionId: String? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "selected_user_card_ids", columnDefinition = "jsonb")
    var selectedUserCardIds: String? = null,

    @Column(name = "payment_kind", nullable = false)
    var paymentKind: String = "ZERO",

    @Column(name = "price_fantiki_reserved", nullable = false)
    var priceFantikiReserved: Long = 0,

    @Column(name = "free_usage_reserved", nullable = false)
    var freeUsageReserved: Boolean = false,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "selected_at")
    var selectedAt: Instant? = null,
)
