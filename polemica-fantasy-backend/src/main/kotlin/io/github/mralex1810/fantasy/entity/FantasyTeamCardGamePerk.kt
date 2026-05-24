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
    name = "fantasy_team_card_game_perk",
    uniqueConstraints = [
        UniqueConstraint(
            name = "fantasy_team_card_game_perk_card_game_score_id_perk_id_key",
            columnNames = ["card_game_score_id", "perk_id"],
        ),
    ],
)
class FantasyTeamCardGamePerk(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "card_game_score_id", nullable = false)
    var gameScore: FantasyTeamCardGameScore? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "perk_id", nullable = false)
    var perk: Perk? = null,

    @Column(name = "bonus_points", nullable = false)
    var bonusPoints: Double = 0.0,
)
