package io.github.mralex1810.fantasy.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "economy_config")
class EconomyConfig(
    @Id
    @Column(name = "key", length = 64, nullable = false)
    var key: String = "",

    @Column(name = "value", length = 256, nullable = false)
    var value: String = "",

    @Column(name = "description", columnDefinition = "TEXT")
    var description: String? = null,
)
