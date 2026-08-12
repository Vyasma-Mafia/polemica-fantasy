package io.github.mralex1810.fantasy.service.leagueimport

import io.github.mralex1810.fantasy.config.TelegramLeagueImportProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

class LeagueAnnouncementParserTest {
    private val properties = TelegramLeagueImportProperties().apply {
        policies.lp = TelegramLeagueImportProperties.LeaguePolicy(
            enabled = true,
            tournamentId = 13,
            nameTemplate = "ЛП. Серия %d",
            namePrefix = "ЛП",
        )
    }
    private val parser = LeagueAnnouncementParser(properties)

    @Test
    fun `real LP 34 announcement becomes exact standalone draft`() {
        val text = """
            **Лига Претендентов: Серия 34.**
            Дата: 11 августа
            Время: 19:00 МСК.
            #анонс_ЛП
        """.trimIndent()
        val result = parser.parse(text, Instant.parse("2026-08-11T10:00:00Z"), "https://t.me/polemica_closed_league/2234")
        val ready = assertInstanceOf(LeagueAnnouncementParseResult.Ready::class.java, result, result.toString())
        assertEquals(34, ready.draft.seriesNumber)
        assertEquals(LocalDate.of(2026, 8, 11), ready.draft.gameStartedOn)
        assertEquals(Instant.parse("2026-08-11T16:00:00Z"), ready.draft.startsAt)
        assertEquals(Instant.parse("2026-08-11T16:10:00Z"), ready.draft.teamDeadline)
        assertEquals("ЛП. Серия 34", ready.draft.name)
    }

    @Test
    fun `yearless date uses Moscow calendar year of source post`() {
        val result = parser.parse("Серия 1, 1 января, 00:30 #анонс_лп", Instant.parse("2026-12-31T22:30:00Z"), "source")
        val ready = assertInstanceOf(LeagueAnnouncementParseResult.Ready::class.java, result, result.toString())
        assertEquals(LocalDate.of(2027, 1, 1), ready.draft.gameStartedOn)
    }

    @Test
    fun `multiple supported tags or dates are blocked`() {
        assertInstanceOf(
            LeagueAnnouncementParseResult.Blocked::class.java,
            parser.parse("Серия 34 11 августа 12 августа 19:00 #анонс_лп", Instant.parse("2026-08-11T10:00:00Z"), "source"),
        )
        assertInstanceOf(
            LeagueAnnouncementParseResult.Blocked::class.java,
            parser.parse("Серия 34 11 августа 19:00 #анонс_лп #анонс_зл", Instant.parse("2026-08-11T10:00:00Z"), "source"),
        )
    }
}
