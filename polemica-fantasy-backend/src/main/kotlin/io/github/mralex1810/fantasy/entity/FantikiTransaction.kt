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
import java.time.Instant

@Entity
@Table(name = "fantiki_transaction")
class FantikiTransaction(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "telegram_user_id", nullable = false)
    var telegramUser: TelegramUser? = null,

    @Column(nullable = false)
    var amount: Long = 0L,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    var reason: FantikiTransactionReason = FantikiTransactionReason.INITIAL,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
)
