package io.github.mralex1810.fantasy.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

enum class ProductCampaignStatus {
    DRAFT,
    QUEUED,
    SENT,
    FAILED,
}

@Entity
@Table(name = "product_campaign")
class ProductCampaign(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false, length = 255)
    var title: String = "",

    @Column(nullable = false, columnDefinition = "TEXT")
    var text: String = "",

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    var audience: ProductAudience = ProductAudience.ALL,

    @Column(name = "button_text", length = 64)
    var buttonText: String? = null,

    @Column(name = "button_url", length = 2048)
    var buttonUrl: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var status: ProductCampaignStatus = ProductCampaignStatus.QUEUED,

    @Column(name = "raw_recipient_count", nullable = false)
    var rawRecipientCount: Int = 0,

    @Column(name = "eligible_recipient_count", nullable = false)
    var eligibleRecipientCount: Int = 0,

    @Column(name = "sent_count", nullable = false)
    var sentCount: Int = 0,

    @Column(name = "skipped_blocked_count", nullable = false)
    var skippedBlockedCount: Int = 0,

    @Column(name = "skipped_preference_count", nullable = false)
    var skippedPreferenceCount: Int = 0,

    @Column(name = "failed_count", nullable = false)
    var failedCount: Int = 0,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "sent_at")
    var sentAt: Instant? = null,
)
