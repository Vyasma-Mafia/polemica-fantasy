package io.github.mralex1810.fantasy.controller.admin

import io.github.mralex1810.fantasy.dto.admin.request.BroadcastMessageRequest
import io.github.mralex1810.fantasy.dto.admin.request.CreateReleaseNoteRequest
import io.github.mralex1810.fantasy.dto.admin.request.ProductCampaignDryRunRequest
import io.github.mralex1810.fantasy.dto.admin.request.ProductCampaignPreviewRequest
import io.github.mralex1810.fantasy.dto.admin.request.SendProductCampaignRequest
import io.github.mralex1810.fantasy.dto.admin.request.UpdateReleaseNoteRequest
import io.github.mralex1810.fantasy.dto.admin.response.BroadcastAcceptedResponse
import io.github.mralex1810.fantasy.dto.admin.response.ProductCampaignAudienceCountDto
import io.github.mralex1810.fantasy.dto.admin.response.ProductCampaignDto
import io.github.mralex1810.fantasy.dto.admin.response.ProductCampaignPreviewDto
import io.github.mralex1810.fantasy.dto.admin.response.ReleaseNoteAdminDto
import io.github.mralex1810.fantasy.service.AdminBroadcastNotificationService
import io.github.mralex1810.fantasy.service.ProductAnalyticsService
import io.github.mralex1810.fantasy.service.ProductCampaignService
import io.github.mralex1810.fantasy.service.ReleaseNoteService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/admin/notifications")
class AdminNotificationController(
    private val adminBroadcastNotificationService: AdminBroadcastNotificationService,
    private val productCampaignService: ProductCampaignService,
    private val releaseNoteService: ReleaseNoteService,
    private val productAnalyticsService: ProductAnalyticsService,
) {

    @PostMapping("/broadcast")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun broadcast(@Valid @RequestBody body: BroadcastMessageRequest): BroadcastAcceptedResponse {
        val recipientCount = adminBroadcastNotificationService.queueBroadcast(body.text.trim())
        return BroadcastAcceptedResponse(recipientCount = recipientCount)
    }

    @GetMapping("/campaigns")
    fun listCampaigns(): List<ProductCampaignDto> = productCampaignService.list()

    @PostMapping("/campaigns/dry-run")
    fun dryRunCampaign(
        @Valid @RequestBody body: ProductCampaignDryRunRequest,
    ): ProductCampaignAudienceCountDto = productCampaignService.dryRun(body)

    @PostMapping("/campaigns/preview")
    fun previewCampaign(
        @Valid @RequestBody body: ProductCampaignPreviewRequest,
    ): ProductCampaignPreviewDto = productCampaignService.preview(body)

    @PostMapping("/campaigns/send")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun sendCampaign(
        @Valid @RequestBody body: SendProductCampaignRequest,
    ): ProductCampaignDto = productCampaignService.send(body)

    @PostMapping("/campaigns/{id}/send")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun sendExistingCampaign(@PathVariable id: Long): ProductCampaignDto = productCampaignService.sendExisting(id)

    @GetMapping("/release-notes")
    fun listReleaseNotes(): List<ReleaseNoteAdminDto> = releaseNoteService.listAdmin()

    @PostMapping("/release-notes")
    fun createReleaseNote(
        @Valid @RequestBody body: CreateReleaseNoteRequest,
    ): ReleaseNoteAdminDto = releaseNoteService.createAdmin(body)

    @PutMapping("/release-notes/{id}")
    fun updateReleaseNote(
        @PathVariable id: Long,
        @Valid @RequestBody body: UpdateReleaseNoteRequest,
    ): ReleaseNoteAdminDto = releaseNoteService.updateAdmin(id, body)

    @PostMapping("/release-notes/{id}/publish")
    fun publishReleaseNote(@PathVariable id: Long): ReleaseNoteAdminDto = releaseNoteService.setActiveAdmin(id, true)

    @PostMapping("/release-notes/{id}/unpublish")
    fun unpublishReleaseNote(@PathVariable id: Long): ReleaseNoteAdminDto = releaseNoteService.setActiveAdmin(id, false)

    @GetMapping("/analytics/summary")
    fun analyticsSummary() = productAnalyticsService.summary()

    @GetMapping("/analytics/campaigns")
    fun campaignAnalytics() = productAnalyticsService.campaigns()

    @GetMapping("/analytics/release-notes")
    fun releaseNoteAnalytics() = productAnalyticsService.releaseNotes()
}
