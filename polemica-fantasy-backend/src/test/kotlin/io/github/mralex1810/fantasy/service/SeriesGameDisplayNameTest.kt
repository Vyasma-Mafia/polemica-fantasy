package io.github.mralex1810.fantasy.service

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.mralex1810.fantasy.entity.SeriesGame
import kotlin.test.Test
import kotlin.test.assertEquals

class SeriesGameDisplayNameTest {

    private val objectMapper = ObjectMapper()

    @Test
    fun `uses stored name when present`() {
        val g = SeriesGame(polemicaGameId = 1, gameName = "Финал")
        assertEquals("Финал", formatSeriesGameDisplayName(g))
    }

    @Test
    fun `synthetic name from num in cache`() {
        val g = SeriesGame(polemicaGameId = 99, gameName = "(no name)")
        g.gameDataCache = objectMapper.readTree("""{"num": 7}""")
        assertEquals("Игра 7", formatSeriesGameDisplayName(g))
    }

    @Test
    fun `fallback to polemica id`() {
        val g = SeriesGame(polemicaGameId = 42, gameName = "")
        assertEquals("Игра #42", formatSeriesGameDisplayName(g))
    }
}
