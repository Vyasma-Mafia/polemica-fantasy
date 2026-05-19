package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.dto.admin.request.BanPairRequest
import io.github.mralex1810.fantasy.dto.admin.request.MarkPairClearedRequest
import io.github.mralex1810.fantasy.entity.CardTemplate
import io.github.mralex1810.fantasy.entity.FantasyPlayer
import io.github.mralex1810.fantasy.entity.MarketplaceListing
import io.github.mralex1810.fantasy.entity.MarketplaceListingStatus
import io.github.mralex1810.fantasy.entity.Rarity
import io.github.mralex1810.fantasy.entity.TelegramUser
import io.github.mralex1810.fantasy.entity.UserCard
import io.github.mralex1810.fantasy.repository.CardTemplateRepository
import io.github.mralex1810.fantasy.repository.FantasyPlayerRepository
import io.github.mralex1810.fantasy.repository.MarketplaceListingRepository
import io.github.mralex1810.fantasy.repository.MarketplaceListingSanctionRepository
import io.github.mralex1810.fantasy.repository.MarketplacePairSanctionHistoryRepository
import io.github.mralex1810.fantasy.repository.TelegramUserRepository
import io.github.mralex1810.fantasy.repository.UserCardRepository
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
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

/**
 * Логика изъятия фантиков при бане пары: [MarketplaceAdminService.sanctionsForOneUserInPair] +
 * [io.github.mralex1810.fantasy.repository.MarketplaceListingRepository.sumSellerReceivedForSalesTo],
 * согласованные с нетто продавца в [MarketplaceService.buyCard]
 * (price - (price * pct) / 100, целочисленное деление).
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class MarketplacePairBanFantikiIntegrationTest {

    @Autowired
    private lateinit var marketplaceAdminService: MarketplaceAdminService

    @Autowired
    private lateinit var economyConfigService: EconomyConfigService

    @Autowired
    private lateinit var marketplaceListingRepository: MarketplaceListingRepository

    @Autowired
    private lateinit var telegramUserRepository: TelegramUserRepository

    @Autowired
    private lateinit var fantasyPlayerRepository: FantasyPlayerRepository

    @Autowired
    private lateinit var cardTemplateRepository: CardTemplateRepository

    @Autowired
    private lateinit var userCardRepository: UserCardRepository

    @Autowired
    private lateinit var marketplacePairSanctionHistoryRepository: MarketplacePairSanctionHistoryRepository

    @Autowired
    private lateinit var marketplaceListingSanctionRepository: MarketplaceListingSanctionRepository

    @Test
    @Transactional
    fun `ban pair confiscates full integer seller net for one SOLD to partner`() {
        val pct = economyConfigService.getMarketplaceCommissionPercent()
        val tgA = 881_000_001L
        val tgB = 881_000_002L
        val a = newUser(tgA)
        val b = newUser(tgB)
        val price = 10_000L
        soldListingFromAToB(a, b, price, tplSuffix = 1L)

        val r = ban(tgA, tgB)
        val expected = sellerNetAfterCommission(price, pct)
        assertEquals(expected, r.userA.fantikiConfiscated)
        assertEquals(0L, r.userB.fantikiConfiscated)
        assertEquals(9_000_000L - expected, r.userA.newBalance)
    }

    @Test
    @Transactional
    fun `ban pair sums all SOLD in one direction`() {
        val pct = economyConfigService.getMarketplaceCommissionPercent()
        val tgA = 881_000_101L
        val tgB = 881_000_102L
        val a = newUser(tgA)
        val b = newUser(tgB)
        val p1 = 1_000L
        val p2 = 2_500L
        soldListingFromAToB(a, b, p1, 201L)
        soldListingFromAToB(a, b, p2, 202L)

        val r = ban(tgA, tgB)
        val expected = sellerNetAfterCommission(p1, pct) + sellerNetAfterCommission(p2, pct)
        assertEquals(expected, r.userA.fantikiConfiscated)
        assertEquals(0L, r.userB.fantikiConfiscated)
    }

    @Test
    @Transactional
    fun `ban pair confiscates per direction when both users sold to each other`() {
        val pct = economyConfigService.getMarketplaceCommissionPercent()
        val tgA = 881_000_201L
        val tgB = 881_000_202L
        val a = newUser(tgA)
        val b = newUser(tgB)
        val aToB = 3_000L
        val bToA = 5_000L
        soldListingFromAToB(a, b, aToB, 301L)
        soldListingFromBToA(a, b, bToA, 302L)

        val r = ban(tgA, tgB)
        assertEquals(sellerNetAfterCommission(aToB, pct), r.userA.fantikiConfiscated)
        assertEquals(sellerNetAfterCommission(bToA, pct), r.userB.fantikiConfiscated)
    }

    @Test
    @Transactional
    fun `sumSellerReceivedForSalesTo matches per-row net same as buyCard`() {
        val pct = economyConfigService.getMarketplaceCommissionPercent()
        val tgA = 881_000_301L
        val tgB = 881_000_302L
        val a = newUser(tgA)
        val b = newUser(tgB)
        val p = 101L
        soldListingFromAToB(a, b, p, 401L)
        val fromRepo = marketplaceListingRepository.sumSellerReceivedForSalesTo(
            MarketplaceListingStatus.SOLD,
            a.id!!,
            b.id!!,
            pct,
        )
        assertEquals(sellerNetAfterCommission(p, pct), fromRepo)
    }

    @Test
    @Transactional
    fun `getPairTrades per-direction sellerReceived matches repository sum and ban confiscation`() {
        val pct = economyConfigService.getMarketplaceCommissionPercent()
        val tgA = 881_000_501L
        val tgB = 881_000_502L
        val a = newUser(tgA)
        val b = newUser(tgB)
        soldListingFromAToB(a, b, 1_200L, 601L)
        soldListingFromAToB(a, b, 800L, 602L)
        soldListingFromBToA(a, b, 2_000L, 603L)

        val sumAtoB = marketplaceListingRepository.sumSellerReceivedForSalesTo(
            MarketplaceListingStatus.SOLD,
            a.id!!,
            b.id!!,
            pct,
        )
        val sumBtoA = marketplaceListingRepository.sumSellerReceivedForSalesTo(
            MarketplaceListingStatus.SOLD,
            b.id!!,
            a.id!!,
            pct,
        )

        val pairTrades = marketplaceAdminService.getPairTrades(tgA, tgB)
        val fromDtosAtoB = pairTrades.trades
            .filter { it.sellerTelegramId == tgA && it.buyerTelegramId == tgB }
            .sumOf { it.sellerReceived }
        val fromDtosBtoA = pairTrades.trades
            .filter { it.sellerTelegramId == tgB && it.buyerTelegramId == tgA }
            .sumOf { it.sellerReceived }

        assertEquals(sumAtoB, fromDtosAtoB)
        assertEquals(sumBtoA, fromDtosBtoA)

        val banR = ban(tgA, tgB)
        assertEquals(sumAtoB, banR.userA.fantikiConfiscated)
        assertEquals(sumBtoA, banR.userB.fantikiConfiscated)
    }

    @Test
    @Transactional
    fun `pair clearance mark and unmark appear in getPairAnalysis`() {
        val tgA = 881_000_601L
        val tgB = 881_000_602L
        val a = newUser(tgA)
        val b = newUser(tgB)
        soldListingFromAToB(a, b, 100L, 701L)

        fun findRow() = marketplaceAdminService.getPairAnalysis()
            .find { row ->
                (row.userATelegramId == tgA && row.userBTelegramId == tgB) ||
                    (row.userATelegramId == tgB && row.userBTelegramId == tgA)
            }!!

        assertEquals(false, findRow().cleared)

        marketplaceAdminService.markPairCleared(
            MarkPairClearedRequest(telegramIdA = tgA, telegramIdB = tgB, note = "ok"),
        )
        val afterMark = findRow()
        assertTrue(afterMark.cleared)
        assertEquals("ok", afterMark.clearedNote)

        marketplaceAdminService.unmarkPairCleared(tgA, tgB)
        assertFalse(findRow().cleared)
    }

    @Test
    @Transactional
    fun `SOLD to partner still counts for seller net if card was later transfered to third user`() {
        val pct = economyConfigService.getMarketplaceCommissionPercent()
        val tgA = 881_000_401L
        val tgB = 881_000_402L
        val tgC = 881_000_403L
        val a = newUser(tgA)
        val b = newUser(tgB)
        val c = newUser(tgC)
        val price = 7_777L
        val (_, uc) = soldListingFromAToBWithCard(a, b, price, 501L)
        val listingId = marketplaceListingRepository.findAll()
            .filter { it.userCard?.id == uc.id }[0].id!!
        uc.telegramUser = c
        userCardRepository.save(uc)

        val r = ban(tgA, tgB)
        assertEquals(sellerNetAfterCommission(price, pct), r.userA.fantikiConfiscated)

        assertTrue(marketplaceListingSanctionRepository.existsByListing_Id(listingId))
        val secondResult = ban(tgA, tgB)
        assertEquals(0L, secondResult.userA.fantikiConfiscated)
    }

    @Test
    @Transactional
    fun `ban pair keeps cards and listings, second ban has no effect`() {
        val pct = economyConfigService.getMarketplaceCommissionPercent()
        val tgA = 881_000_701L
        val tgB = 881_000_702L
        val a = newUser(tgA)
        val b = newUser(tgB)
        val price = 5_000L
        val (_, uc) = soldListingFromAToBWithCard(a, b, price, 701L)
        val listingId = marketplaceListingRepository.findAll()
            .filter { it.userCard?.id == uc.id }[0].id!!

        val firstResult = ban(tgA, tgB)
        val expected = sellerNetAfterCommission(price, pct)
        assertEquals(expected, firstResult.userA.fantikiConfiscated)
        assertEquals(1, firstResult.userB.cardsConfiscated.size)
        assertEquals("ban-test-701", firstResult.userB.cardsConfiscated[0].playerName)

        val reloadedCard = userCardRepository.findById(uc.id!!).orElseThrow()
        assertNull(reloadedCard.deletedAt, "Card must stay available")

        val reloadedListing = marketplaceListingRepository.findById(listingId).orElseThrow()
        assertEquals(MarketplaceListingStatus.SOLD, reloadedListing.status, "Listing must stay SOLD")
        assertTrue(marketplaceListingSanctionRepository.existsByListing_Id(listingId), "Listing must be marked sanctioned")

        val sanctionHistory = marketplacePairSanctionHistoryRepository.findAll()
        assertEquals(1, sanctionHistory.size, "Pair sanction history must be created")

        val secondResult = ban(tgA, tgB)
        assertAll(
            { assertEquals(0L, secondResult.userA.fantikiConfiscated, "Second ban: no fantiki") },
            { assertEquals(0, secondResult.userB.cardsConfiscated.size, "Second ban: no cards") },
        )
    }

    @Test
    @Transactional
    fun `second ban confiscates only new unsanctioned transactions`() {
        val pct = economyConfigService.getMarketplaceCommissionPercent()
        val tgA = 881_000_801L
        val tgB = 881_000_802L
        val a = newUser(tgA)
        val b = newUser(tgB)
        val firstPrice = 4_000L
        val secondPrice = 6_000L
        soldListingFromAToBWithCard(a, b, firstPrice, 801L)

        val firstResult = ban(tgA, tgB)
        assertEquals(sellerNetAfterCommission(firstPrice, pct), firstResult.userA.fantikiConfiscated)

        soldListingFromAToBWithCard(a, b, secondPrice, 802L)

        val secondResult = ban(tgA, tgB)
        assertEquals(sellerNetAfterCommission(secondPrice, pct), secondResult.userA.fantikiConfiscated)
        assertEquals(1, secondResult.userB.cardsConfiscated.size)
    }

    private fun ban(telegramA: Long, telegramB: Long) =
        marketplaceAdminService.banPair(
            BanPairRequest(telegramIdA = telegramA, telegramIdB = telegramB, reason = "test pair ban"),
        )

    private fun newUser(telegramId: Long): TelegramUser = telegramUserRepository.save(
        TelegramUser(telegramId = telegramId, fantiki = 9_000_000L),
    )

    private fun soldListingFromAToB(seller: TelegramUser, buyer: TelegramUser, price: Long, tplSuffix: Long) {
        soldListingFromAToBWithCard(seller, buyer, price, tplSuffix)
    }

    private fun soldListingFromAToBWithCard(
        seller: TelegramUser,
        buyer: TelegramUser,
        price: Long,
        tplSuffix: Long,
    ): Pair<CardTemplate, UserCard> {
        val fp = fantasyPlayerRepository.save(
            FantasyPlayer(polemicaUserId = 5_000_000L + tplSuffix, nickname = "ban-test-$tplSuffix"),
        )
        val tpl = cardTemplateRepository.save(CardTemplate(fantasyPlayer = fp, rarity = Rarity.COMMON))
        var uc = userCardRepository.save(
            UserCard(
                telegramUser = buyer,
                cardTemplate = tpl,
                usesRemaining = 1,
            ),
        )
        marketplaceListingRepository.save(
            MarketplaceListing(
                seller = seller,
                userCard = uc,
                price = price,
                status = MarketplaceListingStatus.SOLD,
                createdAt = Instant.now().minusSeconds(60),
                soldAt = Instant.now(),
                buyer = buyer,
                soldCardTemplate = tpl,
            ),
        )
        return tpl to uc
    }

    private fun soldListingFromBToA(userA: TelegramUser, userB: TelegramUser, price: Long, tplSuffix: Long) {
        soldListingFromAToB(userB, userA, price, tplSuffix)
    }

    private companion object {
        fun sellerNetAfterCommission(price: Long, commissionPercent: Int): Long {
            val commission = price * commissionPercent / 100
            return price - commission
        }

        @JvmField
        @Container
        @ServiceConnection
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")
    }
}
