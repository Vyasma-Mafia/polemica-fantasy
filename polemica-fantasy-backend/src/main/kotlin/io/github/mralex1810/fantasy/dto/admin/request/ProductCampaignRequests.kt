package io.github.mralex1810.fantasy.dto.admin.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class ProductCampaignDryRunRequest(
    @field:NotBlank
    val audience: String,
)

data class ProductCampaignPreviewRequest(
    @field:NotBlank
    @field:Size(max = 4096)
    val text: String,

    @field:NotBlank
    val audience: String,

    @field:Size(max = 64)
    val buttonText: String? = null,

    @field:Size(max = 2048)
    val buttonUrl: String? = null,
)

data class SendProductCampaignRequest(
    @field:NotBlank
    @field:Size(max = 255)
    val title: String,

    @field:NotBlank
    @field:Size(max = 4096)
    val text: String,

    @field:NotBlank
    val audience: String,

    @field:Size(max = 64)
    val buttonText: String? = null,

    @field:Size(max = 2048)
    val buttonUrl: String? = null,
)

data class CreateReleaseNoteRequest(
    @field:NotBlank
    @field:Size(max = 255)
    val title: String,

    @field:NotBlank
    val body: String,

    val audience: String = "ALL",
    val minAppVersion: String? = null,
    val active: Boolean = true,
)

data class UpdateReleaseNoteRequest(
    @field:Size(max = 255)
    val title: String? = null,

    val body: String? = null,
    val audience: String? = null,
    val minAppVersion: String? = null,
    val active: Boolean? = null,
)
