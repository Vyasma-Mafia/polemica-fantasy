package io.github.mralex1810.fantasy.scheduler

import io.github.mralex1810.fantasy.entity.MarketplaceListing
import io.github.mralex1810.fantasy.entity.NotificationCategory
import io.github.mralex1810.fantasy.repository.MarketplaceWatchPendingRepository
import io.github.mralex1810.fantasy.repository.TelegramUserRepository
import io.github.mralex1810.fantasy.service.NotificationDeliveryService
import io.github.mralex1810.fantasy.telegram.NotificationButtonFactory
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class MarketplaceWatchScheduler(
    private val marketplaceWatchPendingRepository: MarketplaceWatchPendingRepository,
    private val telegramUserRepository: TelegramUserRepository,
    private val notificationDeliveryService: NotificationDeliveryService,
    private val notificationButtonFactory: NotificationButtonFactory,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedRate = 300_000)
    @Transactional
    fun flushWatchNotifications() {
        val userIds = marketplaceWatchPendingRepository.findDistinctUserIds()
        for (userId in userIds) {
            try {
                val pending = marketplaceWatchPendingRepository.findAllByTelegramUser_Id(userId)
                if (pending.isEmpty()) {
                    continue
                }
                val user = telegramUserRepository.findById(userId).orElse(null)
                if (user == null) {
                    marketplaceWatchPendingRepository.deleteAllByTelegramUser_Id(userId)
                    continue
                }
                val listings = pending.mapNotNull { it.listing }
                    .distinctBy { it.id }
                    .sortedByDescending { it.createdAt }
                if (listings.isEmpty()) {
                    marketplaceWatchPendingRepository.deleteAllByTelegramUser_Id(userId)
                    continue
                }

                val text = buildWatchMessage(listings)
                val replyMarkup = if (listings.size == 1) {
                    val playerId = listings.first().userCard?.cardTemplate?.fantasyPlayer?.id
                    if (playerId != null) {
                        notificationButtonFactory.openMarketplaceFilteredButton(playerId)
                    } else {
                        notificationButtonFactory.openMarketplaceButton()
                    }
                } else {
                    notificationButtonFactory.openMarketplaceButton()
                }

                notificationDeliveryService.deliver(
                    telegramChatId = user.telegramId,
                    category = NotificationCategory.MARKETPLACE_WATCH,
                    text = text,
                    replyMarkup = replyMarkup,
                )
                marketplaceWatchPendingRepository.deleteAllByTelegramUser_Id(userId)
            } catch (e: Exception) {
                log.error("Failed to flush marketplace watch notifications", e)
            }
        }
    }

    private fun buildWatchMessage(listings: List<MarketplaceListing>): String {
        if (listings.size == 1) {
            val listing = listings.first()
            val template = listing.userCard?.cardTemplate
            return buildString {
                append("\uD83D\uDD14 На маркетплейсе появилась карта из вашего отслеживания:\n\n")
                append("Игрок: ${template?.fantasyPlayer?.nickname ?: "—"}\n")
                append("Редкость: ${template?.rarity?.name ?: "—"}\n")
                append("Цена: ${listing.price} ₣")
            }
        }
        val lines = listings.joinToString("\n") { listing ->
            val template = listing.userCard?.cardTemplate
            val player = template?.fantasyPlayer?.nickname ?: "—"
            val rarity = template?.rarity?.name ?: "—"
            "• $player ($rarity) — ${listing.price} ₣"
        }
        return "\uD83D\uDD14 На маркетплейсе появились карты из вашего отслеживания:\n\n$lines"
    }
}
