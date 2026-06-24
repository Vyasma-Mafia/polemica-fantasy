package io.github.mralex1810.fantasy.dto.admin.response

data class BroadcastAcceptedResponse(
    val recipientCount: Long,
)

data class DirectMessageResultResponse(
    val telegramUserId: Long,
    val sent: Boolean,
    val skippedBlocked: Boolean,
    val skippedPreference: Boolean,
    val failed: Boolean,
)
