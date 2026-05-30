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
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "series_player",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_series_player_series_tournament_player", columnNames = ["series_id", "tournament_player_id"]),
    ],
)
class SeriesPlayer(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "series_id", nullable = false)
    var series: Series? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tournament_player_id", nullable = false)
    var tournamentPlayer: TournamentPlayer? = null,

    @Column(name = "replacement_polemica_user_id")
    var replacementPolemicaUserId: Long? = null,
)
