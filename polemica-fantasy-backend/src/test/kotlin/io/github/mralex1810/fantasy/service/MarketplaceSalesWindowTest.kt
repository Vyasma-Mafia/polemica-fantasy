package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.entity.MarketplaceListing
import io.github.mralex1810.fantasy.entity.Rarity
import io.github.mralex1810.fantasy.repository.MarketplaceListingRepository
import io.github.mralex1810.fantasy.repository.MarketplaceSalesWindowStats
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.time.Instant
import java.time.temporal.ChronoUnit

class MarketplaceSalesWindowTest {
    private val repository = mock<MarketplaceListingRepository>()
    private val service = MarketplaceService(repository, mock(), mock(), mock(), mock(), mock(), mock(),
        mock(), mock(), mock(), mock(), mock(), mock(), mock())

    @Test
    fun `mapping uses one cutoff exact windows and preserves recent-sale average`() {
        whenever(repository.findActiveListingStatsForPlayerAndRarity(any(), any(), any()))
            .thenReturn(arrayOf(2L, 20L, 40L))
        whenever(repository.findRecentSoldByPlayerAndRarity(any(), any(), any(), any()))
            .thenReturn(listOf(MarketplaceListing(price = 9, soldAt = Instant.EPOCH),
                MarketplaceListing(price = 20, soldAt = Instant.EPOCH)))
        val stats = object : MarketplaceSalesWindowStats {
            override val completedSalesCount = 12L
            override val minSalePrice = 1L
            override val maxSalePrice = 12L
            override val medianSalePrice = 6.5
            override val medianTimeToSaleSeconds = 100.0
            override val timeToSaleSampleSize = 11L
        }
        whenever(repository.aggregateSalesWindow(any(), any(), any(), any())).thenReturn(stats)
        val detail = service.getAnalyticsDetail(33, Rarity.EPIC)
        assertEquals(14L, detail.avgSalePrice)
        assertEquals(2, detail.recentSales.size)
        assertEquals(2L, detail.activeCount)
        assertEquals(listOf(7, 30), detail.salesWindows.map { it.windowDays })
        detail.salesWindows.forEach {
            assertEquals(detail.asOf, it.to)
            assertEquals(detail.asOf.minus(it.windowDays.toLong(), ChronoUnit.DAYS), it.from)
            assertEquals(12L, it.completedSalesCount)
            assertEquals(6.5, it.medianSalePrice)
            assertEquals(100.0, it.medianTimeToSaleSeconds)
            assertEquals(11L, it.timeToSaleSampleSize)
            verify(repository).aggregateSalesWindow(33, "EPIC", it.from, it.to)
        }
    }
}
