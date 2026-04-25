package io.github.mralex1810.fantasy.entity

enum class NotificationCategory(
    val userToggleable: Boolean,
    val enabledByDefault: Boolean,
    val description: String,
) {
    ADMIN_BROADCAST(
        userToggleable = false,
        enabledByDefault = true,
        description = "Сообщения от администрации",
    ),
    SERIES_START(
        userToggleable = true,
        enabledByDefault = true,
        description = "Уведомления о старте серии",
    ),
    TEAM_DEADLINE_REMINDER(
        userToggleable = true,
        enabledByDefault = true,
        description = "Напоминание о дедлайне команды",
    ),
    SERIES_FINALIZED(
        userToggleable = true,
        enabledByDefault = true,
        description = "Результаты серии",
    ),
    SERIES_ROSTER_CHANGE(
        userToggleable = true,
        enabledByDefault = true,
        description = "Замена карт в составе серии",
    ),
    MARKETPLACE_SALE(
        userToggleable = true,
        enabledByDefault = true,
        description = "Продажа вашей карты на маркетплейсе",
    ),
    MARKETPLACE_WATCH(
        userToggleable = true,
        enabledByDefault = true,
        description = "Отслеживание карт на маркетплейсе",
    ),
    PAIR_BAN(
        userToggleable = false,
        enabledByDefault = true,
        description = "Уведомления о санкциях",
    ),
}
