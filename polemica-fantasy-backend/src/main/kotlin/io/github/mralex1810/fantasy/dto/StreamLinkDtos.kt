package io.github.mralex1810.fantasy.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class StreamLinkDto(
    val label: String?,
    val url: String,
)

data class StreamLinkRequest(
    @field:Size(max = 128)
    val label: String? = null,
    @field:NotBlank @field:Size(max = 2048)
    val url: String,
)
