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
@Table(name = "series")
class Series(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tournament_id", nullable = false)
    var tournament: Tournament? = null,

    @Column(nullable = false)
    var name: String = "",

    @Column(name = "name_prefix")
    var namePrefix: String? = null,

    @Column(name = "game_num_from")
    var gameNumFrom: Long? = null,

    @Column(name = "game_num_to")
    var gameNumTo: Long? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var status: SeriesStatus = SeriesStatus.UPCOMING,

    @Column(name = "starts_at", nullable = false)
    var startsAt: Instant = Instant.now(),

    @Column(name = "team_deadline", nullable = false)
    var teamDeadline: Instant = Instant.now(),

    @Column(name = "finalized", nullable = false)
    var finalized: Boolean = false,

    @OneToMany(mappedBy = "series")
    var seriesPlayers: MutableList<SeriesPlayer> = mutableListOf(),

    @OneToMany(mappedBy = "series")
    var seriesGames: MutableList<SeriesGame> = mutableListOf(),

    @OneToMany(mappedBy = "series")
    var seriesLeagues: MutableList<SeriesLeague> = mutableListOf(),

    @OneToMany(mappedBy = "series")
    var fantasyTeams: MutableList<FantasyTeam> = mutableListOf(),
)
