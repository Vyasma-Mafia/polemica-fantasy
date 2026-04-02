package io.github.mralex1810.fantasy.event

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SeriesFinalizationTelegramMessageTest {

    private val sampleRecipient = SeriesFinalizedRecipient(
        telegramId = 42L,
        place = 3,
        total = 15,
        reward = 50L,
        balanceAfter = 200L,
    )

    @Test
    fun `message contains tournament series place reward balance`() {
        val text = buildSeriesFinalizedTelegramMessage(
            tournamentName = "Spring Cup",
            seriesName = "Round 1",
            winnerPublicName = "Alice",
            recipient = sampleRecipient,
        )
        assertTrue(text.contains("Spring Cup"))
        assertTrue(text.contains("Round 1"))
        assertTrue(text.contains("3-е место из 15"))
        assertTrue(text.contains("50"))
        assertTrue(text.contains("200"))
    }

    @Test
    fun `message includes winner line when winner name provided`() {
        val text = buildSeriesFinalizedTelegramMessage(
            tournamentName = "T",
            seriesName = "S",
            winnerPublicName = "ПобедительНик",
            recipient = sampleRecipient,
        )
        assertTrue(text.contains("Победитель серии: ПобедительНик."))
    }

    @Test
    fun `message omits winner line when winner name null`() {
        val text = buildSeriesFinalizedTelegramMessage(
            tournamentName = "T",
            seriesName = "S",
            winnerPublicName = null,
            recipient = sampleRecipient,
        )
        assertFalse(text.contains("Победитель серии:"))
    }

    @Test
    fun `message omits winner line when winner name blank`() {
        val text = buildSeriesFinalizedTelegramMessage(
            tournamentName = "T",
            seriesName = "S",
            winnerPublicName = "   ",
            recipient = sampleRecipient,
        )
        assertFalse(text.contains("Победитель серии:"))
    }
}
