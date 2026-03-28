package io.github.mralex1810.fantasy.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "polemica.api")
data class PolemicaProperties(
    val baseUrl: String = "https://polemicagame.com",
    val profileSiteBaseUrl: String = "https://polemicagame.com",
    val username: String = "",
    val password: String = "",
)
