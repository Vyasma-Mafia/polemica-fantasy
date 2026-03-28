package io.github.mralex1810.fantasy.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import java.net.URI

@Configuration
class S3Config {

    @Bean
    fun s3Client(properties: S3Properties): S3Client {
        val credentials = AwsBasicCredentials.create(properties.accessKey, properties.secretKey)
        return S3Client.builder()
            .region(Region.of(properties.region))
            .credentialsProvider(StaticCredentialsProvider.create(credentials))
            .endpointOverride(URI.create(properties.endpoint))
            .serviceConfiguration(
                S3Configuration.builder()
                    .pathStyleAccessEnabled(true)
                    .build(),
            )
            .build()
    }
}
