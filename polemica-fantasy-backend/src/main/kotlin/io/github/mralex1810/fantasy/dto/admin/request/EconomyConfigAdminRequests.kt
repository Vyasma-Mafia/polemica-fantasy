package io.github.mralex1810.fantasy.dto.admin.request

import jakarta.validation.constraints.NotBlank

data class UpdateEconomyConfigRequest(
    @field:NotBlank val value: String,
)

data class BulkUpdateEconomyConfigRequest(
    val items: List<BulkEconomyConfigEntry>,
)

data class BulkEconomyConfigEntry(
    @field:NotBlank val key: String,
    @field:NotBlank val value: String,
)
