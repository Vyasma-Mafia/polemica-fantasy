package io.github.mralex1810.fantasy.service.leagueimport

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.mafia.vyasma.polemica.library.model.game.PolemicaGame
import com.github.mafia.vyasma.polemica.library.model.game.PolemicaGameResult
import com.github.mafia.vyasma.polemica.library.model.game.PolemicaPlayer
import com.github.mafia.vyasma.polemica.library.model.game.PolemicaUser
import com.github.mafia.vyasma.polemica.library.model.game.Position
import com.github.mafia.vyasma.polemica.library.model.game.Role
import io.github.mralex1810.fantasy.entity.Series
import io.github.mralex1810.fantasy.entity.SeriesGame
import io.github.mralex1810.fantasy.entity.SeriesStatus
import io.github.mralex1810.fantasy.repository.LeagueImportItemRow
import io.github.mralex1810.fantasy.repository.LeagueImportRepository
import io.github.mralex1810.fantasy.repository.LeagueImportResultMafiaOverrideRow
import io.github.mralex1810.fantasy.repository.SeriesGameRepository
import io.github.mralex1810.fantasy.repository.SeriesRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class LeagueResultMafiaOverrideServiceTest {
    @Mock private lateinit var seriesRepository: SeriesRepository
    @Mock private lateinit var seriesGameRepository: SeriesGameRepository
    @Mock private lateinit var leagueImportRepository: LeagueImportRepository
    @Mock private lateinit var objectMapper: ObjectMapper
    @Mock private lateinit var draftJson: JsonNode
    @Mock private lateinit var gameJson: JsonNode
    @InjectMocks private lateinit var service: LeagueResultMafiaOverrideService

    @Test
    fun `creates immutable audited override only when corrected line matches current roles`() {
        arrangeEvidence()
        val stored = LeagueImportResultMafiaOverrideRow(
            id = 9L,
            seriesId = 246L,
            importItemId = 20L,
            gameNumber = 2,
            originalMafiaLine = "Beach House, Scotland, Beach House",
            correctedMafiaLine = "Beach House, Scotland, Якорь",
            reason = "Source post cannot be edited",
            adminActor = "admin",
            createdAt = Instant.parse("2026-08-24T00:00:00Z"),
        )
        `when`(
            leagueImportRepository.insertResultMafiaOverride(
                246L, 20L, 2, stored.originalMafiaLine, stored.correctedMafiaLine, stored.reason, stored.adminActor,
            ),
        ).thenReturn(stored)

        val result = service.create(
            246L, 2, "Beach House, Scotland, Якорь", "Source post cannot be edited", "admin",
        )

        assertEquals(9L, result.id)
        assertEquals(stored.originalMafiaLine, result.originalMafiaLine)
        assertEquals(stored.correctedMafiaLine, result.correctedMafiaLine)
        verify(leagueImportRepository).audit(
            20L, null, null, "RESULT_MAFIA_OVERRIDDEN", "COMMITTED",
            mapOf(
                "seriesId" to 246L,
                "gameNumber" to 2,
                "originalMafiaLine" to stored.originalMafiaLine,
                "correctedMafiaLine" to stored.correctedMafiaLine,
                "reason" to stored.reason,
                "adminActor" to stored.adminActor,
            ),
        )
    }

    @Test
    fun `rejects an override that does not match current roles`() {
        arrangeEvidence()

        val error = assertThrows(ResponseStatusException::class.java) {
            service.create(246L, 2, "Beach House, Scotland, Jesica", "Source post cannot be edited", "admin")
        }

        assertEquals(409, error.statusCode.value())
        verify(leagueImportRepository, never()).insertResultMafiaOverride(
            anyLong(), anyLong(), anyInt(), anyString(), anyString(), anyString(), anyString(),
        )
    }

    private fun arrangeEvidence() {
        val series = Series(id = 246L, status = SeriesStatus.SCORING, finalized = false)
        val item = LeagueImportItemRow(
            id = 20L,
            sourceChannelId = -100L,
            sourceMessageId = 2280L,
            currentRevision = 1,
            currentSourceVersion = "v1",
            currentContentHash = "a".repeat(64),
            currentEvidenceHash = "b".repeat(64),
            classification = "RESULT",
            leagueCode = "ЛП",
            state = "WAITING_FOR_GAMES",
            version = 2L,
            draftJson = draftJson,
            draftChecksum = "c".repeat(64),
            targetSeriesId = 246L,
            readinessStatus = "NOT_READY",
            readinessChecksum = null,
            stablePollCount = 0,
            readySince = null,
            lastStableObservationAt = null,
            policyGeneration = "manual-v1",
            rosterStatus = "NO_MEDIA",
            rosterDraftJson = null,
            rosterChecksum = null,
        )
        val draft = LeagueResultDraft(
            league = "ЛП",
            seriesNumber = 39L,
            tournamentId = 1L,
            expectedGameCount = 2,
            games = listOf(
                LeagueResultGame(1, AnnouncedGameWinner.MAFIA, "A, B, C", "Sheriff"),
                LeagueResultGame(2, AnnouncedGameWinner.MAFIA, "Beach House, Scotland, Beach House", "Wake up"),
            ),
            sourceUrl = "https://t.me/example/2280",
        )
        val game = polemicaGame()
        `when`(seriesRepository.findByIdForUpdate(246L)).thenReturn(series)
        `when`(leagueImportRepository.findCurrentResultItemsForSeries(246L, lock = true)).thenReturn(listOf(item))
        `when`(objectMapper.treeToValue(draftJson, LeagueResultDraft::class.java)).thenReturn(draft)
        `when`(seriesGameRepository.findAllBySeries_Id(246L)).thenReturn(
            listOf(
                SeriesGame(id = 1L, series = series, polemicaGameId = 100L, playedAt = Instant.parse("2026-08-21T16:00:00Z")),
                SeriesGame(id = 2L, series = series, polemicaGameId = 101L, playedAt = Instant.parse("2026-08-21T17:00:00Z"), gameDataCache = gameJson),
            ),
        )
        `when`(objectMapper.treeToValue(gameJson, PolemicaGame::class.java)).thenReturn(game)
    }

    private fun polemicaGame() = PolemicaGame(
        id = 101L,
        name = "Game 2",
        master = 1L,
        referee = null,
        scoringVersion = null,
        scoringType = 0,
        version = 0,
        zeroVoting = null,
        tags = emptyList(),
        players = listOf(
            player(1, "Beach House", Role.DON),
            player(2, "Scotland", Role.MAFIA),
            player(3, "Якорь", Role.MAFIA),
            player(4, "Wake up", Role.SHERIFF),
        ),
        checks = emptyList(),
        shots = emptyList(),
        stage = null,
        votes = emptyList(),
        comKiller = null,
        bonuses = emptyList(),
        started = LocalDateTime.parse("2026-08-21T20:00:00"),
        stop = null,
        isLive = false,
        result = PolemicaGameResult.BLACK_WIN,
        num = null,
        table = null,
        phase = null,
        factor = null,
    )

    private fun player(position: Int, username: String, role: Role) = PolemicaPlayer(
        position = Position.entries.first { it.value == position },
        username = username,
        role = role,
        techs = emptyList(),
        fouls = emptyList(),
        guess = null,
        player = PolemicaUser(position.toLong(), username),
        disqual = null,
        award = null,
    )
}
