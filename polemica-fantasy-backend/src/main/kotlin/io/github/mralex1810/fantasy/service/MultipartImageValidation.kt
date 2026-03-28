package io.github.mralex1810.fantasy.service

import org.springframework.http.HttpStatus
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException

private val ALLOWED_IMAGE_TYPES = mapOf(
    "image/jpeg" to "jpg",
    "image/png" to "png",
    "image/webp" to "webp",
)

private const val MAX_BYTES = 5L * 1024 * 1024

fun MultipartFile.validateImageUpload() {
    val ct = contentType ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing content type")
    if (ct !in ALLOWED_IMAGE_TYPES.keys) {
        throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Allowed image types: JPEG, PNG, WebP")
    }
    if (size > MAX_BYTES) {
        throw ResponseStatusException(HttpStatus.BAD_REQUEST, "File too large (max 5MB)")
    }
}

fun MultipartFile.imageExtension(): String =
    ALLOWED_IMAGE_TYPES[contentType]
        ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported image type")
