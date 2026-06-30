package io.github.mralex1810.fantasy.service

import com.github.mafia.vyasma.polemica.library.client.GamePointsService
import io.github.mralex1810.fantasy.entity.Perk
import io.github.mralex1810.fantasy.entity.OccurrenceType
import io.github.mralex1810.fantasy.polemica.PolemicaIntegrationService
import io.github.mralex1810.fantasy.repository.PerkRepository
import io.github.mralex1810.fantasy.repository.FantasyPlayerAliasRepository
import io.github.mralex1810.fantasy.repository.FantasyPlayerRepository
import io.github.mralex1810.fantasy.scoring.perk.PerkDetectorRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class PerkStatisticsServiceTest {

    @Test
    fun `collectReport with no fantasy players still lists catalog perks with zeros`() {
        val fantasyPlayerRepository = mock(FantasyPlayerRepository::class.java)
        val fantasyPlayerAliasRepository = mock(FantasyPlayerAliasRepository::class.java)
        val perkRepository = mock(PerkRepository::class.java)
        val perkRegistry = mock(PerkDetectorRegistry::class.java)
        val polemicaIntegrationService = mock(PolemicaIntegrationService::class.java)
        val gamePointsService = mock(GamePointsService::class.java)

        `when`(fantasyPlayerRepository.findAll()).thenReturn(emptyList())
        val a = Perk().apply {
            id = "sniper"
            name = "Sniper"
            occurrenceType = OccurrenceType.ONCE_PER_GAME
        }
        `when`(perkRepository.findAllWithApplicableRoles()).thenReturn(listOf(a))

        val service = PerkStatisticsService(
            fantasyPlayerRepository,
            fantasyPlayerAliasRepository,
            perkRepository,
            perkRegistry,
            polemicaIntegrationService,
            gamePointsService,
        )

        val report = service.collectReport()
        assertEquals(0, report.fantasyPlayerCount)
        assertEquals(0, report.uniqueMatchIdsFromProfiles)
        assertEquals(0, report.uniqueGamesLoaded)
        assertEquals(1, report.byPerk.size)
        assertEquals("sniper", report.byPerk[0].perkId)
        assertEquals(0L, report.byPerk[0].applicableSlots)
        assertEquals(0L, report.totalPlayerSlots)
        assertEquals(0, report.anomalies.size)
    }
}
