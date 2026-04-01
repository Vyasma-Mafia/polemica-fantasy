package io.github.mralex1810.fantasy.event

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SeriesFinalizationTelegramMessageTest {

    @Test
    fun `message contains tournament series place reward balance`() {
        val text = buildSeriesFinalizedTelegramMessage(
            tournamentName = "Spring Cup",
            seriesName = "Round 1",
            recipient = SeriesFinalizedRecipient(
                telegramId = 42L,
                place = 3,
                total = 15,
                reward = 50L,
                balanceAfter = 200L,
            ),
        )
        assertTrue(text.contains("Spring Cup"))
        assertTrue(text.contains("Round 1"))
        assertTrue(text.contains("3-е место из 15"))
        assertTrue(text.contains("50"))
        assertTrue(text.contains("200"))
    }
}
