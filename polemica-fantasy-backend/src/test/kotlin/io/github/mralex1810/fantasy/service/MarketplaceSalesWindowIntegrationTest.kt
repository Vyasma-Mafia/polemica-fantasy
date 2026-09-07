package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.entity.*
import io.github.mralex1810.fantasy.repository.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import java.time.temporal.ChronoUnit

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@Transactional
class MarketplaceSalesWindowIntegrationTest {
    @Autowired private lateinit var listings: MarketplaceListingRepository
    @Autowired private lateinit var players: FantasyPlayerRepository
    @Autowired private lateinit var templates: CardTemplateRepository
    @Autowired private lateinit var users: TelegramUserRepository
    @Autowired private lateinit var cards: UserCardRepository
    @Autowired private lateinit var service: MarketplaceService

    @Test
    fun `aggregate covers every sale and exact windows using sale snapshot and valid durations only`() {
        val to = Instant.parse("2026-09-07T00:00:00Z")
        val from = to.minus(7, ChronoUnit.DAYS)
        val player = players.save(FantasyPlayer(polemicaUserId = 98170001, nickname = "Window test"))
        val epic = templates.save(CardTemplate(fantasyPlayer = player, rarity = Rarity.EPIC))
        val legendary = templates.save(CardTemplate(fantasyPlayer = player, rarity = Rarity.LEGENDARY))
        val owner = users.save(TelegramUser(telegramId = 98170001))
        val card = cards.save(UserCard(telegramUser = owner, cardTemplate = legendary))
        fun sale(price: Long, soldAt: Instant, duration: Long = 100, snapshot: CardTemplate? = epic,
                 status: MarketplaceListingStatus = MarketplaceListingStatus.SOLD) {
            listings.save(MarketplaceListing(seller = owner, buyer = owner, userCard = card,
                price = price, status = status, createdAt = soldAt.minusSeconds(duration),
                soldAt = soldAt, soldCardTemplate = snapshot))
        }
        // 12 eligible sales, even median 6.5; lower boundary is included.
        (1L..12L).forEach { sale(it, if (it == 1L) from else from.plusSeconds(it), if (it == 12L) -1 else 100) }
        sale(900, to) // upper boundary excluded
        sale(800, from.minusSeconds(1)) // 30-day only
        sale(700, to.minus(31, ChronoUnit.DAYS))
        sale(600, from.plusSeconds(100), status = MarketplaceListingStatus.CANCELLED)
        sale(500, from.plusSeconds(200), snapshot = null) // legacy uses current LEGENDARY
        listings.flush()
        val result = listings.aggregateSalesWindow(player.id!!, "EPIC", from, to)
        assertEquals(12L, result.completedSalesCount)
        assertEquals(1L, result.minSalePrice)
        assertEquals(12L, result.maxSalePrice)
        assertEquals(6.5, result.medianSalePrice)
        assertEquals(100.0, result.medianTimeToSaleSeconds)
        assertEquals(11L, result.timeToSaleSampleSize)
        assertEquals(13L, listings.aggregateSalesWindow(player.id!!, "EPIC", to.minus(30, ChronoUnit.DAYS), to).completedSalesCount)
        assertEquals(1L, listings.aggregateSalesWindow(player.id!!, "LEGENDARY", from, to).completedSalesCount)
        val empty = listings.aggregateSalesWindow(player.id!!, "COMMON", from, to)
        assertEquals(0L, empty.completedSalesCount)
        assertEquals(0L, empty.timeToSaleSampleSize)
        assertNull(empty.minSalePrice)
        assertNull(empty.maxSalePrice)
        assertNull(empty.medianSalePrice)
        assertNull(empty.medianTimeToSaleSeconds)
    }

    @Test
    fun `detail always includes two empty windows sharing one cutoff`() {
        val detail = service.getAnalyticsDetail(Long.MAX_VALUE, Rarity.COMMON)
        assertEquals(listOf(7, 30), detail.salesWindows.map { it.windowDays })
        detail.salesWindows.forEach {
            assertEquals(detail.asOf, it.to)
            assertEquals(detail.asOf.minus(it.windowDays.toLong(), ChronoUnit.DAYS), it.from)
            assertEquals(0L, it.completedSalesCount)
            assertNull(it.medianSalePrice)
        }
        assertTrue(detail.recentSales.isEmpty())
        assertNull(detail.avgSalePrice)
    }

    companion object {
        @Container @ServiceConnection @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine")
    }
}
