package io.github.mralex1810.fantasy.polemica

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.github.mafia.vyasma.polemica.library.client.PolemicaClient
import com.github.mafia.vyasma.polemica.library.model.game.PolemicaGame
import io.github.mralex1810.fantasy.config.PolemicaProperties
import io.github.mralex1810.fantasy.entity.FantasyPlayer
import io.github.mralex1810.fantasy.entity.Series
import io.github.mralex1810.fantasy.entity.SeriesGame
import io.github.mralex1810.fantasy.entity.SeriesPlayer
import io.github.mralex1810.fantasy.entity.Tournament
import io.github.mralex1810.fantasy.entity.TournamentKind
import io.github.mralex1810.fantasy.entity.TournamentPlayer
import io.github.mralex1810.fantasy.repository.FantasyPlayerAliasRepository
import io.github.mralex1810.fantasy.repository.SeriesGameRepository
import io.github.mralex1810.fantasy.repository.SeriesPlayerRepository
import io.github.mralex1810.fantasy.repository.SeriesRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.SimpleTransactionStatus
import java.time.LocalDateTime
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class DefaultGameSyncServiceTest {

    @Mock
    private lateinit var integration: PolemicaIntegrationService

    @Mock
    private lateinit var seriesRepository: SeriesRepository

    @Mock
    private lateinit var seriesPlayerRepository: SeriesPlayerRepository

    @Mock
    private lateinit var seriesGameRepository: SeriesGameRepository

    @Mock
    private lateinit var fantasyPlayerAliasRepository: FantasyPlayerAliasRepository

    @Test
    fun `standalone sync uses secondary aliases for internal player overlap`() {
        val series = standaloneSeries()
        `when`(seriesRepository.findById(1L)).thenReturn(Optional.of(series))
        `when`(seriesPlayerRepository.findAllBySeries_Id(1L)).thenReturn(
            listOf(seriesPlayer(1L, 11L), seriesPlayer(2L, 21L)),
        )
        `when`(fantasyPlayerAliasRepository.findPolemicaUserIdsByFantasyPlayerId(1L)).thenReturn(listOf(11L, 12L))
        `when`(fantasyPlayerAliasRepository.findPolemicaUserIdsByFantasyPlayerId(2L)).thenReturn(listOf(21L))
        `when`(integration.fetchRecentProfileRowsForSync(11L)).thenReturn(emptyList())
        `when`(integration.fetchRecentProfileRowsForSync(12L)).thenReturn(listOf(profileRow(100L)))
        `when`(integration.fetchRecentProfileRowsForSync(21L)).thenReturn(listOf(profileRow(100L)))
        val game = polemicaGame(100L, "Alias Cup 1")
        `when`(integration.loadMatch(100L)).thenReturn(game)
        `when`(integration.toJsonNode(game)).thenReturn(objectMapper.createObjectNode())
        `when`(seriesGameRepository.findBySeries_IdAndPolemicaGameId(1L, 100L)).thenReturn(null)

        service().syncGames(1L)

        verify(seriesGameRepository).save(anyNotNull())
    }

    @Test
    fun `standalone sync counts aliases as one internal player in overlap`() {
        val series = standaloneSeries()
        `when`(seriesRepository.findById(1L)).thenReturn(Optional.of(series))
        `when`(seriesPlayerRepository.findAllBySeries_Id(1L)).thenReturn(
            listOf(seriesPlayer(1L, 11L), seriesPlayer(2L, 21L)),
        )
        `when`(fantasyPlayerAliasRepository.findPolemicaUserIdsByFantasyPlayerId(1L)).thenReturn(listOf(11L, 12L))
        `when`(fantasyPlayerAliasRepository.findPolemicaUserIdsByFantasyPlayerId(2L)).thenReturn(listOf(21L))
        `when`(integration.fetchRecentProfileRowsForSync(11L)).thenReturn(listOf(profileRow(100L)))
        `when`(integration.fetchRecentProfileRowsForSync(12L)).thenReturn(listOf(profileRow(100L)))
        `when`(integration.fetchRecentProfileRowsForSync(21L)).thenReturn(emptyList())

        service().syncGames(1L)

        verify(integration, never()).loadMatch(anyLong())
        verify(seriesGameRepository, never()).save(anyNotNull())
    }

    private fun service() = DefaultGameSyncService(
        polemicaProperties = PolemicaProperties(username = "u", password = "p"),
        integration = integration,
        seriesRepository = seriesRepository,
        seriesPlayerRepository = seriesPlayerRepository,
        seriesGameRepository = seriesGameRepository,
        fantasyPlayerAliasRepository = fantasyPlayerAliasRepository,
        platformTransactionManager = NoopTransactionManager(),
    )

    private fun standaloneSeries() =
        Series(
            tournament = Tournament(kind = TournamentKind.STANDALONE),
            namePrefix = "Alias Cup",
        ).apply { id = 1L }

    private fun seriesPlayer(fantasyPlayerId: Long, polemicaUserId: Long) =
        SeriesPlayer(
            series = Series().apply { id = 1L },
            tournamentPlayer = TournamentPlayer(
                fantasyPlayer = FantasyPlayer(
                    id = fantasyPlayerId,
                    polemicaUserId = polemicaUserId,
                    nickname = "p$fantasyPlayerId",
                ),
            ),
        )

    private fun profileRow(matchId: Long) =
        PolemicaClient.ProfileGameRow(
            id = matchId,
            type = null,
            gameMode = null,
            dateStart = null,
            dateEnds = null,
            duration = null,
            points = null,
            sp = null,
            role = null,
            result = null,
            mmr = null,
        )

    private fun polemicaGame(id: Long, name: String) =
        PolemicaGame(
            id = id,
            name = name,
            master = 1L,
            referee = null,
            scoringVersion = null,
            scoringType = 0,
            version = 0,
            zeroVoting = null,
            tags = emptyList(),
            players = emptyList(),
            checks = emptyList(),
            shots = emptyList(),
            stage = null,
            votes = emptyList(),
            comKiller = null,
            bonuses = emptyList(),
            started = LocalDateTime.of(2026, 1, 1, 12, 0),
            stop = null,
            isLive = false,
            result = null,
            table = 1,
            num = 1,
            phase = 0,
            factor = null,
        )

    private class NoopTransactionManager : PlatformTransactionManager {
        override fun getTransaction(definition: TransactionDefinition?): TransactionStatus = SimpleTransactionStatus()
        override fun commit(status: TransactionStatus) = Unit
        override fun rollback(status: TransactionStatus) = Unit
    }

    private companion object {
        val objectMapper: ObjectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())

        private fun <T> anyNotNull(): T {
            org.mockito.ArgumentMatchers.any<T>()
            @Suppress("UNCHECKED_CAST")
            return null as T
        }
    }
}
