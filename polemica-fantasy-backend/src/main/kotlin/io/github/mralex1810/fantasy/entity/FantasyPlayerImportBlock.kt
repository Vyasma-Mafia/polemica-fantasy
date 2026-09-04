package io.github.mralex1810.fantasy.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "fantasy_player_import_block")
class FantasyPlayerImportBlock(
    @Id
    @Column(name = "polemica_user_id", nullable = false)
    var polemicaUserId: Long = 0L,

    @Column(length = 1000)
    var reason: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
)
