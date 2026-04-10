package io.github.mralex1810.fantasy.event

import io.github.mralex1810.fantasy.service.FantasyRosterPrunedCard
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SeriesRosterReplacementTelegramMessageTest {

    @Test
    fun `single card with replacement mentions both names`() {
        val text = buildSeriesRosterReplacementTelegramMessage(
            tournamentName = "T",
            seriesName = "S",
            prunedCardsForUser = listOf(
                FantasyRosterPrunedCard(telegramChatId = 1L, fantasyPlayerId = 10L, playerNickname = "Moss"),
            ),
            replacementNicknameByRemovedFantasyPlayerId = mapOf(10L to "Артик"),
        )
        assertTrue(text.contains("Турнир «T», серия «S»"))
        assertTrue(text.contains("Moss"))
        assertTrue(text.contains("Артик"))
        assertTrue(text.contains("Вместо него серию играет"))
    }

    @Test
    fun `single card without replacement uses fallback wording`() {
        val text = buildSeriesRosterReplacementTelegramMessage(
            tournamentName = "T",
            seriesName = "S",
            prunedCardsForUser = listOf(
                FantasyRosterPrunedCard(telegramChatId = 1L, fantasyPlayerId = 10L, playerNickname = "Moss"),
            ),
            replacementNicknameByRemovedFantasyPlayerId = emptyMap(),
        )
        assertTrue(text.contains("Moss"))
        assertTrue(text.contains("больше не входит в состав серии"))
    }

    @Test
    fun `multiple cards lists bullets`() {
        val text = buildSeriesRosterReplacementTelegramMessage(
            tournamentName = "T",
            seriesName = "S",
            prunedCardsForUser = listOf(
                FantasyRosterPrunedCard(telegramChatId = 1L, fantasyPlayerId = 10L, playerNickname = "A"),
                FantasyRosterPrunedCard(telegramChatId = 1L, fantasyPlayerId = 11L, playerNickname = "B"),
            ),
            replacementNicknameByRemovedFantasyPlayerId = mapOf(10L to "X", 11L to "Y"),
        )
        assertTrue(text.contains("• A"))
        assertTrue(text.contains("• B"))
        assertTrue(text.contains("свободные слоты"))
    }
}
