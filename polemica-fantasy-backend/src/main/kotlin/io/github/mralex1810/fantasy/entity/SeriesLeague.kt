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
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "series_league",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_series_league", columnNames = ["series_id", "league_id"]),
    ],
)
class SeriesLeague(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "series_id", nullable = false)
    var series: Series? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "league_id", nullable = false)
    var league: League? = null,

    @Column(name = "value_cap_override")
    var valueCapOverride: Long? = null,

    @Column(name = "reward_scale_override")
    var rewardScaleOverride: Int? = null,

    @Column(nullable = false)
    var enabled: Boolean = true,

    @OneToMany(mappedBy = "seriesLeague")
    var fantasyTeams: MutableList<FantasyTeam> = mutableListOf(),
)
