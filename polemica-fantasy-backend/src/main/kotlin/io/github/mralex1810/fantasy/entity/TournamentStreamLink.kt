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

@Entity
@Table(name = "tournament_stream_link")
class TournamentStreamLink(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tournament_id", nullable = false)
    var tournament: Tournament? = null,

    @Column(length = 128)
    var label: String? = null,

    @Column(nullable = false, length = 2048)
    var url: String = "",

    @Column(name = "display_order", nullable = false)
    var displayOrder: Int = 0,
)
