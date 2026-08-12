package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.entity.CardTemplate
import io.github.mralex1810.fantasy.entity.FantasyPlayer
import io.github.mralex1810.fantasy.entity.FantasyTeam
import io.github.mralex1810.fantasy.entity.FantasyTeamCard
import io.github.mralex1810.fantasy.entity.Rarity
import io.github.mralex1810.fantasy.entity.Series
import io.github.mralex1810.fantasy.entity.TelegramUser
import io.github.mralex1810.fantasy.entity.Tournament
import io.github.mralex1810.fantasy.entity.UserCard
import io.github.mralex1810.fantasy.repository.FantasyTeamCardRepository
import io.github.mralex1810.fantasy.repository.FantasyTeamRepository
import io.github.mralex1810.fantasy.repository.SeriesPlayerRepository
import io.github.mralex1810.fantasy.repository.SeriesRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.time.Instant
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class FantasyTeamRosterPruningServiceTest {

    @Mock
    private lateinit var seriesRepository: SeriesRepository

    @Mock
    private lateinit var seriesPlayerRepository: SeriesPlayerRepository

    @Mock
    private lateinit var fantasyTeamRepository: FantasyTeamRepository

    @Mock
    private lateinit var fantasyTeamCardRepository: FantasyTeamCardRepository

    @InjectMocks
    private lateinit var service: FantasyTeamRosterPruningService

    private val seriesId = 42L

    private fun fp(id: Long) = FantasyPlayer(polemicaUserId = id, nickname = "p$id").apply { this.id = id }

    private fun template(fp: FantasyPlayer) = CardTemplate(fantasyPlayer = fp, rarity = Rarity.COMMON).apply { id = fp.id }

    @Test
    fun `no-op when team deadline has passed`() {
        val series = Series(
            tournament = Tournament(name = "t"),
            name = "s",
            teamDeadline = Instant.now().minusSeconds(60),
        ).apply { id = seriesId }
        `when`(seriesRepository.findByIdForUpdate(seriesId)).thenReturn(series)

        val result = service.pruneInvalidCardsForSeries(seriesId)
        assertTrue(result.prunedCards.isEmpty())

        verify(fantasyTeamRepository, never()).findAllBySeries_IdWithCards(ArgumentMatchers.anyLong())
    }

    @Test
    fun `removes invalid middle slot and renumbers to 1 and 2`() {
        val series = Series(
            tournament = Tournament(name = "t"),
            name = "s",
            teamDeadline = Instant.now().plusSeconds(3600),
        ).apply { id = seriesId }
        `when`(seriesRepository.findByIdForUpdate(seriesId)).thenReturn(series)

        val fp1 = fp(1L)
        val fp2 = fp(2L)
        val fp3 = fp(3L)
        val uc1 = UserCard(telegramUser = TelegramUser(telegramId = 1L), cardTemplate = template(fp1), usesRemaining = 1).apply { id = 101L }
        val uc2 = UserCard(telegramUser = TelegramUser(telegramId = 1L), cardTemplate = template(fp2), usesRemaining = 1).apply { id = 102L }
        val uc3 = UserCard(telegramUser = TelegramUser(telegramId = 1L), cardTemplate = template(fp3), usesRemaining = 1).apply { id = 103L }

        val owner = TelegramUser(telegramId = 900L).apply { id = 50L }
        val team = FantasyTeam(telegramUser = owner).apply { id = 7L }
        val c1 = FantasyTeamCard(fantasyTeam = team, userCard = uc1, slot = 1)
        val c2 = FantasyTeamCard(fantasyTeam = team, userCard = uc2, slot = 2)
        val c3 = FantasyTeamCard(fantasyTeam = team, userCard = uc3, slot = 3)
        team.cards = mutableListOf(c1, c2, c3)

        `when`(fantasyTeamRepository.findAllBySeries_IdWithCards(seriesId)).thenReturn(listOf(team))
        `when`(seriesPlayerRepository.existsBySeries_IdAndTournamentPlayer_FantasyPlayer_Id(seriesId, 1L)).thenReturn(true)
        `when`(seriesPlayerRepository.existsBySeries_IdAndTournamentPlayer_FantasyPlayer_Id(seriesId, 2L)).thenReturn(false)
        `when`(seriesPlayerRepository.existsBySeries_IdAndTournamentPlayer_FantasyPlayer_Id(seriesId, 3L)).thenReturn(true)

        val result = service.pruneInvalidCardsForSeries(seriesId)
        assertEquals(1, result.prunedCards.size)
        assertEquals(900L, result.prunedCards[0].telegramChatId)
        assertEquals(2L, result.prunedCards[0].fantasyPlayerId)
        assertEquals("p2", result.prunedCards[0].playerNickname)

        verify(fantasyTeamCardRepository).delete(c2)
        verify(fantasyTeamCardRepository).saveAndFlush(c3)
        assertEquals(2, c3.slot)
        verify(fantasyTeamCardRepository, never()).saveAll(ArgumentMatchers.anyList())
        verify(fantasyTeamRepository, never()).delete(team)
    }

    @Test
    fun `removes invalid first slot and renumbers high slots without unique violation order`() {
        val series = Series(
            tournament = Tournament(name = "t"),
            name = "s",
            teamDeadline = Instant.now().plusSeconds(3600),
        ).apply { id = seriesId }
        `when`(seriesRepository.findByIdForUpdate(seriesId)).thenReturn(series)

        val fp1 = fp(1L)
        val fp2 = fp(2L)
        val fp3 = fp(3L)
        val uc1 = UserCard(telegramUser = TelegramUser(telegramId = 1L), cardTemplate = template(fp1), usesRemaining = 1).apply { id = 101L }
        val uc2 = UserCard(telegramUser = TelegramUser(telegramId = 1L), cardTemplate = template(fp2), usesRemaining = 1).apply { id = 102L }
        val uc3 = UserCard(telegramUser = TelegramUser(telegramId = 1L), cardTemplate = template(fp3), usesRemaining = 1).apply { id = 103L }

        val owner = TelegramUser(telegramId = 901L).apply { id = 51L }
        val team = FantasyTeam(telegramUser = owner).apply { id = 7L }
        val c1 = FantasyTeamCard(fantasyTeam = team, userCard = uc1, slot = 1)
        val c2 = FantasyTeamCard(fantasyTeam = team, userCard = uc2, slot = 2)
        val c3 = FantasyTeamCard(fantasyTeam = team, userCard = uc3, slot = 3)
        team.cards = mutableListOf(c1, c2, c3)

        `when`(fantasyTeamRepository.findAllBySeries_IdWithCards(seriesId)).thenReturn(listOf(team))
        `when`(seriesPlayerRepository.existsBySeries_IdAndTournamentPlayer_FantasyPlayer_Id(seriesId, 1L)).thenReturn(false)
        `when`(seriesPlayerRepository.existsBySeries_IdAndTournamentPlayer_FantasyPlayer_Id(seriesId, 2L)).thenReturn(true)
        `when`(seriesPlayerRepository.existsBySeries_IdAndTournamentPlayer_FantasyPlayer_Id(seriesId, 3L)).thenReturn(true)

        val result = service.pruneInvalidCardsForSeries(seriesId)
        assertEquals(1, result.prunedCards.size)
        assertEquals(901L, result.prunedCards[0].telegramChatId)
        assertEquals(1L, result.prunedCards[0].fantasyPlayerId)

        verify(fantasyTeamCardRepository).delete(c1)
        val ord = inOrder(fantasyTeamCardRepository)
        ord.verify(fantasyTeamCardRepository).saveAndFlush(c2)
        ord.verify(fantasyTeamCardRepository).saveAndFlush(c3)
        assertEquals(1, c2.slot)
        assertEquals(2, c3.slot)
        verify(fantasyTeamRepository, never()).delete(team)
    }

    @Test
    fun `deletes fantasy team when all cards invalid`() {
        val series = Series(
            tournament = Tournament(name = "t"),
            name = "s",
            teamDeadline = Instant.now().plusSeconds(3600),
        ).apply { id = seriesId }
        `when`(seriesRepository.findByIdForUpdate(seriesId)).thenReturn(series)

        val fp1 = fp(1L)
        val uc1 = UserCard(telegramUser = TelegramUser(telegramId = 1L), cardTemplate = template(fp1), usesRemaining = 1).apply { id = 101L }
        val owner = TelegramUser(telegramId = 902L).apply { id = 52L }
        val team = FantasyTeam(telegramUser = owner).apply { id = 7L }
        val c1 = FantasyTeamCard(fantasyTeam = team, userCard = uc1, slot = 1)
        team.cards = mutableListOf(c1)

        `when`(fantasyTeamRepository.findAllBySeries_IdWithCards(seriesId)).thenReturn(listOf(team))
        `when`(seriesPlayerRepository.existsBySeries_IdAndTournamentPlayer_FantasyPlayer_Id(seriesId, 1L)).thenReturn(false)

        val result = service.pruneInvalidCardsForSeries(seriesId)
        assertEquals(1, result.prunedCards.size)
        assertEquals(902L, result.prunedCards[0].telegramChatId)

        verify(fantasyTeamCardRepository).delete(c1)
        verify(fantasyTeamRepository).delete(team)
        verify(fantasyTeamCardRepository, never()).saveAll(ArgumentMatchers.anyList())
        verify(fantasyTeamCardRepository, never()).saveAndFlush(ArgumentMatchers.any())
    }
}
