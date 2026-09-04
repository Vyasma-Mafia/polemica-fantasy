package io.github.mralex1810.fantasy.service.leagueimport

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
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
        policies.zl = TelegramLeagueImportProperties.LeaguePolicy(
            enabled = true,
            tournamentId = 13,
            expectedRosterCount = 10,
            rosterNicknameAliases = linkedMapOf(
                "Градиент" to "Gradient",
                "Cristo I" to "Cristo",
                "Doc." to "Doc",
            ),
        )
    }
    private val repository = mock<TournamentPlayerRepository>()
    private val resolver = LeagueImportRosterResolver(properties, repository)
    private val objectMapper = ObjectMapper()

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

    @Test
    fun `real LP 35 OCR golden resolves all ten players`() {
        val fixture = fixture("announcement-2243.json")
        val expected = fixture.path("expectedRoster").map(JsonNode::asText)
        val players = expected.mapIndexed { index, nickname -> player(index.toLong() + 1, nickname) }
        whenever(repository.findAllByTournamentIdWithFantasyPlayer(17)).thenReturn(players)

        val result = resolver.resolve(
            "ЛП", 17, "1".repeat(64), media(fixture.path("ocrLines").map(JsonNode::asText)),
            announcementText = fixture.path("caption").asText(),
        )

        assertTrue(result.draft.ready, result.draft.issues.joinToString("; "))
        assertEquals(expected, result.draft.resolved.map { it.productionNickname })
        assertTrue(result.draft.substitutions.isEmpty())
    }

    @Test
    fun `real ZL 24 OCR golden applies caption substitution`() {
        val fixture = fixture("announcement-2247.json")
        val expected = fixture.path("expectedRoster").map(JsonNode::asText)
        val players = (expected + "Монарх").distinct().mapIndexed { index, nickname -> player(index.toLong() + 1, nickname) }
        whenever(repository.findAllByTournamentIdWithFantasyPlayer(13)).thenReturn(players)

        val result = resolver.resolve(
            "ЗЛ", 13, "2".repeat(64), media(fixture.path("ocrLines").map(JsonNode::asText)),
            announcementText = fixture.path("caption").asText(),
        )

        assertTrue(result.draft.ready, result.draft.issues.joinToString("; "))
        assertEquals(expected, result.draft.resolved.map { it.productionNickname })
        assertEquals(1, result.draft.substitutions.size)
        assertEquals("Монарх", result.draft.substitutions.single().outgoingNickname)
        assertEquals("Воробей", result.draft.substitutions.single().incomingNickname)
        assertTrue(result.draft.resolved.any { it.ocrText == "Градиент" && it.productionNickname == "Gradient" })
        assertTrue(result.draft.resolved.any { it.ocrText == "Cristo I" && it.productionNickname == "Cristo" })
    }

    @Test
    fun `configured punctuation alias resolves exact tournament player`() {
        val players = (1L..9L).map { player(it, "Player $it") } + player(10, "Doc")
        whenever(repository.findAllByTournamentIdWithFantasyPlayer(13)).thenReturn(players)

        val result = resolver.resolve(
            "ЗЛ", 13, "6".repeat(64),
            media((1L..9L).map { "Player $it" } + "Doc."),
        )

        assertTrue(result.draft.ready, result.draft.issues.joinToString("; "))
        assertTrue(result.draft.resolved.any { it.ocrText == "Doc." && it.productionNickname == "Doc" })
    }

    @Test
    fun `unsupported caption substitution wording requires review`() {
        val players = (1L..10L).map { player(it, "Player $it") }
        whenever(repository.findAllByTournamentIdWithFantasyPlayer(17)).thenReturn(players)

        val result = resolver.resolve(
            "ЛП", 17, "3".repeat(64), media(players.map { it.fantasyPlayer!!.nickname }),
            announcementText = "Замена: Player 1 / Player 11",
        )

        assertFalse(result.draft.ready)
        assertTrue(result.draft.issues.single().contains("unsupported"))
    }

    @Test
    fun `instead-of caption form applies exact incoming player`() {
        val players = (1L..9L).map { player(it, "Player $it") } + player(10, "SM") + player(11, "День")
        whenever(repository.findAllByTournamentIdWithFantasyPlayer(17)).thenReturn(players)

        val result = resolver.resolve(
            "ЛП", 17, "4".repeat(64), media((1L..9L).map { "Player $it" } + "SM"),
            announcementText = "Вместо SM выступит День",
        )

        assertTrue(result.draft.ready, result.draft.issues.joinToString("; "))
        assertFalse(result.draft.resolved.any { it.productionNickname == "SM" })
        assertTrue(result.draft.resolved.any { it.productionNickname == "День" })
    }

    @Test
    fun `exact outgoing colliding with another players genitive requires review`() {
        val players = (1L..8L).map { player(it, "Player $it") } +
            player(9, "Монарх") + player(10, "Монарха") + player(11, "Воробей")
        whenever(repository.findAllByTournamentIdWithFantasyPlayer(17)).thenReturn(players)

        val result = resolver.resolve(
            "ЛП", 17, "5".repeat(64), media((1L..8L).map { "Player $it" } + listOf("Монарх", "Монарха")),
            announcementText = "Заменой Монарха выступит Воробей",
        )

        assertFalse(result.draft.ready)
        assertTrue(result.draft.issues.any { it.contains("not exact-unique") })
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

    private fun fixture(name: String): JsonNode = requireNotNull(
        javaClass.getResourceAsStream("/league-import/ocr/$name"),
    ).use(objectMapper::readTree)
}
