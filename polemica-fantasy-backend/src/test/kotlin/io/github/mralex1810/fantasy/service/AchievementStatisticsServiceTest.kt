package io.github.mralex1810.fantasy.service

import com.github.mafia.vyasma.polemica.library.client.GamePointsService
import io.github.mralex1810.fantasy.entity.Achievement
import io.github.mralex1810.fantasy.entity.OccurrenceType
import io.github.mralex1810.fantasy.polemica.PolemicaIntegrationService
import io.github.mralex1810.fantasy.repository.AchievementRepository
import io.github.mralex1810.fantasy.repository.FantasyPlayerRepository
import io.github.mralex1810.fantasy.scoring.achievement.AchievementDetectorRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class AchievementStatisticsServiceTest {

    @Test
    fun `collectReport with no fantasy players still lists catalog achievements with zeros`() {
        val fantasyPlayerRepository = mock(FantasyPlayerRepository::class.java)
        val achievementRepository = mock(AchievementRepository::class.java)
        val achievementRegistry = mock(AchievementDetectorRegistry::class.java)
        val polemicaIntegrationService = mock(PolemicaIntegrationService::class.java)
        val gamePointsService = mock(GamePointsService::class.java)

        `when`(fantasyPlayerRepository.findAllPolemicaUserIds()).thenReturn(emptyList())
        val a = Achievement().apply {
            id = "sniper"
            name = "Sniper"
            occurrenceType = OccurrenceType.ONCE_PER_GAME
        }
        `when`(achievementRepository.findAllWithApplicableRoles()).thenReturn(listOf(a))

        val service = AchievementStatisticsService(
            fantasyPlayerRepository,
            achievementRepository,
            achievementRegistry,
            polemicaIntegrationService,
            gamePointsService,
        )

        val report = service.collectReport()
        assertEquals(0, report.fantasyPlayerCount)
        assertEquals(0, report.uniqueMatchIdsFromProfiles)
        assertEquals(0, report.uniqueGamesLoaded)
        assertEquals(1, report.byAchievement.size)
        assertEquals("sniper", report.byAchievement[0].achievementId)
        assertEquals(0L, report.byAchievement[0].applicableSlots)
    }
}
