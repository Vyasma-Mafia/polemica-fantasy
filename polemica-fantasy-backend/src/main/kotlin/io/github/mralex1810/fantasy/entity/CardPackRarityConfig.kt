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
import jakarta.persistence.Table

@Entity
@Table(name = "card_pack_rarity_config")
class CardPackRarityConfig(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "card_pack_id", nullable = false)
    var cardPack: CardPack? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var rarity: Rarity = Rarity.COMMON,

    @Column(name = "cards_count", nullable = false)
    var cardsCount: Int = 0,
)
