package io.github.mralex1810.fantasy.telegram

import io.github.mralex1810.fantasy.entity.NotificationCategory
import io.github.mralex1810.fantasy.repository.ProductCampaignRepository
import io.github.mralex1810.fantasy.service.NotificationDeliveryService
import io.github.mralex1810.fantasy.service.UserSegmentService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

@Component
class ProductCampaignAsyncSender(
    private val productCampaignRepository: ProductCampaignRepository,
    private val userSegmentService: UserSegmentService,
    private val notificationDeliveryService: NotificationDeliveryService,
    private val notificationButtonFactory: NotificationButtonFactory,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Async
    fun send(campaignId: Long) {
        val campaign = productCampaignRepository.findById(campaignId).orElse(null) ?: return
        val recipients = userSegmentService.eligibleTelegramIds(campaign.audience)
        val replyMarkup =
            if (!campaign.buttonText.isNullOrBlank() && !campaign.buttonUrl.isNullOrBlank()) {
                notificationButtonFactory.singleButton(
                    campaign.buttonText!!,
                    withCampaignId(campaign.buttonUrl!!, campaignId),
                )
            } else {
                null
            }
        val report = notificationDeliveryService.deliverToMany(
            recipients = recipients,
            category = NotificationCategory.ONBOARDING_TIPS,
            textProvider = { campaign.text },
            replyMarkup = replyMarkup,
        )
        log.info(
            "Product campaign {} delivery done: sent={}, skippedBlocked={}, skippedPreference={}, failed={}",
            campaignId,
            report.sent,
            report.skippedBlocked,
            report.skippedPreference,
            report.failed,
        )
        // Resolve lazily to avoid a circular constructor dependency.
        productCampaignRepository.findById(campaignId).ifPresent {
            it.sentCount = report.sent
            it.skippedBlockedCount = report.skippedBlocked
            it.skippedPreferenceCount = report.skippedPreference
            it.failedCount = report.failed
            it.status = if (report.failed > 0 && report.sent == 0) {
                io.github.mralex1810.fantasy.entity.ProductCampaignStatus.FAILED
            } else {
                io.github.mralex1810.fantasy.entity.ProductCampaignStatus.SENT
            }
            it.sentAt = java.time.Instant.now()
            productCampaignRepository.save(it)
        }
    }

    private fun withCampaignId(url: String, campaignId: Long): String {
        val trimmed = url.trim()
        if (trimmed.contains("campaignId=")) return trimmed
        val hashIndex = trimmed.indexOf('#')
        val base = if (hashIndex >= 0) trimmed.substring(0, hashIndex) else trimmed
        val fragment = if (hashIndex >= 0) trimmed.substring(hashIndex) else ""
        val separator = if (base.contains('?')) "&" else "?"
        return "$base${separator}campaignId=$campaignId$fragment"
    }
}
