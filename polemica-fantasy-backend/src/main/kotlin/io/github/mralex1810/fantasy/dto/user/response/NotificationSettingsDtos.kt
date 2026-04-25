package io.github.mralex1810.fantasy.dto.user.response

data class NotificationCategoryDto(
    val category: String,
    val enabled: Boolean,
    val toggleable: Boolean,
    val description: String,
)

data class NotificationSettingsResponse(
    val categories: List<NotificationCategoryDto>,
)
