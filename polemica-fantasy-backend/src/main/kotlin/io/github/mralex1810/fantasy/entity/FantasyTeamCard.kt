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
    name = "fantasy_team_card",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_fantasy_team_card_team_slot", columnNames = ["fantasy_team_id", "slot"]),
        UniqueConstraint(name = "uk_fantasy_team_card_team_user_card", columnNames = ["fantasy_team_id", "user_card_id"]),
    ],
)
class FantasyTeamCard(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fantasy_team_id", nullable = false)
    var fantasyTeam: FantasyTeam? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_card_id", nullable = false)
    var userCard: UserCard? = null,

    @Column(nullable = false)
    var slot: Int = 0,

    @Column
    var score: Double? = null,
)
