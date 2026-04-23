package io.github.mralex1810.fantasy.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "s3")
data class S3Properties(
    val endpoint: String = "",
    /**
     * Base URL for object links returned in API (browser, external scripts).
     * When empty, [endpoint] is used. In Docker, set to e.g. `http://localhost:9000` while
     * [endpoint] stays `http://minio:9000` for the S3 client.
     */
    val publicBaseUrl: String = "",
    val region: String = "us-east-1",
    val bucket: String = "",
    val accessKey: String = "",
    val secretKey: String = "",
    val ensureBucketOnStartup: Boolean = true,
) {
    fun effectivePublicBaseUrl(): String = publicBaseUrl.trim().ifEmpty { endpoint }
}
