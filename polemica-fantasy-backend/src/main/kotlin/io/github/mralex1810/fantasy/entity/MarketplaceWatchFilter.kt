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
import jakarta.persistence.JoinTable
import jakarta.persistence.ManyToMany
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "marketplace_watch_filter")
class MarketplaceWatchFilter(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "telegram_user_id", nullable = false)
    var telegramUser: TelegramUser? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fantasy_player_id")
    var fantasyPlayer: FantasyPlayer? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tournament_id")
    var tournament: Tournament? = null,

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    var rarity: Rarity? = null,

    @Column(name = "max_price")
    var maxPrice: Long? = null,

    @Column(name = "perk_ids_key", nullable = false, length = 1024)
    var perkIdsKey: String = "",

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "marketplace_watch_filter_perk",
        joinColumns = [JoinColumn(name = "watch_filter_id")],
        inverseJoinColumns = [JoinColumn(name = "perk_id")],
    )
    var perks: MutableSet<Perk> = linkedSetOf(),

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
)
