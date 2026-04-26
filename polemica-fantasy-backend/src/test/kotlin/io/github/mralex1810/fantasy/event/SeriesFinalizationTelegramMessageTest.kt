package io.github.mralex1810.fantasy.event

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SeriesFinalizationTelegramMessageTest {

    private val sampleRecipient = SeriesFinalizedRecipient(
        telegramId = 42L,
        leagueResults = listOf(
            LeagueResult(
                leagueName = "Основная лига",
                winnerPublicName = "Alice",
                place = 3,
                total = 15,
                reward = 50L,
            ),
        ),
        totalReward = 50L,
        balanceAfter = 200L,
    )

    @Test
    fun `message contains tournament series place reward balance`() {
        val text = buildSeriesFinalizedTelegramMessage(
            tournamentName = "Spring Cup",
            seriesName = "Round 1",
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
            recipient = sampleRecipient.copy(
                leagueResults = listOf(
                    LeagueResult(
                        leagueName = "Main",
                        winnerPublicName = "ПобедительНик",
                        place = 1,
                        total = 10,
                        reward = 100L,
                    ),
                ),
                totalReward = 100L,
            ),
        )
        assertTrue(text.contains("Победитель серии: ПобедительНик (Main)."))
    }

    @Test
    fun `message omits winner line when winner name null`() {
        val text = buildSeriesFinalizedTelegramMessage(
            tournamentName = "T",
            seriesName = "S",
            recipient = sampleRecipient.copy(
                leagueResults = listOf(
                    LeagueResult(
                        leagueName = "Main",
                        winnerPublicName = null,
                        place = 4,
                        total = 10,
                        reward = 0L,
                    ),
                ),
                totalReward = 0L,
            ),
        )
        assertFalse(text.contains("Победитель серии:"))
    }

    @Test
    fun `message renders multi league sections and total reward`() {
        val text = buildSeriesFinalizedTelegramMessage(
            tournamentName = "T",
            seriesName = "S",
            recipient = SeriesFinalizedRecipient(
                telegramId = 42L,
                leagueResults = listOf(
                    LeagueResult(
                        leagueName = "Основная",
                        winnerPublicName = "Winner A",
                        place = 2,
                        total = 20,
                        reward = 100L,
                    ),
                    LeagueResult(
                        leagueName = "Бюджетная",
                        winnerPublicName = "Winner B",
                        place = 8,
                        total = 20,
                        reward = 0L,
                    ),
                ),
                totalReward = 100L,
                balanceAfter = 700L,
            ),
        )
        assertTrue(text.contains("Основная (20 участников):"))
        assertTrue(text.contains("Бюджетная (20 участников):"))
        assertTrue(text.contains("Итого за серию: +100 фантиков."))
    }
}
