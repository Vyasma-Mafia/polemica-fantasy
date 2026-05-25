package io.github.mralex1810.fantasy.event

enum class AchievementProgressEventType {
    TEAM_CHANGED,
    SERIES_FINALIZED,
    PACK_OPENED,
    COLLECTION_CHANGED,
    MARKETPLACE_CHANGED,
    MARKETPLACE_WATCH_CREATED,
    SOCIAL_ACTION,
    LEGENDARY_UPGRADE,
}

data class AchievementProgressEvent(
    val type: AchievementProgressEventType,
    /** Internal telegram_user.id values, not Telegram platform ids. */
    val internalTelegramUserIds: Set<Long>,
)
