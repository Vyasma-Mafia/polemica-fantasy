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
        UniqueConstraint(name = "uk_fantasy_team_user_series", columnNames = ["telegram_user_id", "series_id"]),
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

    @Column(name = "total_score")
    var totalScore: Double? = null,

    @Column(name = "submitted_at", nullable = false)
    var submittedAt: Instant = Instant.now(),

    @OneToMany(mappedBy = "fantasyTeam")
    var cards: MutableList<FantasyTeamCard> = mutableListOf(),
)
