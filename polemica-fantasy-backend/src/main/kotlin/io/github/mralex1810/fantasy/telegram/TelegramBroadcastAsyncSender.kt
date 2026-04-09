package io.github.mralex1810.fantasy.telegram

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

private const val DELAY_MS_BETWEEN_SENDS = 50L

@Component
class TelegramBroadcastAsyncSender(
    private val telegramBotApiClient: TelegramBotApiClient,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Async
    fun sendToAllChats(botToken: String, chatIds: List<Long>, text: String) {
        for (chatId in chatIds) {
            try {
                telegramBotApiClient.sendMessage(
                    botToken,
                    chatId,
                    text,
                    parseMode = TelegramBotApiClient.PARSE_MODE_MARKDOWN_V2,
                )
            } catch (e: Exception) {
                log.warn("Failed to send broadcast Telegram message to chatId={}", chatId, e)
            }
            try {
                Thread.sleep(DELAY_MS_BETWEEN_SENDS)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                return
            }
        }
    }
}
