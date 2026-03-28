package io.github.mralex1810.fantasy.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "tournament")
class Tournament(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false)
    var name: String = "",

    @Column
    var description: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var status: TournamentStatus = TournamentStatus.DRAFT,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var kind: TournamentKind = TournamentKind.STANDALONE,

    @Column(name = "polemica_competition_id")
    var polemicaCompetitionId: Long? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @OneToMany(mappedBy = "tournament")
    var players: MutableList<TournamentPlayer> = mutableListOf(),

    @OneToMany(mappedBy = "tournament")
    var series: MutableList<Series> = mutableListOf(),

    @OneToMany(mappedBy = "tournament")
    var cardPacks: MutableList<CardPack> = mutableListOf(),
)
