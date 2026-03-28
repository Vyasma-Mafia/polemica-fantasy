package io.github.mralex1810.fantasy.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table

@Entity
@Table(name = "card_template")
class CardTemplate(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fantasy_player_id", nullable = false)
    var fantasyPlayer: FantasyPlayer? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var rarity: Rarity = Rarity.COMMON,

    @Column(name = "image_url")
    var imageUrl: String? = null,

    @Column
    var description: String? = null,

    @OneToMany(mappedBy = "cardTemplate")
    var achievements: MutableList<CardTemplateAchievement> = mutableListOf(),

    @OneToMany(mappedBy = "cardTemplate")
    var userCards: MutableList<UserCard> = mutableListOf(),
)
