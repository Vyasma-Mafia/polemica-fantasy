package io.github.mralex1810.fantasy.service

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.mralex1810.fantasy.config.TelegramLeagueImportProperties
import io.github.mralex1810.fantasy.entity.Series
import io.github.mralex1810.fantasy.entity.SeriesStatus
import io.github.mralex1810.fantasy.entity.Tournament
import io.github.mralex1810.fantasy.repository.LeagueImportRepository
import io.github.mralex1810.fantasy.repository.SeriesGameRepository
import io.github.mralex1810.fantasy.repository.SeriesRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.Mockito.`when`

@ExtendWith(MockitoExtension::class)
class SeriesCompletionServiceTest {
    @Mock private lateinit var seriesRepository: SeriesRepository
    @Mock private lateinit var seriesGameRepository: SeriesGameRepository
    @Mock private lateinit var leagueImportRepository: LeagueImportRepository
    @Mock private lateinit var objectMapper: ObjectMapper
    @Mock private lateinit var properties: TelegramLeagueImportProperties
    @Mock private lateinit var scoringContextFingerprintService: SeriesScoringContextFingerprintService
    @Mock private lateinit var economyFingerprintService: SeriesEconomyFingerprintService
    @Mock private lateinit var selectorFingerprintService: SeriesGameSelectorFingerprintService

    @InjectMocks private lateinit var service: SeriesCompletionService

    @Test
    fun `result identity matcher recognizes known cross-script aliases`() {
        assertEquals(
            SeriesResultIdentityMatcher.normalizeIdentity("fox"),
            SeriesResultIdentityMatcher.normalizeIdentity("фокс"),
        )
        assertEquals(
            SeriesResultIdentityMatcher.normalizeIdentity("olof"),
            SeriesResultIdentityMatcher.normalizeIdentity("ОloF"),
        )
        assertTrue(
            SeriesResultIdentityMatcher.matchesMafiaLine(
                "фокс, тише-тише, cristo",
                listOf("Тише-Тише", "Cristo", "fox"),
            ),
        )
    }

    @Test
    fun `admin readiness bypasses scoring status while Telegram readiness remains strict`() {
        listOf(SeriesStatus.UPCOMING, SeriesStatus.ACTIVE).forEach { status ->
            val series = Series(
                id = 7L,
                tournament = Tournament(id = 3L),
                status = status,
                expectedGameCount = null,
            )
            `when`(seriesRepository.findByIdWithTournament(7L)).thenReturn(series)

            val telegram = service.evaluate(7L)
            val admin = service.evaluateForAdmin(7L)

            assertFalse(telegram.ready)
            assertEquals("series status must be SCORING", telegram.reason)
            assertFalse(admin.ready)
            assertEquals("expected game count is not configured", admin.reason)
        }
    }
}
