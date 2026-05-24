package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.config.AppProperties
import io.github.mralex1810.fantasy.entity.ProductAudience
import io.github.mralex1810.fantasy.entity.TelegramUser
import io.github.mralex1810.fantasy.repository.FantasyTeamRepository
import io.github.mralex1810.fantasy.repository.TelegramUserRepository
import io.github.mralex1810.fantasy.repository.UserCardRepository
import io.github.mralex1810.fantasy.telegram.NotificationButtonFactory
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever
import java.time.Instant

class TelegramStartMenuServiceTest {
    private lateinit var telegramUserRepository: TelegramUserRepository
    private lateinit var userSegmentService: UserSegmentService
    private lateinit var onboardingService: OnboardingService
    private lateinit var fantasyTeamRepository: FantasyTeamRepository
    private lateinit var userCardRepository: UserCardRepository
    private lateinit var service: TelegramStartMenuService

    @BeforeEach
    fun setup() {
        telegramUserRepository = mock(TelegramUserRepository::class.java)
        userSegmentService = mock(UserSegmentService::class.java)
        onboardingService = mock(OnboardingService::class.java)
        fantasyTeamRepository = mock(FantasyTeamRepository::class.java)
        userCardRepository = mock(UserCardRepository::class.java)
        service = TelegramStartMenuService(
            telegramUserRepository,
            userSegmentService,
            onboardingService,
            fantasyTeamRepository,
            userCardRepository,
            NotificationButtonFactory(AppProperties(webappBaseUrl = "https://fantasy.example")),
        )
    }

    @Test
    fun `unknown user gets simple open app CTA`() {
        whenever(telegramUserRepository.findByTelegramId(100L)).thenReturn(null)

        val menu = service.build(100L)

        assertTrue(menu.text.contains("Откройте мини-приложение"))
        assertTrue(menu.replyMarkup.inlineKeyboard.flatten().any { it.text == "Открыть игру" })
    }

    @Test
    fun `registered user with cards and open deadline gets team text`() {
        val user = TelegramUser(id = 7L, telegramId = 100L)
        whenever(telegramUserRepository.findByTelegramId(100L)).thenReturn(user)
        whenever(onboardingService.firstOpenTeamTarget(7L)).thenReturn(
            OpenTeamTarget("/series/5/team", Instant.parse("2026-05-24T18:30:00Z")),
        )
        whenever(userCardRepository.countByTelegramUser_IdAndDeletedAtIsNull(7L)).thenReturn(2L)
        whenever(fantasyTeamRepository.countByTelegramUser_Id(7L)).thenReturn(0L)
        whenever(userSegmentService.audienceForUser(7L)).thenReturn(ProductAudience.ACTION_NO_TEAM)

        val menu = service.build(100L)

        assertTrue(menu.text.contains("Есть открытая серия"))
        assertTrue(menu.text.contains("Подайте команду до"))
        assertTrue(menu.replyMarkup.inlineKeyboard.flatten().any { it.text == "Собрать команду" })
    }
}
