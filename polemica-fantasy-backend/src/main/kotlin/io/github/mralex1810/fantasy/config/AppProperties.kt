package io.github.mralex1810.fantasy.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app")
data class AppProperties(
    val webappBaseUrl: String = "https://fantasy.polemica.ru",
)
