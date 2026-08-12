package io.github.mralex1810.fantasy.scoring

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.github.mafia.vyasma.polemica.library.client.GamePointsService
import com.github.mafia.vyasma.polemica.library.model.PlayerPoints
import com.github.mafia.vyasma.polemica.library.model.game.PolemicaGame
import com.github.mafia.vyasma.polemica.library.model.game.PolemicaGameResult
import com.github.mafia.vyasma.polemica.library.model.game.PolemicaPlayer
import com.github.mafia.vyasma.polemica.library.model.game.PolemicaUser
import com.github.mafia.vyasma.polemica.library.model.game.Position
import com.github.mafia.vyasma.polemica.library.model.game.Role
import io.github.mralex1810.fantasy.entity.CardTemplate
import io.github.mralex1810.fantasy.entity.FantasyPlayer
import io.github.mralex1810.fantasy.entity.FantasyTeam
import io.github.mralex1810.fantasy.entity.FantasyTeamCard
import io.github.mralex1810.fantasy.entity.Rarity
import io.github.mralex1810.fantasy.entity.Series
import io.github.mralex1810.fantasy.entity.SeriesGame
import io.github.mralex1810.fantasy.entity.SeriesPlayer
import io.github.mralex1810.fantasy.entity.TournamentPlayer
import io.github.mralex1810.fantasy.entity.UserCard
import io.github.mralex1810.fantasy.observability.FantasyMetrics
import io.github.mralex1810.fantasy.repository.FantasyTeamRepository
import io.github.mralex1810.fantasy.repository.FantasyPlayerAliasRepository
import io.github.mralex1810.fantasy.repository.SeriesGameRepository
import io.github.mralex1810.fantasy.repository.SeriesPlayerRepository
import io.github.mralex1810.fantasy.repository.SeriesRepository
import io.github.mralex1810.fantasy.scoring.perk.PerkDetectorRegistry
import io.github.mralex1810.fantasy.service.SeriesScoringContextFingerprintService
import io.github.mralex1810.fantasy.service.SeriesGameSelectorFingerprintService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.SimpleTransactionStatus
import org.springframework.web.server.ResponseStatusException
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import java.time.LocalDateTime
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class DefaultScoringServiceTest {

    @Mock
    private lateinit var seriesRepository: SeriesRepository

    @Mock
    private lateinit var seriesGameRepository: SeriesGameRepository

    @Mock
    private lateinit var fantasyTeamRepository: FantasyTeamRepository

    @Mock
    private lateinit var seriesPlayerRepository: SeriesPlayerRepository

    @Mock
    private lateinit var fantasyPlayerAliasRepository: FantasyPlayerAliasRepository

    @Mock
    private lateinit var perkRegistry: PerkDetectorRegistry

    @Mock
    private lateinit var gamePointsService: GamePointsService

    @Mock
    private lateinit var scoringContextFingerprintService: SeriesScoringContextFingerprintService

    @Mock
    private lateinit var selectorFingerprintService: SeriesGameSelectorFingerprintService

    @Test
    fun `replacement player scores when main player is absent`() {
        val team = scoringFixture()
        val game = seriesGameWithPlayers(player(22L, "replacement", Position.TWO))
        stubScoringInputs(team, game, replacementPolemicaUserId = 22L)

        service().calculateScores(1L)

        assertEquals(7.0, team.totalScore)
        val score = team.cards.single().gameScores.single()
        assertEquals(22L, score.scoredPolemicaUserId)
        assertEquals("replacement", score.scoredPlayerName)
        assertEquals(true, score.scoredViaReplacement)
        assertEquals(7.0, score.basePoints)
    }

    @Test
    fun `main player scores when both main and replacement are present`() {
        val team = scoringFixture()
        val game = seriesGameWithPlayers(
            player(11L, "main", Position.ONE),
            player(22L, "replacement", Position.TWO),
        )
        stubScoringInputs(team, game, replacementPolemicaUserId = 22L)

        service().calculateScores(1L)

        assertEquals(3.0, team.totalScore)
        val score = team.cards.single().gameScores.single()
        assertEquals(11L, score.scoredPolemicaUserId)
        assertEquals("main", score.scoredPlayerName)
        assertEquals(false, score.scoredViaReplacement)
        assertEquals(3.0, score.basePoints)
    }

    @Test
    fun `main alias scores before replacement fallback`() {
        val team = scoringFixture()
        val game = seriesGameWithPlayers(
            player(12L, "main-alias", Position.ONE),
            player(22L, "replacement", Position.TWO),
        )
        stubScoringInputs(team, game, replacementPolemicaUserId = 22L, mainAliases = listOf(11L, 12L))

        service().calculateScores(1L)

        assertEquals(3.0, team.totalScore)
        val score = team.cards.single().gameScores.single()
        assertEquals(12L, score.scoredPolemicaUserId)
        assertEquals("main-alias", score.scoredPlayerName)
        assertEquals(false, score.scoredViaReplacement)
    }

    @Test
    fun `card game score is not created when main and replacement are absent`() {
        val team = scoringFixture()
        val game = seriesGameWithPlayers(player(33L, "other", Position.THREE))
        stubScoringInputs(team, game, replacementPolemicaUserId = 22L)

        service().calculateScores(1L)

        assertEquals(0.0, team.totalScore)
        assertEquals(emptyList<Any>(), team.cards.single().gameScores)
    }

    @Test
    fun `finalized series is rejected before points fetch`() {
        val series = Series(finalized = true).apply { id = 1L }
        `when`(seriesRepository.findById(1L)).thenReturn(Optional.of(series))

        val ex = assertThrows(ResponseStatusException::class.java) {
            service().calculateScores(1L)
        }

        assertEquals(409, ex.statusCode.value())
        verify(gamePointsService, never()).fetchPlayerStats(org.mockito.ArgumentMatchers.anyLong())
        verify(seriesRepository, never()).findByIdForUpdate(1L)
    }

    @Test
    fun `selector changed since sync is rejected before points fetch`() {
        val series = Series(finalized = false, lastSyncedSelectorChecksum = "old").apply { id = 1L }
        `when`(seriesRepository.findById(1L)).thenReturn(Optional.of(series))

        val ex = assertThrows(ResponseStatusException::class.java) {
            service().calculateScores(1L)
        }

        assertEquals(409, ex.statusCode.value())
        verify(gamePointsService, never()).fetchPlayerStats(org.mockito.ArgumentMatchers.anyLong())
    }

    @Test
    fun `series finalized during points fetch is rejected before score mutation`() {
        val initial = Series(finalized = false, lastSyncedSelectorChecksum = "f".repeat(64)).apply { id = 1L }
        val finalized = Series(finalized = true).apply { id = 1L }
        val game = seriesGameWithPlayers(player(11L, "main", Position.ONE))
        `when`(seriesRepository.findById(1L)).thenReturn(Optional.of(initial))
        `when`(seriesRepository.findByIdForUpdate(1L)).thenReturn(finalized)
        `when`(seriesGameRepository.findAllBySeries_Id(1L)).thenReturn(listOf(game))
        `when`(scoringContextFingerprintService.fingerprint(1L)).thenReturn("a".repeat(64))
        `when`(gamePointsService.fetchPlayerStats(100L)).thenReturn(
            listOf(PlayerPoints(position = 1, points = 3.0)),
        )

        val ex = assertThrows(ResponseStatusException::class.java) {
            service().calculateScores(1L)
        }

        assertEquals(409, ex.statusCode.value())
        verify(gamePointsService).fetchPlayerStats(100L)
        verify(fantasyTeamRepository, never()).findAllWithCardsForScoring(1L)
    }

    @Test
    fun `duplicate public table position is rejected as partial`() {
        val game = seriesGameWithPlayers(player(11L, "main", Position.ONE))
        stubInvalidPoints(game, listOf(PlayerPoints(1, 3.0), PlayerPoints(1, 4.0)))

        assertThrows(ResponseStatusException::class.java) { service().calculateScores(1L) }

        assertEquals("PARTIAL", game.pointsStatus)
        assertEquals(false, game.scored)
    }

    @Test
    fun `extra public table position is rejected as partial`() {
        val game = seriesGameWithPlayers(player(11L, "main", Position.ONE))
        stubInvalidPoints(game, listOf(PlayerPoints(1, 3.0), PlayerPoints(2, 4.0)))

        assertThrows(ResponseStatusException::class.java) { service().calculateScores(1L) }

        assertEquals("PARTIAL", game.pointsStatus)
        assertEquals(false, game.scored)
    }

    private fun service(): DefaultScoringService {
        org.mockito.Mockito.lenient().`when`(selectorFingerprintService.fingerprint(org.mockito.ArgumentMatchers.anyLong()))
            .thenReturn("f".repeat(64))
        return DefaultScoringService(
        seriesRepository = seriesRepository,
        seriesGameRepository = seriesGameRepository,
        fantasyTeamRepository = fantasyTeamRepository,
        seriesPlayerRepository = seriesPlayerRepository,
        fantasyPlayerAliasRepository = fantasyPlayerAliasRepository,
        perkRegistry = perkRegistry,
        objectMapper = objectMapper,
        gamePointsService = gamePointsService,
        fantasyMetrics = FantasyMetrics(SimpleMeterRegistry()),
        scoringContextFingerprintService = scoringContextFingerprintService,
        selectorFingerprintService = selectorFingerprintService,
        platformTransactionManager = NoopTransactionManager(),
        )
    }

    private fun stubScoringInputs(
        team: FantasyTeam,
        game: SeriesGame,
        replacementPolemicaUserId: Long,
        mainAliases: List<Long> = listOf(11L),
    ) {
        val series = Series(finalized = false, lastSyncedSelectorChecksum = "f".repeat(64)).apply { id = 1L }
        `when`(seriesRepository.findById(1L)).thenReturn(Optional.of(series))
        `when`(seriesRepository.findByIdForUpdate(1L)).thenReturn(series)
        `when`(seriesGameRepository.findAllBySeries_Id(1L)).thenReturn(listOf(game))
        `when`(scoringContextFingerprintService.fingerprint(1L)).thenReturn("a".repeat(64))
        `when`(gamePointsService.fetchPlayerStats(100L)).thenReturn(
            objectMapper.treeToValue(game.gameDataCache, PolemicaGame::class.java).players.orEmpty().map {
                PlayerPoints(position = it.position.value, points = mapOf(1 to 3.0, 2 to 7.0, 3 to 5.0).getValue(it.position.value))
            },
        )
        `when`(fantasyTeamRepository.findAllWithCardsForScoring(1L)).thenReturn(listOf(team))
        `when`(fantasyPlayerAliasRepository.findPolemicaUserIdsByFantasyPlayerId(11L)).thenReturn(mainAliases)
        `when`(seriesPlayerRepository.findAllBySeries_IdWithTournamentPlayers(1L)).thenReturn(
            listOf(
                SeriesPlayer(
                    series = Series().apply { id = 1L },
                    tournamentPlayer = TournamentPlayer(
                        fantasyPlayer = FantasyPlayer(id = 11L, polemicaUserId = 11L, nickname = "main"),
                    ),
                    replacementPolemicaUserId = replacementPolemicaUserId,
                ),
            ),
        )
    }

    private fun stubInvalidPoints(game: SeriesGame, points: List<PlayerPoints>) {
        val series = Series(finalized = false, lastSyncedSelectorChecksum = "f".repeat(64)).apply { id = 1L }
        `when`(seriesRepository.findById(1L)).thenReturn(Optional.of(series))
        `when`(seriesRepository.findByIdForUpdate(1L)).thenReturn(series)
        `when`(seriesGameRepository.findAllBySeries_Id(1L)).thenReturn(listOf(game))
        `when`(scoringContextFingerprintService.fingerprint(1L)).thenReturn("a".repeat(64))
        `when`(gamePointsService.fetchPlayerStats(100L)).thenReturn(points)
    }

    private fun scoringFixture(): FantasyTeam {
        val template = CardTemplate(
            fantasyPlayer = FantasyPlayer(id = 11L, polemicaUserId = 11L, nickname = "main"),
            rarity = Rarity.COMMON,
        )
        val userCard = UserCard(cardTemplate = template).apply { id = 101L }
        val team = FantasyTeam().apply { id = 201L }
        val card = FantasyTeamCard(fantasyTeam = team, userCard = userCard, slot = 1).apply { id = 301L }
        team.cards.add(card)
        return team
    }

    private fun seriesGameWithPlayers(vararg players: PolemicaPlayer): SeriesGame =
        SeriesGame(
            series = Series().apply { id = 1L },
            polemicaGameId = 100L,
            gameName = "Game",
            gameDataCache = objectMapper.valueToTree(polemicaGame(players.toList())),
        ).apply { id = 401L }

    private fun polemicaGame(players: List<PolemicaPlayer>) = PolemicaGame(
        id = 100L,
        name = "Game",
        master = 1L,
        referee = null,
        scoringVersion = null,
        scoringType = 0,
        version = 0,
        zeroVoting = null,
        tags = emptyList(),
        players = players,
        checks = emptyList(),
        shots = emptyList(),
        stage = null,
        votes = emptyList(),
        comKiller = null,
        bonuses = emptyList(),
        started = LocalDateTime.parse("2026-01-01T12:00:00"),
        stop = null,
        isLive = false,
        result = PolemicaGameResult.RED_WIN,
        num = null,
        table = null,
        phase = null,
        factor = null,
    )

    private fun player(polemicaUserId: Long, username: String, position: Position) = PolemicaPlayer(
        position = position,
        username = username,
        role = Role.PEACE,
        techs = emptyList(),
        fouls = emptyList(),
        guess = null,
        player = PolemicaUser(polemicaUserId, username),
        disqual = null,
        award = null,
    )

    private class NoopTransactionManager : PlatformTransactionManager {
        override fun getTransaction(definition: TransactionDefinition?): TransactionStatus =
            SimpleTransactionStatus()

        override fun commit(status: TransactionStatus) = Unit

        override fun rollback(status: TransactionStatus) = Unit
    }

    private companion object {
        private val objectMapper = ObjectMapper()
            .registerModule(KotlinModule.Builder().build())
            .registerModule(JavaTimeModule())
    }
}
