package io.github.mralex1810.fantasy.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant

data class MarketplacePairClearanceId(
    var userIdLow: Long = 0L,
    var userIdHigh: Long = 0L,
) : Serializable

@Entity
@IdClass(MarketplacePairClearanceId::class)
@Table(name = "marketplace_pair_clearance")
class MarketplacePairClearance(
    @Id
    @Column(name = "user_id_low", nullable = false)
    var userIdLow: Long = 0L,

    @Id
    @Column(name = "user_id_high", nullable = false)
    var userIdHigh: Long = 0L,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "note")
    var note: String? = null,
)
