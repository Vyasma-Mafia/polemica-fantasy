package io.github.mralex1810.fantasy.entity

import jakarta.persistence.CascadeType
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
import org.hibernate.annotations.BatchSize

@Entity
@Table(
    name = "fantasy_team_card_game_score",
    uniqueConstraints = [
        UniqueConstraint(
            name = "fantasy_team_card_game_score_fantasy_team_card_id_series_game_id_key",
            columnNames = ["fantasy_team_card_id", "series_game_id"],
        ),
    ],
)
class FantasyTeamCardGameScore(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fantasy_team_card_id", nullable = false)
    var fantasyTeamCard: FantasyTeamCard? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "series_game_id", nullable = false)
    var seriesGame: SeriesGame? = null,

    @Column(name = "base_points")
    var basePoints: Double? = null,

    @Column(name = "perk_bonus")
    var perkBonus: Double? = null,

    @Column(name = "rarity_modifier")
    var rarityModifier: Double? = null,

    @Column(name = "total_score")
    var totalScore: Double? = null,

    @OneToMany(mappedBy = "gameScore", cascade = [CascadeType.ALL], orphanRemoval = true)
    @BatchSize(size = 32)
    var perks: MutableList<FantasyTeamCardGamePerk> = mutableListOf(),
)
