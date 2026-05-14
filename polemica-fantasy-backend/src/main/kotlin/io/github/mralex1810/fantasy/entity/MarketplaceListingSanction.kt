package io.github.mralex1810.fantasy.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "marketplace_listing_sanction")
class MarketplaceListingSanction(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "listing_id", nullable = false)
    var listing: MarketplaceListing? = null,

    @Column(nullable = false, columnDefinition = "TEXT")
    var reason: String = "",

    @Column(name = "seller_fine", nullable = false)
    var sellerFine: Long = 0,

    @Column(name = "buyer_fine", nullable = false)
    var buyerFine: Long = 0,

    @Column(name = "complainant_reward", nullable = false)
    var complainantReward: Long = 0,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "admin_username", nullable = false, length = 64)
    var adminUsername: String = "",
)
