package io.github.mralex1810.fantasy.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.admin")
data class AdminProperties(
    var username: String = "admin",
    var password: String = "defaultPassword123",
    var moderatorUsername: String = "",
    var moderatorPassword: String = "",
)
