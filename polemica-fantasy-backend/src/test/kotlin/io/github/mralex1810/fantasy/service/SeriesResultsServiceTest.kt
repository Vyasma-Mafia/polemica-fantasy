package io.github.mralex1810.fantasy.service

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.mralex1810.fantasy.dto.admin.response.SeriesResultsPointsStatus
import io.github.mralex1810.fantasy.entity.Series
import io.github.mralex1810.fantasy.entity.SeriesGame
import io.github.mralex1810.fantasy.entity.Tournament
import io.github.mralex1810.fantasy.entity.TournamentKind
import io.github.mralex1810.fantasy.polemica.PolemicaPublicPointsLoader
import io.github.mralex1810.fantasy.polemica.PolemicaPublicPointsResult
import io.github.mralex1810.fantasy.polemica.PolemicaPublicPointsRow
import io.github.mralex1810.fantasy.repository.SeriesGameRepository
import io.github.mralex1810.fantasy.repository.SeriesRepository
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SeriesResultsServiceTest {
    private val seriesRepository = mock<SeriesRepository>()
    private val gameRepository = mock<SeriesGameRepository>()
    private val pointsLoader = mock<PolemicaPublicPointsLoader>()
    private val service = SeriesResultsService(seriesRepository, gameRepository, pointsLoader)
    private val objectMapper = ObjectMapper()

    @Test
    fun `standalone orders games labels columns and sorts totals`() {
        val series = series(TournamentKind.STANDALONE)
        val late = game(
            id = 12,
            polemicaId = 102,
            playedAt = "2026-01-02T10:00:00Z",
            players = listOf(player(1, 11, "Alpha latest")),
        )
        val early = game(
            id = 11,
            polemicaId = 101,
            playedAt = "2026-01-01T10:00:00Z",
            players = listOf(player(1, 11, "Alpha"), player(2, 22, "Beta")),
        )
        whenever(seriesRepository.findByIdWithTournament(1)).thenReturn(series)
        whenever(gameRepository.findAllBySeries_Id(1)).thenReturn(listOf(late, early))
        whenever(pointsLoader.load(101)).thenReturn(points(1 to 0.0, 2 to 2.25))
        whenever(pointsLoader.load(102)).thenReturn(points(1 to -1.5))

        val result = service.getResults(1)

        assertEquals(listOf(11L, 12L), result.games.map { it.seriesGameId })
        assertEquals(listOf("1", "2"), result.games.map { it.columnLabel })
        assertEquals(listOf("Beta", "Alpha latest"), result.players.map { it.nickname })
        assertEquals(listOf(2.25, -1.5), result.players.map { it.totalPoints })
        val beta = result.players.first()
        assertEquals(listOf(11L, 12L), beta.cells.map { it.seriesGameId })
        assertEquals(2.25, beta.cell(11).points)
        assertFalse(beta.cell(12).participated)
        assertNull(beta.cell(12).points)
        assertTrue(beta.complete)
    }

    @Test
    fun `competition orders by actual game metadata and keeps physical duplicates`() {
        val series = series(TournamentKind.POLEMICA_COMPETITION)
        val noNum = game(24, 204, "2026-01-01T10:00:00Z", emptyList(), num = null, phase = 0, table = 1)
        val phaseOne = game(23, 203, "2026-01-01T10:00:00Z", emptyList(), num = 8, phase = 1, table = 1)
        val tableTwo = game(22, 202, "2026-01-01T10:00:00Z", emptyList(), num = 8, phase = 0, table = 2)
        val duplicate = game(21, 202, "2026-01-01T09:00:00Z", emptyList(), num = 8, phase = 0, table = 1)
        whenever(seriesRepository.findByIdWithTournament(1)).thenReturn(series)
        whenever(gameRepository.findAllBySeries_Id(1)).thenReturn(listOf(noNum, phaseOne, tableTwo, duplicate))
        whenever(pointsLoader.load(any())).thenReturn(PolemicaPublicPointsResult(emptyList(), emptyList()))

        val result = service.getResults(1)

        assertEquals(listOf(21L, 22L, 23L, 24L), result.games.map { it.seriesGameId })
        assertEquals(listOf("8", "8", "8", "#204"), result.games.map { it.columnLabel })
        assertEquals(4, result.games.size)
    }

    @Test
    fun `partial points distinguish absent participant from participated with missing points`() {
        val series = series(TournamentKind.STANDALONE)
        val first = game(31, 301, "2026-01-01T09:00:00Z", listOf(player(1, 11, "Alpha")))
        val second = game(32, 302, "2026-01-01T10:00:00Z", listOf(player(2, 22, "Beta")))
        whenever(seriesRepository.findByIdWithTournament(1)).thenReturn(series)
        whenever(gameRepository.findAllBySeries_Id(1)).thenReturn(listOf(first, second))
        whenever(pointsLoader.load(301)).thenReturn(points(1 to 3.0))
        whenever(pointsLoader.load(302)).thenReturn(PolemicaPublicPointsResult(listOf(PolemicaPublicPointsRow(3, 9.0)), emptyList()))

        val result = service.getResults(1)

        val alpha = result.players.first { it.polemicaUserId == 11L }
        val beta = result.players.first { it.polemicaUserId == 22L }
        assertFalse(alpha.cell(32).participated)
        assertTrue(alpha.complete)
        assertTrue(beta.cell(32).participated)
        assertNull(beta.cell(32).points)
        assertFalse(beta.complete)
        assertEquals(SeriesResultsPointsStatus.PARTIAL, result.games[1].pointsStatus)
    }

    @Test
    fun `duplicate positions are partial and never assigned ambiguously`() {
        val series = series(TournamentKind.STANDALONE)
        val game = game(41, 401, "2026-01-01T09:00:00Z", listOf(player(1, 11, "Alpha")))
        whenever(seriesRepository.findByIdWithTournament(1)).thenReturn(series)
        whenever(gameRepository.findAllBySeries_Id(1)).thenReturn(listOf(game))
        whenever(pointsLoader.load(401)).thenReturn(points(1 to 2.0, 1 to 3.0))

        val result = service.getResults(1)

        assertEquals(SeriesResultsPointsStatus.PARTIAL, result.games.single().pointsStatus)
        assertNull(result.players.single().cell(41).points)
        assertFalse(result.players.single().complete)
        assertTrue(result.warnings.any { it.contains("duplicate public") })
    }

    @Test
    fun `duplicate cached player ids remain separate auditable rows`() {
        val series = series(TournamentKind.STANDALONE)
        val game = game(
            45,
            405,
            "2026-01-01T09:00:00Z",
            listOf(player(1, 11, "Alpha"), player(2, 11, "Alpha duplicate")),
        )
        whenever(seriesRepository.findByIdWithTournament(1)).thenReturn(series)
        whenever(gameRepository.findAllBySeries_Id(1)).thenReturn(listOf(game))
        whenever(pointsLoader.load(405)).thenReturn(points(1 to 2.0, 2 to 3.0))

        val result = service.getResults(1)

        assertEquals(SeriesResultsPointsStatus.PARTIAL, result.games.single().pointsStatus)
        assertEquals(listOf(3.0, 2.0), result.players.map { it.totalPoints })
        assertEquals(2, result.players.map { it.playerKey }.distinct().size)
        assertTrue(result.warnings.any { it.contains("duplicate cached Polemica player ids") })
    }

    @Test
    fun `load failure is local and unfinished game is not fetched`() {
        val series = series(TournamentKind.STANDALONE)
        val failed = game(51, 501, "2026-01-01T09:00:00Z", listOf(player(1, 11, "Alpha")))
        val unfinished = game(52, 502, "2026-01-01T10:00:00Z", listOf(player(1, 11, "Alpha")), finished = false)
        whenever(seriesRepository.findByIdWithTournament(1)).thenReturn(series)
        whenever(gameRepository.findAllBySeries_Id(1)).thenReturn(listOf(failed, unfinished))
        whenever(pointsLoader.load(501)).thenThrow(IllegalStateException("boom"))

        val result = service.getResults(1)

        assertEquals(SeriesResultsPointsStatus.LOAD_FAILED, result.games[0].pointsStatus)
        assertEquals(SeriesResultsPointsStatus.UNFINISHED, result.games[1].pointsStatus)
        assertFalse(result.players.single().complete)
        verify(pointsLoader, never()).load(502)
    }

    @Test
    fun `unfinished participation makes total incomplete`() {
        val series = series(TournamentKind.STANDALONE)
        val unfinished = game(61, 601, "2026-01-01T10:00:00Z", listOf(player(1, 11, "Alpha")), finished = false)
        whenever(seriesRepository.findByIdWithTournament(1)).thenReturn(series)
        whenever(gameRepository.findAllBySeries_Id(1)).thenReturn(listOf(unfinished))

        val result = service.getResults(1)

        assertFalse(result.players.single().complete)
        assertEquals(0.0, result.players.single().totalPoints)
        verify(pointsLoader, never()).load(601)
    }

    @Test
    fun `missing series is 404`() {
        whenever(seriesRepository.findByIdWithTournament(999)).thenReturn(null)

        val exception = kotlin.runCatching { service.getResults(999) }.exceptionOrNull() as ResponseStatusException

        assertEquals(404, exception.statusCode.value())
        verify(gameRepository, never()).findAllBySeries_Id(any())
    }

    private fun series(kind: TournamentKind) = Series(
        id = 1,
        tournament = Tournament(id = 10, kind = kind),
    )

    private fun game(
        id: Long,
        polemicaId: Long,
        playedAt: String,
        players: List<String>,
        num: Int? = null,
        phase: Int? = null,
        table: Int? = null,
        finished: Boolean = true,
    ): SeriesGame {
        val fields = mutableListOf<String>()
        num?.let { fields += "\"num\":$it" }
        phase?.let { fields += "\"phase\":$it" }
        table?.let { fields += "\"table\":$it" }
        fields += "\"players\":[${players.joinToString()}]"
        fields += "\"result\":${if (finished) "\"RED_WIN\"" else "null"}"
        return SeriesGame(
            id = id,
            polemicaGameId = polemicaId,
            playedAt = Instant.parse(playedAt),
            gameDataCache = objectMapper.readTree("{${fields.joinToString()}}"),
        )
    }

    private fun player(position: Int, id: Long?, nickname: String): String =
        """{"position":$position,"username":"$nickname","player":${id?.let { "{\"id\":$it,\"username\":\"$nickname\"}" } ?: "null"}}"""

    private fun points(vararg values: Pair<Int, Double>) = PolemicaPublicPointsResult(
        rows = values.map { PolemicaPublicPointsRow(it.first, it.second) },
        warnings = emptyList(),
    )

    private fun io.github.mralex1810.fantasy.dto.admin.response.SeriesResultsPlayerDto.cell(seriesGameId: Long) =
        cells.single { it.seriesGameId == seriesGameId }
}
