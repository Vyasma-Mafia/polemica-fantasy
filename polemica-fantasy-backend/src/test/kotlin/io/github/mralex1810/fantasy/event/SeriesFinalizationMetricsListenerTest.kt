package io.github.mralex1810.fantasy.event

import io.github.mralex1810.fantasy.observability.FantasyMetrics
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.event.TransactionalEventListenerFactory
import org.springframework.transaction.support.AbstractPlatformTransactionManager
import org.springframework.transaction.support.DefaultTransactionStatus
import org.springframework.transaction.support.TransactionTemplate
import java.util.function.Supplier

class SeriesFinalizationMetricsListenerTest {
    @Test
    fun `metric advances exactly once after commit and not after rollback`() {
        val registry = SimpleMeterRegistry()
        val metrics = FantasyMetrics(registry)
        AnnotationConfigApplicationContext().use { context ->
            context.registerBean(
                "fantasyMetrics",
                FantasyMetrics::class.java,
                Supplier { metrics },
            )
            context.registerBean(
                "seriesFinalizationMetricsListener",
                SeriesFinalizationMetricsListener::class.java,
                Supplier { SeriesFinalizationMetricsListener(metrics) },
            )
            context.registerBean(
                "transactionalEventListenerFactory",
                TransactionalEventListenerFactory::class.java,
                Supplier { TransactionalEventListenerFactory() },
            )
            context.refresh()
            val transactionTemplate = TransactionTemplate(StubTransactionManager())

            transactionTemplate.executeWithoutResult {
                context.publishEvent(finalizedEvent())
            }
            assertEquals(1.0, registry.get("fantasy.series.finalizations").counter().count())

            transactionTemplate.executeWithoutResult { status ->
                context.publishEvent(finalizedEvent())
                status.setRollbackOnly()
            }
            assertEquals(1.0, registry.get("fantasy.series.finalizations").counter().count())
        }
    }

    private fun finalizedEvent() = SeriesFinalizedNotificationEvent(
        seriesId = 1L,
        tournamentName = "Tournament",
        seriesName = "Series",
        recipients = emptyList(),
        rewardedUsers = 2,
        cardUsesDecremented = 3,
    )

    private class StubTransactionManager : AbstractPlatformTransactionManager() {
        override fun doGetTransaction(): Any = Any()

        override fun doBegin(transaction: Any, definition: TransactionDefinition) = Unit

        override fun doCommit(status: DefaultTransactionStatus) = Unit

        override fun doRollback(status: DefaultTransactionStatus) = Unit
    }
}
