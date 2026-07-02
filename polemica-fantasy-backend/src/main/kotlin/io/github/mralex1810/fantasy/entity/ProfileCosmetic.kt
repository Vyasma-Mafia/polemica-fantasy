package io.github.mralex1810.fantasy.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "profile_cosmetic")
class ProfileCosmetic(
    @Id
    @Column(name = "code", nullable = false, length = 96)
    var code: String = "",

    @Column(name = "kind", nullable = false, length = 32)
    var kind: String = "",

    @Column(name = "name", nullable = false, length = 128)
    var name: String = "",

    @Column(name = "description", length = 512)
    var description: String? = null,

    @Column(name = "style_token", length = 96)
    var styleToken: String? = null,

    @Column(name = "display_order", nullable = false)
    var displayOrder: Int = 0,

    @Column(name = "enabled", nullable = false)
    var enabled: Boolean = true,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)
