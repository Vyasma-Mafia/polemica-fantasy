package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.entity.FantasyPlayer
import io.github.mralex1810.fantasy.entity.Series
import io.github.mralex1810.fantasy.entity.SeriesPlayer
import io.github.mralex1810.fantasy.entity.TelegramUser
import io.github.mralex1810.fantasy.entity.Tournament
import io.github.mralex1810.fantasy.entity.TournamentKind
import io.github.mralex1810.fantasy.entity.TournamentPlayer
import io.github.mralex1810.fantasy.repository.SeriesPlayerRepository
import io.github.mralex1810.fantasy.repository.SeriesRepository
import io.github.mralex1810.fantasy.repository.TournamentPlayerRepository
import io.github.mralex1810.fantasy.repository.TournamentRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.Optional

/** Internal IDs deliberately differ from Polemica IDs: names cannot establish identity. */
class UserResearchIdentityTest {
    private val seriesRepository = mock<SeriesRepository>()
    private val seriesPlayerRepository = mock<SeriesPlayerRepository>()
    private val tournamentRepository = mock<TournamentRepository>()
    private val tournamentPlayerRepository = mock<TournamentPlayerRepository>()
    private val streamLinkService = mock<StreamLinkService>()
    private val seriesService = UserSeriesService(
        seriesRepository, mock(), seriesPlayerRepository, mock(), mock(), mock(), mock(),
        mock(), streamLinkService,
    )
    private val tournamentService = UserTournamentService(
        tournamentRepository, seriesRepository, mock(), mock(), tournamentPlayerRepository,
        mock(), mock(), streamLinkService,
    )
    private val player = FantasyPlayer(id = 33, polemicaUserId = 700001, nickname = "Local alias")
    private val participant = TournamentPlayer(id = 44, fantasyPlayer = player)
    private val user = TelegramUser(id = 55)

    @Test
    fun `standalone detail exposes profile identity without requiring a competition`() {
        val tournament = Tournament(id = 28, name = "Local league", kind = TournamentKind.STANDALONE)
        val result = seriesService.getSeriesDetail(user, prepareSeries(tournament))

        assertEquals(28L, result.tournamentId)
        assertEquals(TournamentKind.STANDALONE, result.tournamentKind)
        assertNull(result.polemicaCompetitionId)
        assertEquals(44L, result.players.single().tournamentPlayerId)
        assertEquals(33L, result.players.single().fantasyPlayerId)
        assertEquals(700001L, result.players.single().polemicaUserId)
        assertEquals("Local alias", result.players.single().nickname)
    }

    @Test
    fun `competition detail exposes external competition ID independently of name and internal ID`() {
        val tournament = Tournament(
            id = 28, name = "Different local name", kind = TournamentKind.POLEMICA_COMPETITION,
            polemicaCompetitionId = 5705,
        )
        val result = seriesService.getSeriesDetail(user, prepareSeries(tournament))

        assertEquals(28L, result.tournamentId)
        assertEquals(TournamentKind.POLEMICA_COMPETITION, result.tournamentKind)
        assertEquals(5705L, result.polemicaCompetitionId)
        assertEquals(700001L, result.players.single().polemicaUserId)
    }

    @Test
    fun `tournament participants expose the same canonical primary profile ID`() {
        whenever(tournamentRepository.existsById(28)).thenReturn(true)
        whenever(tournamentPlayerRepository.findAllByTournament_IdOrderById(28))
            .thenReturn(listOf(participant))

        val result = tournamentService.listParticipants(28).single()

        assertEquals(44L, result.tournamentPlayerId)
        assertEquals(33L, result.fantasyPlayerId)
        assertEquals(700001L, result.polemicaUserId)
    }

    private fun prepareSeries(tournament: Tournament): Long {
        val series = Series(id = 275, tournament = tournament)
        whenever(seriesRepository.findById(275)).thenReturn(Optional.of(series))
        whenever(seriesPlayerRepository.findAllBySeries_IdWithTournamentPlayers(275))
            .thenReturn(listOf(SeriesPlayer(id = 66, series = series, tournamentPlayer = participant)))
        return 275
    }
}
