package io.github.mralex1810.fantasy.telegram

sealed class TelegramSendResult {
    data object Success : TelegramSendResult()

    data class BotBlocked(val description: String) : TelegramSendResult()

    data class RateLimited(val retryAfterSeconds: Int) : TelegramSendResult()

    data class OtherError(val code: Int, val description: String) : TelegramSendResult()
}
