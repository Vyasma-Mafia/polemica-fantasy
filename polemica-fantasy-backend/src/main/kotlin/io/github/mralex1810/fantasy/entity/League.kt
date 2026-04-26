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
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "league")
class League(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false, unique = true, length = 32)
    var code: String = "",

    @Column(nullable = false, length = 128)
    var name: String = "",

    @Column
    var description: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "league_type", nullable = false, length = 32)
    var leagueType: LeagueType = LeagueType.SYSTEM,

    @Column(name = "value_cap")
    var valueCap: Long? = null,

    @Column(name = "max_legendary_count")
    var maxLegendaryCount: Int? = null,

    @Column(name = "min_team_size", nullable = false)
    var minTeamSize: Int = 1,

    @Column(name = "max_team_size", nullable = false)
    var maxTeamSize: Int = 3,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_telegram_user_id")
    var createdByTelegramUser: TelegramUser? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 32)
    var visibility: LeagueVisibility = LeagueVisibility.PUBLIC,

    @Column(name = "entry_fee", nullable = false)
    var entryFee: Long = 0L,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @OneToMany(mappedBy = "league")
    var seriesLeagues: MutableList<SeriesLeague> = mutableListOf(),
)
