package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.dto.admin.request.ProductCampaignDryRunRequest
import io.github.mralex1810.fantasy.dto.admin.request.ProductCampaignPreviewRequest
import io.github.mralex1810.fantasy.dto.admin.request.SendProductCampaignRequest
import io.github.mralex1810.fantasy.dto.admin.response.ProductCampaignAudienceCountDto
import io.github.mralex1810.fantasy.dto.admin.response.ProductCampaignDto
import io.github.mralex1810.fantasy.dto.admin.response.ProductCampaignPreviewDto
import io.github.mralex1810.fantasy.entity.ProductCampaign
import io.github.mralex1810.fantasy.entity.ProductCampaignStatus
import io.github.mralex1810.fantasy.repository.ProductCampaignRepository
import io.github.mralex1810.fantasy.telegram.ProductCampaignAsyncSender
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class ProductCampaignService(
    private val productCampaignRepository: ProductCampaignRepository,
    private val userSegmentService: UserSegmentService,
    private val productCampaignAsyncSender: ProductCampaignAsyncSender,
) {
    @Transactional(readOnly = true)
    fun list(): List<ProductCampaignDto> = productCampaignRepository.findTop50ByOrderByCreatedAtDesc()
        .map { it.toDto() }

    @Transactional(readOnly = true)
    fun dryRun(request: ProductCampaignDryRunRequest): ProductCampaignAudienceCountDto {
        val audience = userSegmentService.parseAudience(request.audience)
        val counts = userSegmentService.counts(audience)
        return ProductCampaignAudienceCountDto(
            audience = audience.name,
            rawCount = counts.rawCount,
            eligibleCount = counts.eligibleCount,
        )
    }

    @Transactional(readOnly = true)
    fun preview(request: ProductCampaignPreviewRequest): ProductCampaignPreviewDto {
        val audience = userSegmentService.parseAudience(request.audience)
        val counts = userSegmentService.counts(audience)
        return ProductCampaignPreviewDto(
            text = request.text.trim(),
            audience = audience.name,
            buttonText = request.buttonText?.trim()?.takeIf { it.isNotEmpty() },
            buttonUrl = request.buttonUrl?.trim()?.takeIf { it.isNotEmpty() },
            rawCount = counts.rawCount,
            eligibleCount = counts.eligibleCount,
        )
    }

    @Transactional
    fun send(request: SendProductCampaignRequest): ProductCampaignDto {
        val audience = userSegmentService.parseAudience(request.audience)
        val counts = userSegmentService.counts(audience)
        val campaign = productCampaignRepository.save(
            ProductCampaign(
                title = request.title.trim(),
                text = request.text.trim(),
                audience = audience,
                buttonText = request.buttonText?.trim()?.takeIf { it.isNotEmpty() },
                buttonUrl = request.buttonUrl?.trim()?.takeIf { it.isNotEmpty() },
                status = ProductCampaignStatus.QUEUED,
                rawRecipientCount = counts.rawCount,
                eligibleRecipientCount = counts.eligibleCount,
                createdAt = Instant.now(),
            ),
        )
        productCampaignAsyncSender.send(campaign.id!!)
        return campaign.toDto()
    }

    @Transactional
    fun markDelivered(campaignId: Long, sent: Int, skippedBlocked: Int, skippedPreference: Int, failed: Int) {
        val campaign = productCampaignRepository.findById(campaignId).orElse(null) ?: return
        campaign.sentCount = sent
        campaign.skippedBlockedCount = skippedBlocked
        campaign.skippedPreferenceCount = skippedPreference
        campaign.failedCount = failed
        campaign.status = if (failed > 0 && sent == 0) ProductCampaignStatus.FAILED else ProductCampaignStatus.SENT
        campaign.sentAt = Instant.now()
        productCampaignRepository.save(campaign)
    }

    fun ProductCampaign.toDto(): ProductCampaignDto = ProductCampaignDto(
        id = id!!,
        title = title,
        text = text,
        audience = audience.name,
        buttonText = buttonText,
        buttonUrl = buttonUrl,
        status = status.name,
        rawRecipientCount = rawRecipientCount,
        eligibleRecipientCount = eligibleRecipientCount,
        sentCount = sentCount,
        skippedBlockedCount = skippedBlockedCount,
        skippedPreferenceCount = skippedPreferenceCount,
        failedCount = failedCount,
        createdAt = createdAt,
        sentAt = sentAt,
    )
}
