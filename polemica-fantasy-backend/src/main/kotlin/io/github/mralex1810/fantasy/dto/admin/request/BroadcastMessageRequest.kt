package io.github.mralex1810.fantasy.dto.admin.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class BroadcastMessageRequest(
    @field:NotBlank
    @field:Size(max = 4096)
    val text: String,
)
