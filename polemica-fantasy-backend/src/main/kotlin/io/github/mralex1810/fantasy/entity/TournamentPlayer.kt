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

@Entity
@Table(name = "tournament_player")
class TournamentPlayer(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tournament_id", nullable = false)
    var tournament: Tournament? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fantasy_player_id", nullable = false)
    var fantasyPlayer: FantasyPlayer? = null,

    /**
     * If true, this player is not included in auto-generated card pack draws for this tournament
     * (both "all tournament players" and explicit pack pools).
     */
    @Column(name = "excluded_from_pack_pool", nullable = false)
    var excludedFromPackPool: Boolean = false,

    @OneToMany(mappedBy = "tournamentPlayer")
    var seriesPlayers: MutableList<SeriesPlayer> = mutableListOf(),
)
