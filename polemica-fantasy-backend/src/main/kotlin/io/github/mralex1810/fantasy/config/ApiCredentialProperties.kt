package io.github.mralex1810.fantasy.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.api-credentials")
data class ApiCredentialProperties(
    /** Enable only when nginx is the exclusive boundary and overwrites X-Real-IP. */
    var trustXRealIp: Boolean = false,
)
