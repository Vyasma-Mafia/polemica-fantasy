package io.github.mralex1810.fantasy.service.leagueimport

import io.github.mralex1810.fantasy.config.TelegramLeagueImportProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

class LeagueResultParserTest {
    private val properties = TelegramLeagueImportProperties().apply {
        policies.lp = TelegramLeagueImportProperties.LeaguePolicy(
            enabled = true,
            tournamentId = 13,
            nameTemplate = "ЛП. Серия %d",
            namePrefix = "ЛП",
            expectedGameCount = 5,
        )
    }
    private val parser = LeagueResultParser(properties)

    @Test
    fun `exact LP result parses contiguous five winners`() {
        val result = parser.parse(
            """
            Лига Претендентов: Серия 31. Результаты

            Игра 1
            Мафия: Alpha, Houston, TX, Gamma
            Шериф: Sheriff
            Победа: Мафия
            Игра 2
            Мафия: Alpha, Beta, Gamma
            Шериф: Sheriff
            Победили Мирные
            Игра 3
            Мафия: Alpha, Beta, Gamma
            Шериф: Sheriff
            Победа: Мафии
            Игра 4
            Мафия: Alpha, Beta, Gamma
            Шериф: Sheriff
            Победа: Красные
            Игра 5
            Мафия: Alpha, Beta, Gamma
            Шериф: Sheriff
            Победитель: Мафия

            #результаты_ЛП
            """.trimIndent(),
            "https://t.me/polemica_closed_league/2031",
        )
        val ready = assertInstanceOf(LeagueResultParseResult.Ready::class.java, result, result.toString())
        assertEquals(31L, ready.draft.seriesNumber)
        assertEquals(listOf(1, 2, 3, 4, 5), ready.draft.games.map { it.number })
        assertEquals("alpha, houston, tx, gamma", ready.draft.games.first().mafiaLine)
        assertEquals(
            listOf(
                AnnouncedGameWinner.MAFIA, AnnouncedGameWinner.CIVILIANS, AnnouncedGameWinner.MAFIA,
                AnnouncedGameWinner.CIVILIANS, AnnouncedGameWinner.MAFIA,
            ),
            ready.draft.games.map { it.winner },
        )
    }

    @Test
    fun `missing duplicate or extra game is blocked`() {
        val nonContiguous = """
            Лига Претендентов: Серия 31. Результаты
            Игра 1
            Мафия: A, B, C
            Шериф: S
            Победа: Мафия
            Игра 2
            Мафия: A, B, C
            Шериф: S
            Победа: Мирные
            Игра 3
            Мафия: A, B, C
            Шериф: S
            Победа: Мафия
            Игра 3
            Мафия: A, B, C
            Шериф: S
            Победа: Мирные
            Игра 5
            Мафия: A, B, C
            Шериф: S
            Победа: Мафия
            #результаты_ЛП
        """.trimIndent()
        assertInstanceOf(LeagueResultParseResult.Blocked::class.java, parser.parse(nonContiguous, "source"))
    }
}
