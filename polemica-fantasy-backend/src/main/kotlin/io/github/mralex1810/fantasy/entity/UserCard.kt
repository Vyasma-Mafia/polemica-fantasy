package io.github.mralex1810.fantasy.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "user_card")
class UserCard(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "telegram_user_id", nullable = false)
    var telegramUser: TelegramUser? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "card_template_id", nullable = false)
    var cardTemplate: CardTemplate? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "source_card_pack_id", nullable = true)
    var sourceCardPack: CardPack? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "card_skin_id", nullable = true)
    var cardSkin: CardSkin? = null,

    /** User who crafted this card via EPIC → LEGENDARY upgrade; admin-issued LEGENDARY stays null. */
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "crafted_by_telegram_user_id", nullable = true)
    var craftedBy: TelegramUser? = null,

    @Column(name = "acquired_at", nullable = false)
    var acquiredAt: Instant = Instant.now(),

    @Column(name = "uses_remaining", nullable = false)
    var usesRemaining: Int = 0,

    @Column(name = "times_renewed", nullable = false)
    var timesRenewed: Int = 0,

    @Column(name = "deleted_at")
    var deletedAt: Instant? = null,

    @OneToMany(mappedBy = "userCard")
    var fantasyTeamCards: MutableList<FantasyTeamCard> = mutableListOf(),
)
