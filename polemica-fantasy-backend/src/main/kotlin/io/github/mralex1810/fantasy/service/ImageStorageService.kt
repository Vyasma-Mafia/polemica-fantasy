package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.config.S3Properties
import org.springframework.stereotype.Service
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest

@Service
class ImageStorageService(
    private val s3Client: S3Client,
    private val s3Properties: S3Properties,
) {

    fun playerPhotoKey(fantasyPlayerId: Long, extension: String): String =
        "players/$fantasyPlayerId/photo.$extension"

    fun cardImageKey(cardTemplateId: Long, extension: String): String =
        "cards/$cardTemplateId/image.$extension"

    /**
     * Uploads an object and returns its public URL (direct GET from S3/MinIO).
     */
    fun upload(key: String, data: ByteArray, contentType: String): String {
        s3Client.putObject(
            PutObjectRequest.builder()
                .bucket(s3Properties.bucket)
                .key(key)
                .contentType(contentType)
                .build(),
            RequestBody.fromBytes(data),
        )
        return publicUrlForKey(key)
    }

    fun delete(key: String) {
        s3Client.deleteObject(
            DeleteObjectRequest.builder()
                .bucket(s3Properties.bucket)
                .key(key)
                .build(),
        )
    }

    /**
     * Resolves a stored object key from a full URL previously returned by [upload], or returns the input if it is already a key.
     */
    fun keyFromUrlOrKey(urlOrKey: String): String {
        val marker = "${s3Properties.bucket}/"
        val idx = urlOrKey.indexOf(marker)
        return if (idx >= 0) urlOrKey.substring(idx + marker.length) else urlOrKey.removePrefix("/")
    }

    private fun publicUrlForKey(key: String): String {
        val base = s3Properties.endpoint.trimEnd('/')
        val bucket = s3Properties.bucket
        return "$base/$bucket/$key"
    }
}
