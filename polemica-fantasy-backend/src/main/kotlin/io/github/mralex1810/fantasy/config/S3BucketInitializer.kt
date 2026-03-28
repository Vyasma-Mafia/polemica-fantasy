package io.github.mralex1810.fantasy.config

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.annotation.Profile
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.CreateBucketRequest
import software.amazon.awssdk.services.s3.model.HeadBucketRequest

@Component
@Profile("!test")
@ConditionalOnProperty(name = ["s3.ensure-bucket-on-startup"], havingValue = "true", matchIfMissing = true)
class S3BucketInitializer(
    private val s3Client: S3Client,
    private val s3Properties: S3Properties,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener(ApplicationReadyEvent::class)
    fun ensureBucketExists() {
        val bucket = s3Properties.bucket
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build())
            log.debug("S3 bucket '{}' already exists", bucket)
        } catch (e: Exception) {
            log.info("Creating S3 bucket '{}'", bucket)
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build())
        }
    }
}
