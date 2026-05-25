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
import java.time.Instant

@Entity
@Table(
    name = "fantasy_team",
    uniqueConstraints = [
        UniqueConstraint(
            name = "fantasy_team_user_series_league_key",
            columnNames = ["telegram_user_id", "series_id", "series_league_id"],
        ),
    ],
)
class FantasyTeam(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "telegram_user_id", nullable = false)
    var telegramUser: TelegramUser? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "series_id", nullable = false)
    var series: Series? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "series_league_id", nullable = false)
    var seriesLeague: SeriesLeague? = null,

    @Column(name = "total_score")
    var totalScore: Double? = null,

    @Column(name = "submitted_at", nullable = false)
    var submittedAt: Instant = Instant.now(),

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @OneToMany(mappedBy = "fantasyTeam")
    var cards: MutableList<FantasyTeamCard> = mutableListOf(),
)
