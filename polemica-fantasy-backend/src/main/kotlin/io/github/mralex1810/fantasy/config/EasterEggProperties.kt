package io.github.mralex1810.fantasy.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "easter-egg")
data class EasterEggProperties(
    var tyulenchikImageUrl: String = "",
)
