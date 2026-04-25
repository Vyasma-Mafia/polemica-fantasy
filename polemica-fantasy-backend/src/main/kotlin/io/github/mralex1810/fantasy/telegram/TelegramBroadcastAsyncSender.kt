package io.github.mralex1810.fantasy.telegram

import io.github.mralex1810.fantasy.service.NotificationDeliveryService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

@Component
class TelegramBroadcastAsyncSender(
    private val notificationDeliveryService: NotificationDeliveryService,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Async
    fun sendToAllChats(text: String) {
        val report = notificationDeliveryService.broadcast(
            text = text,
            parseMode = TelegramBotApiClient.PARSE_MODE_MARKDOWN_V2,
        )
        log.info(
            "Broadcast delivery done: sent={}, skippedBlocked={}, skippedPreference={}, failed={}",
            report.sent,
            report.skippedBlocked,
            report.skippedPreference,
            report.failed,
        )
    }
}
