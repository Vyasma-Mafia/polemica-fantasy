package io.github.mralex1810.fantasy.event

import io.github.mralex1810.fantasy.observability.FantasyMetrics
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class SeriesFinalizationMetricsListener(
    private val fantasyMetrics: FantasyMetrics,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onSeriesFinalized(event: SeriesFinalizedNotificationEvent) {
        fantasyMetrics.recordSeriesFinalization(
            rewardedUsers = event.rewardedUsers,
            cardUsesDecremented = event.cardUsesDecremented,
        )
    }
}
