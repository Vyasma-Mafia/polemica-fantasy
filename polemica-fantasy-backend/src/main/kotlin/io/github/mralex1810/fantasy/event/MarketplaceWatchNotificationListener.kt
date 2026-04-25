package io.github.mralex1810.fantasy.event

import io.github.mralex1810.fantasy.entity.MarketplaceWatchPending
import io.github.mralex1810.fantasy.repository.MarketplaceListingRepository
import io.github.mralex1810.fantasy.repository.MarketplaceWatchFilterRepository
import io.github.mralex1810.fantasy.repository.MarketplaceWatchPendingRepository
import io.github.mralex1810.fantasy.repository.TelegramUserRepository
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Component
class MarketplaceWatchNotificationListener(
    private val marketplaceWatchFilterRepository: MarketplaceWatchFilterRepository,
    private val marketplaceWatchPendingRepository: MarketplaceWatchPendingRepository,
    private val telegramUserRepository: TelegramUserRepository,
    private val marketplaceListingRepository: MarketplaceListingRepository,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onListingCreated(event: MarketplaceListingCreatedEvent) {
        val tournamentIds = event.tournamentIds.ifEmpty { listOf(-1L) }
        val matchingUserIds = marketplaceWatchFilterRepository.findMatchingUserIds(
            sellerId = event.sellerId,
            fantasyPlayerId = event.fantasyPlayerId,
            rarity = event.rarity.name,
            price = event.price,
            tournamentIds = tournamentIds,
        )
        if (matchingUserIds.isEmpty()) {
            return
        }

        val listing = marketplaceListingRepository.findById(event.listingId).orElse(null) ?: return
        val usersById = telegramUserRepository.findAllById(matchingUserIds).associateBy { it.id }

        for (userId in matchingUserIds) {
            val user = usersById[userId] ?: continue
            marketplaceWatchPendingRepository.save(
                MarketplaceWatchPending(
                    telegramUser = user,
                    listing = listing,
                ),
            )
        }
    }
}
