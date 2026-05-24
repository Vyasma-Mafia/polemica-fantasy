package io.github.mralex1810.fantasy.dto.user.request

import com.fasterxml.jackson.databind.JsonNode
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class ProductEventRequest(
    @field:NotBlank
    @field:Size(max = 64)
    val eventType: String,

    val campaignId: Long? = null,
    val releaseNoteId: Long? = null,

    @field:Size(max = 64)
    val subjectType: String? = null,

    val subjectId: Long? = null,

    @field:Size(max = 64)
    val source: String? = null,

    val metadata: JsonNode? = null,
)
