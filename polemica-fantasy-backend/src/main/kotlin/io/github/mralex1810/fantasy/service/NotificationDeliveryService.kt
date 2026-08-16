package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.config.TelegramProperties
import io.github.mralex1810.fantasy.entity.NotificationCategory
import io.github.mralex1810.fantasy.observability.FantasyMetrics
import io.github.mralex1810.fantasy.observability.FantasyMetrics.NotificationOutcome
import io.github.mralex1810.fantasy.repository.NotificationPreferenceRepository
import io.github.mralex1810.fantasy.repository.TelegramUserRepository
import io.github.mralex1810.fantasy.telegram.InlineKeyboardMarkup
import io.github.mralex1810.fantasy.telegram.TelegramBotApiClient
import io.github.mralex1810.fantasy.telegram.TelegramSendResult
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

private const val BATCH_DELAY_MS = 50L

@Service
class NotificationDeliveryService(
    private val telegramBotApiClient: TelegramBotApiClient,
    private val telegramProperties: TelegramProperties,
    private val telegramUserRepository: TelegramUserRepository,
    private val notificationPreferenceRepository: NotificationPreferenceRepository,
    private val fantasyMetrics: FantasyMetrics,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun deliver(
        telegramChatId: Long,
        category: NotificationCategory,
        text: String,
        parseMode: String? = null,
        replyMarkup: InlineKeyboardMarkup? = null,
    ): Boolean {
        val report = deliverToMany(
            recipients = listOf(telegramChatId),
            category = category,
            textProvider = { text },
            parseMode = parseMode,
            replyMarkup = replyMarkup,
        )
        return report.sent > 0
    }

    fun deliverToMany(
        recipients: List<Long>,
        category: NotificationCategory,
        textProvider: (telegramChatId: Long) -> String,
        parseMode: String? = null,
        replyMarkup: InlineKeyboardMarkup? = null,
    ): DeliveryReport {
        val token = telegramProperties.token.trim()
        if (!telegramProperties.notifications.enabled || token.isBlank()) {
            log.debug(
                "Notification delivery skipped globally (enabled={}, token blank={})",
                telegramProperties.notifications.enabled,
                token.isBlank(),
            )
            fantasyMetrics.recordNotificationDeliveries(
                category = category,
                outcome = NotificationOutcome.GLOBALLY_DISABLED,
                count = recipients.size,
            )
            return DeliveryReport()
        }

        var sent = 0
        var skippedBlocked = 0
        var skippedPreference = 0
        var failed = 0
        for ((index, chatId) in recipients.withIndex()) {
            if (index > 0) {
                sleepQuietly(BATCH_DELAY_MS)
            }
            try {
                val user = telegramUserRepository.findByTelegramId(chatId)
                if (user == null) {
                    failed++
                    fantasyMetrics.recordNotificationDeliveries(category, NotificationOutcome.ERROR)
                    continue
                }
                val userId = user.id ?: run {
                    failed++
                    fantasyMetrics.recordNotificationDeliveries(category, NotificationOutcome.ERROR)
                    continue
                }
                if (user.botBlocked) {
                    skippedBlocked++
                    fantasyMetrics.recordNotificationDeliveries(category, NotificationOutcome.BOT_BLOCKED)
                    continue
                }
                if (!isCategoryEnabledForUser(userId, category)) {
                    skippedPreference++
                    fantasyMetrics.recordNotificationDeliveries(category, NotificationOutcome.PREFERENCE_DISABLED)
                    continue
                }

                val text = textProvider(chatId)
                val sendResult = telegramBotApiClient.sendMessageSafe(
                    botToken = token,
                    chatId = chatId,
                    text = text,
                    parseMode = parseMode,
                    replyMarkup = replyMarkup,
                )
                when (sendResult) {
                    is TelegramSendResult.Success -> {
                        sent++
                        fantasyMetrics.recordNotificationDeliveries(category, NotificationOutcome.SENT)
                    }
                    is TelegramSendResult.BotBlocked -> {
                        if (!user.botBlocked) {
                            user.botBlocked = true
                            telegramUserRepository.save(user)
                        }
                        skippedBlocked++
                        fantasyMetrics.recordNotificationDeliveries(category, NotificationOutcome.BOT_BLOCKED)
                        log.info(
                            "Marked notification recipient as unreachable category={} (stored as bot_blocked, desc={})",
                            category,
                            sendResult.description,
                        )
                    }
                    is TelegramSendResult.RateLimited -> {
                        sleepQuietly(sendResult.retryAfterSeconds * 1000L)
                        val retryResult = telegramBotApiClient.sendMessageSafe(
                            botToken = token,
                            chatId = chatId,
                            text = text,
                            parseMode = parseMode,
                            replyMarkup = replyMarkup,
                        )
                        when (retryResult) {
                            is TelegramSendResult.Success -> {
                                sent++
                                fantasyMetrics.recordNotificationDeliveries(category, NotificationOutcome.SENT)
                            }
                            is TelegramSendResult.BotBlocked -> {
                                if (!user.botBlocked) {
                                    user.botBlocked = true
                                    telegramUserRepository.save(user)
                                }
                                skippedBlocked++
                                fantasyMetrics.recordNotificationDeliveries(category, NotificationOutcome.BOT_BLOCKED)
                            }
                            else -> {
                                failed++
                                fantasyMetrics.recordNotificationDeliveries(category, NotificationOutcome.ERROR)
                            }
                        }
                        if (retryResult !is TelegramSendResult.Success) {
                            log.warn(
                                "Notification retry failed for category={} resultType={}",
                                category,
                                retryResult.javaClass.simpleName,
                            )
                        }
                    }
                    is TelegramSendResult.OtherError -> {
                        failed++
                        fantasyMetrics.recordNotificationDeliveries(category, NotificationOutcome.ERROR)
                        log.warn(
                            "Telegram error category={} code={} description={}",
                            category,
                            sendResult.code,
                            sendResult.description,
                        )
                    }
                }
            } catch (e: Exception) {
                failed++
                fantasyMetrics.recordNotificationDeliveries(category, NotificationOutcome.ERROR)
                log.warn("Notification preparation or delivery failed for category={}", category, e)
            }
        }
        return DeliveryReport(
            sent = sent,
            skippedBlocked = skippedBlocked,
            skippedPreference = skippedPreference,
            failed = failed,
        )
    }

    fun broadcast(text: String, parseMode: String? = null): DeliveryReport {
        val recipients = telegramUserRepository.findAllTelegramIds()
        return deliverToMany(
            recipients = recipients,
            category = NotificationCategory.ADMIN_BROADCAST,
            textProvider = { text },
            parseMode = parseMode,
        )
    }

    private fun isCategoryEnabledForUser(telegramUserId: Long, category: NotificationCategory): Boolean {
        if (!category.userToggleable) return true
        return notificationPreferenceRepository
            .findByTelegramUser_IdAndCategory(telegramUserId, category)
            ?.enabled
            ?: category.enabledByDefault
    }

    private fun sleepQuietly(millis: Long) {
        try {
            Thread.sleep(millis)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }
}

data class DeliveryReport(
    val sent: Int = 0,
    val skippedBlocked: Int = 0,
    val skippedPreference: Int = 0,
    val failed: Int = 0,
)
