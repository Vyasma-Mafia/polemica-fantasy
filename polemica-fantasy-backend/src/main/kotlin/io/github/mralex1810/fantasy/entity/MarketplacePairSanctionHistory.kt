package io.github.mralex1810.fantasy.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "marketplace_pair_sanction_history")
class MarketplacePairSanctionHistory(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    var id: Long? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "user_id_low", nullable = false)
    var userIdLow: Long = 0L,

    @Column(name = "user_id_high", nullable = false)
    var userIdHigh: Long = 0L,

    @Column(name = "reason", nullable = false, columnDefinition = "text")
    var reason: String = "",

    @Column(name = "fantiki_taken_low", nullable = false)
    var fantikiTakenLow: Long = 0L,

    @Column(name = "fantiki_taken_high", nullable = false)
    var fantikiTakenHigh: Long = 0L,

    @Column(name = "cards_count_low", nullable = false)
    var cardsCountLow: Int = 0,

    @Column(name = "cards_count_high", nullable = false)
    var cardsCountHigh: Int = 0,
)
