package io.github.mralex1810.fantasy.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "s3")
data class S3Properties(
    val endpoint: String = "",
    val region: String = "us-east-1",
    val bucket: String = "",
    val accessKey: String = "",
    val secretKey: String = "",
    val ensureBucketOnStartup: Boolean = true,
)
