package io.github.mralex1810.fantasy.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.MapsId
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "onboarding_progress")
class OnboardingProgress(
    @Id
    @Column(name = "telegram_user_id")
    var telegramUserId: Long? = null,

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "telegram_user_id", nullable = false)
    var telegramUser: TelegramUser? = null,

    @Column(name = "store_opened_at")
    var storeOpenedAt: Instant? = null,

    @Column(name = "collection_viewed_at")
    var collectionViewedAt: Instant? = null,

    @Column(name = "notifications_viewed_at")
    var notificationsViewedAt: Instant? = null,

    @Column(name = "results_viewed_at")
    var resultsViewedAt: Instant? = null,

    @Column(name = "first_pack_opened_at")
    var firstPackOpenedAt: Instant? = null,

    @Column(name = "first_team_submitted_at")
    var firstTeamSubmittedAt: Instant? = null,

    @Column(name = "no_action_nudge_sent_at")
    var noActionNudgeSentAt: Instant? = null,

    @Column(name = "action_no_team_nudge_sent_at")
    var actionNoTeamNudgeSentAt: Instant? = null,

    @Column(name = "open_deadline_nudge_sent_at")
    var openDeadlineNudgeSentAt: Instant? = null,

    @Column(name = "after_first_team_nudge_sent_at")
    var afterFirstTeamNudgeSentAt: Instant? = null,

    @Column(name = "last_nudge_sent_at")
    var lastNudgeSentAt: Instant? = null,

    @Column(name = "nudge_count", nullable = false)
    var nudgeCount: Int = 0,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)

enum class OnboardingStep {
    OPEN_STORE,
    OPEN_PACK,
    VIEW_COLLECTION,
    SUBMIT_FIRST_TEAM,
    OPEN_NOTIFICATIONS,
    VIEW_RESULTS,
}

enum class OnboardingNudgeType {
    NO_ACTION,
    ACTION_NO_TEAM,
    OPEN_DEADLINE,
    AFTER_FIRST_TEAM,
}
