package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.entity.CardTemplate
import io.github.mralex1810.fantasy.entity.FantasyPlayer
import io.github.mralex1810.fantasy.entity.Rarity
import io.github.mralex1810.fantasy.entity.TelegramUser
import io.github.mralex1810.fantasy.entity.FantikiTransactionReason
import io.github.mralex1810.fantasy.entity.UserCard
import io.github.mralex1810.fantasy.repository.CardTemplateRepository
import io.github.mralex1810.fantasy.repository.FantasyPlayerRepository
import io.github.mralex1810.fantasy.repository.TelegramUserRepository
import io.github.mralex1810.fantasy.repository.UserCardRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.cache.CacheManager
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class GlobalRatingServiceIntegrationTest {

    @Autowired
    private lateinit var globalRatingService: GlobalRatingService

    @Autowired
    private lateinit var telegramUserRepository: TelegramUserRepository

    @Autowired
    private lateinit var fantasyPlayerRepository: FantasyPlayerRepository

    @Autowired
    private lateinit var cardTemplateRepository: CardTemplateRepository

    @Autowired
    private lateinit var userCardRepository: UserCardRepository

    @Autowired
    private lateinit var userService: UserService

    @Autowired
    private lateinit var cacheManager: CacheManager

    @SpyBean
    private lateinit var spiedUserCardRepository: UserCardRepository

    @BeforeEach
    fun clearCacheAndSpies() {
        cacheManager.getCache("globalRating")?.clear()
        clearInvocations(spiedUserCardRepository)
    }

    @Test
    @Transactional
    fun `ordering by totalValue and currentUser rank`() {
        val rich = telegramUserRepository.save(
            TelegramUser(telegramId = 881_001L, fantiki = 1_000L),
        )
        val poor = telegramUserRepository.save(
            TelegramUser(telegramId = 881_002L, fantiki = 100L),
        )
        val fp = fantasyPlayerRepository.save(
            FantasyPlayer(polemicaUserId = 881_101L, nickname = "rating-t"),
        )
        val commonTemplate = cardTemplateRepository.save(
            CardTemplate(fantasyPlayer = fp, rarity = Rarity.COMMON),
        )
        // poor gets a card: still below rich with default balance (1000) + 0 cards = 1000; poor = 100 + 25
        userCardRepository.save(
            UserCard(telegramUser = poor, cardTemplate = commonTemplate, usesRemaining = 0),
        )

        val out = globalRatingService.getRating(rich)
        val byTelegram = out.entries.associateBy { it.user.telegramId }
        val rankRich = byTelegram[rich.telegramId]!!
        val rankPoor = byTelegram[poor.telegramId]!!
        assertEquals(1, rankRich.rank)
        assertEquals(2, rankPoor.rank)
        assertEquals(1_000L, rankRich.totalValue) // 1000 fantiki, no cards
        assertEquals(100L + rankPoor.cardsValue, rankPoor.totalValue)
        assertEquals(0, byTelegram[rich.telegramId]!!.cardsCount)
        assertEquals(1, byTelegram[poor.telegramId]!!.cardsCount)
        assertEquals(0L, rankRich.prizeWinnings)
        assertEquals(0L, rankPoor.prizeWinnings)

        assertNotNull(out.currentUser)
        assertEquals(1, out.currentUser!!.rank)
        assertEquals(rich.telegramId, out.currentUser.user.telegramId)
    }

    @Test
    @Transactional
    fun `prizeWinnings is sum of SERIES_REWARD only`() {
        val low = telegramUserRepository.save(
            TelegramUser(telegramId = 883_001L, fantiki = 5_00L),
        )
        val high = telegramUserRepository.save(
            TelegramUser(telegramId = 883_002L, fantiki = 5_00L),
        )
        userService.addBalance(low.id!!, 100L, FantikiTransactionReason.SERIES_REWARD)
        userService.addBalance(low.id!!, 50L, FantikiTransactionReason.SERIES_REWARD)
        userService.addBalance(low.id!!, 9_99L, FantikiTransactionReason.ADMIN_GRANT)
        userService.addBalance(high.id!!, 1L, FantikiTransactionReason.SERIES_REWARD)
        userService.addBalance(high.id!!, 400L, FantikiTransactionReason.SERIES_REWARD)

        val out = globalRatingService.getRating(high)
        val byId = out.entries.associateBy { it.user.telegramId }
        assertEquals(150L, byId[883_001L]!!.prizeWinnings)
        assertEquals(401L, byId[883_002L]!!.prizeWinnings)
        // totalValue = balance + cards only (prizeWinnings is a subset of balance history, not double-counted in total)
        val entryHigh = byId[883_002L]!!
        assertEquals(901L, entryHigh.fantikiBalance) // 500 + 401 from SERIES_REWARD
        assertEquals(901L, entryHigh.totalValue)
    }

    @Test
    @Transactional
    fun `findAllForGlobalRating invoked once for two rating requests due to cache`() {
        val u = telegramUserRepository.save(TelegramUser(telegramId = 882_001L, fantiki = 500L))
        globalRatingService.getRating(u)
        globalRatingService.getRating(u)
        verify(spiedUserCardRepository, times(1)).findAllForGlobalRating()
    }

    companion object {
        @JvmField
        @Container
        @ServiceConnection
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")
    }
}
