package io.github.mralex1810.fantasy.service.leagueimport

import io.github.mralex1810.fantasy.config.TelegramLeagueImportProperties
import io.github.mralex1810.fantasy.dto.internal.TelegramLeagueImportMediaEvidence
import io.github.mralex1810.fantasy.dto.internal.TelegramLeagueImportOcrEvidence
import io.github.mralex1810.fantasy.dto.internal.TelegramLeagueImportOcrLine
import io.github.mralex1810.fantasy.entity.FantasyPlayer
import io.github.mralex1810.fantasy.entity.TournamentPlayer
import io.github.mralex1810.fantasy.repository.TournamentPlayerRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class LeagueImportRosterResolverTest {
    private val properties = TelegramLeagueImportProperties().apply {
        policies.lp = TelegramLeagueImportProperties.LeaguePolicy(
            enabled = true,
            tournamentId = 17,
            expectedRosterCount = 10,
        )
    }
    private val repository = mock<TournamentPlayerRepository>()
    private val resolver = LeagueImportRosterResolver(properties, repository)

    @Test
    fun `resolves exactly ten unique tournament nicknames and ignores poster noise`() {
        val players = (1L..10L).map { player(it, "Player $it") }
        whenever(repository.findAllByTournamentIdWithFantasyPlayer(17)).thenReturn(players)
        val lines = listOf("Лига претендентов", "2 сезон") + players.map { it.fantasyPlayer!!.nickname } + listOf("Twitch")

        val result = resolver.resolve("ЛП", 17, "a".repeat(64), media(lines))

        assertTrue(result.draft.ready)
        assertEquals((1L..10L).toList(), result.draft.resolved.map { it.tournamentPlayerId })
        assertTrue(result.draft.issues.isEmpty())
    }

    @Test
    fun `normalization collision is ambiguous and cannot produce a roster`() {
        val players = (1L..9L).map { player(it, "Player $it") } +
            player(10, "Same Name") + player(11, "same   name")
        whenever(repository.findAllByTournamentIdWithFantasyPlayer(17)).thenReturn(players)

        val result = resolver.resolve(
            "ЛП", 17, "b".repeat(64),
            media((1L..9L).map { "Player $it" } + " SAME NAME "),
        )

        assertFalse(result.draft.ready)
        assertTrue(result.draft.issues.any { it.contains("ambiguous") })
    }

    @Test
    fun `latin cyrillic confusable is not substituted`() {
        val players = (1L..10L).map { player(it, if (it == 10L) "Мир" else "Player $it") }
        whenever(repository.findAllByTournamentIdWithFantasyPlayer(17)).thenReturn(players)

        val result = resolver.resolve(
            "ЛП", 17, "c".repeat(64),
            media((1L..9L).map { "Player $it" } + "Mир"),
        )

        assertFalse(result.draft.ready)
        assertTrue(result.draft.issues.any { it.contains("found 9") })
    }

    @Test
    fun `commentator or substitution marker blocks otherwise exact roster`() {
        val players = (1L..10L).map { player(it, "Player $it") }
        whenever(repository.findAllByTournamentIdWithFantasyPlayer(17)).thenReturn(players)

        val result = resolver.resolve(
            "ЛП", 17, "d".repeat(64),
            media(players.map { it.fantasyPlayer!!.nickname }, fullTextSuffix = "\nКомментаторы: Host"),
        )

        assertFalse(result.draft.ready)
        assertTrue(result.draft.issues.single().contains("manual review"))
    }

    private fun player(id: Long, nickname: String) = TournamentPlayer(
        id = id,
        fantasyPlayer = FantasyPlayer(id = 100 + id, polemicaUserId = 1000 + id, nickname = nickname),
    )

    private fun media(lines: List<String>, fullTextSuffix: String = "") = TelegramLeagueImportMediaEvidence(
        telegramMediaId = "photo-1",
        mimeType = "image/jpeg",
        byteSize = 1000,
        width = 1200,
        height = 800,
        sha256 = "e".repeat(64),
        ocr = TelegramLeagueImportOcrEvidence(
            status = "SUCCESS",
            provider = "YANDEX_VISION",
            model = "page",
            languageCodes = listOf("ru", "en"),
            checksum = "f".repeat(64),
            fullText = lines.joinToString("\n") + fullTextSuffix,
            lines = lines.map(::TelegramLeagueImportOcrLine),
        ),
    )
}
