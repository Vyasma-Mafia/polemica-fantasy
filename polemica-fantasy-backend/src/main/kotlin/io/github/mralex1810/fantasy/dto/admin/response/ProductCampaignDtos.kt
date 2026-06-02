package io.github.mralex1810.fantasy.dto.admin.response

import java.time.Instant

data class ProductCampaignAudienceCountDto(
    val audience: String,
    val rawCount: Int,
    val eligibleCount: Int,
)

data class ProductCampaignPreviewDto(
    val text: String,
    val audience: String,
    val buttonText: String?,
    val buttonUrl: String?,
    val rawCount: Int,
    val eligibleCount: Int,
)

data class ProductCampaignDto(
    val id: Long,
    val title: String,
    val text: String,
    val audience: String,
    val buttonText: String?,
    val buttonUrl: String?,
    val status: String,
    val rawRecipientCount: Int,
    val eligibleRecipientCount: Int,
    val sentCount: Int,
    val skippedBlockedCount: Int,
    val skippedPreferenceCount: Int,
    val failedCount: Int,
    val createdAt: Instant,
    val sentAt: Instant?,
)

data class ProductAnalyticsSummaryDto(
    val totalUsers: Long,
    val botBlockedUsers: Long,
    val botBlockedPercent: Double,
    val startToFirstAction24h: Long,
    val startToFirstTeam7d: Long,
    val actionNoTeamUsers: Long,
    val actionNoTeamTeamSubmit7d: Long,
    val checklistCompletedUsers: Long,
    val checklistCompletedPercent: Double,
)

data class ProductCampaignAnalyticsDto(
    val campaignId: Long,
    val title: String,
    val audience: String,
    val sentCount: Int,
    val openedCount: Long,
    val clickedCount: Long,
    val actedCount: Long,
)

data class ReleaseNoteAnalyticsDto(
    val releaseNoteId: Long,
    val title: String,
    val audience: String,
    val seenCount: Long,
    val featureUsedCount: Long,
)

data class ReleaseNoteAdminDto(
    val id: Long,
    val title: String,
    val body: String,
    val buttonText: String?,
    val buttonUrl: String?,
    val audience: String,
    val minAppVersion: String?,
    val active: Boolean,
    val publishedAt: Instant,
    val createdAt: Instant,
)
