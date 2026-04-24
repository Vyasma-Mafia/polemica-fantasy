package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.entity.TelegramUser
import io.github.mralex1810.fantasy.repository.TelegramUserRepository
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cache.CacheManager
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest(
    properties = [
        "app.rating.excluded-telegram-ids=884001,884002",
    ],
)
@Testcontainers
@ActiveProfiles("test")
class GlobalRatingExclusionIntegrationTest {

    @Autowired
    private lateinit var globalRatingService: GlobalRatingService

    @Autowired
    private lateinit var telegramUserRepository: TelegramUserRepository

    @Autowired
    private lateinit var cacheManager: CacheManager

    @Test
    @Transactional
    fun `excluded users are not on leaderboard and have no currentUser`() {
        val excluded = telegramUserRepository.save(
            TelegramUser(telegramId = 884_001L, fantiki = 1_000_000L),
        )
        val normal = telegramUserRepository.save(
            TelegramUser(telegramId = 884_003L, fantiki = 10L),
        )
        cacheManager.getCache("globalRating")?.clear()

        val forExcluded = globalRatingService.getRating(excluded)
        assertTrue(forExcluded.entries.none { it.user.telegramId == 884_001L })
        assertNull(forExcluded.currentUser)

        val forNormal = globalRatingService.getRating(normal)
        assertNotNull(forNormal.currentUser)
        assertTrue(forNormal.entries.any { it.user.telegramId == 884_003L })
    }

    companion object {
        @JvmField
        @Container
        @ServiceConnection
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")
    }
}
