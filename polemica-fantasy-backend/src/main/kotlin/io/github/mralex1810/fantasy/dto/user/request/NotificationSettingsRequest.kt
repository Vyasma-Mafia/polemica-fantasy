package io.github.mralex1810.fantasy.dto.user.request

data class UpdateNotificationSettingsRequest(
    val categories: Map<String, Boolean>,
)
