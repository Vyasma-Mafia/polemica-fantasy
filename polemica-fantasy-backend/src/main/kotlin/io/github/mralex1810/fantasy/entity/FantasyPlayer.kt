package io.github.mralex1810.fantasy.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.Table

@Entity
@Table(name = "fantasy_player")
class FantasyPlayer(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "polemica_user_id", nullable = false, unique = true)
    var polemicaUserId: Long = 0L,

    @Column(nullable = false)
    var nickname: String = "",

    @Column(name = "photo_url")
    var photoUrl: String? = null,

    @OneToMany(mappedBy = "fantasyPlayer")
    var tournamentPlayers: MutableList<TournamentPlayer> = mutableListOf(),

    @OneToMany(mappedBy = "fantasyPlayer")
    var cardTemplates: MutableList<CardTemplate> = mutableListOf(),

    @OneToMany(mappedBy = "fantasyPlayer")
    var aliases: MutableList<FantasyPlayerAlias> = mutableListOf(),
)
