package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.entity.CardTemplate
import io.github.mralex1810.fantasy.entity.FantikiTransactionReason
import io.github.mralex1810.fantasy.entity.FantasyPlayer
import io.github.mralex1810.fantasy.entity.FantasyTeam
import io.github.mralex1810.fantasy.entity.FantasyTeamCard
import io.github.mralex1810.fantasy.entity.League
import io.github.mralex1810.fantasy.entity.LeagueType
import io.github.mralex1810.fantasy.entity.Rarity
import io.github.mralex1810.fantasy.entity.Series
import io.github.mralex1810.fantasy.entity.SeriesLeague
import io.github.mralex1810.fantasy.entity.SeriesStatus
import io.github.mralex1810.fantasy.entity.TelegramUser
import io.github.mralex1810.fantasy.entity.Tournament
import io.github.mralex1810.fantasy.entity.UserCard
import io.github.mralex1810.fantasy.event.SeriesFinalizedNotificationEvent
import io.github.mralex1810.fantasy.repository.FantasyTeamRepository
import io.github.mralex1810.fantasy.repository.SeriesLeagueRepository
import io.github.mralex1810.fantasy.repository.SeriesRepository
import io.github.mralex1810.fantasy.repository.UserCardRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.Mockito.verify
import org.mockito.Mockito.times
import org.mockito.Mockito.`when`
import org.springframework.context.ApplicationEventPublisher
import org.springframework.web.server.ResponseStatusException
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class SeriesFinalizationServiceTest {

    @Mock
    private lateinit var seriesRepository: SeriesRepository

    @Mock
    private lateinit var fantasyTeamRepository: FantasyTeamRepository

    @Mock
    private lateinit var userCardRepository: UserCardRepository

    @Mock
    private lateinit var seriesLeagueRepository: SeriesLeagueRepository

    @Mock
    private lateinit var economyConfigService: EconomyConfigService

    @Mock
    private lateinit var leagueService: LeagueService

    @Mock
    private lateinit var userService: UserService

    @Mock
    private lateinit var applicationEventPublisher: ApplicationEventPublisher

    @Mock
    private lateinit var seriesCompletionService: SeriesCompletionService

    @InjectMocks
    private lateinit var service: SeriesFinalizationService

    private fun template() = CardTemplate(
        fantasyPlayer = FantasyPlayer(polemicaUserId = 1L, nickname = "p"),
        rarity = Rarity.COMMON,
    )

    @Test
    fun `finalize throws when already finalized`() {
        val s = Series(tournament = Tournament(), finalized = true).apply { id = 1L }
        `when`(seriesRepository.findByIdForUpdate(1L)).thenReturn(s)

        val ex = assertThrows(ResponseStatusException::class.java) {
            service.finalizeSeries(1L)
        }
        assertEquals(409, ex.statusCode.value())
    }

    @Test
    fun `finalize decrements uses and distributes rewards`() {
        val t = Tournament().apply { id = 1L }
        val s = Series(tournament = t, finalized = false).apply { id = 7L }
        `when`(seriesRepository.findByIdForUpdate(7L)).thenReturn(s)
        `when`(seriesCompletionService.evaluateForFinalization(7L)).thenReturn(
            SeriesCompletion(SeriesCompletionStatus.READY, "a".repeat(64)),
        )

        val u1 = TelegramUser(telegramId = 1L).apply { id = 10L }
        val u2 = TelegramUser(telegramId = 2L).apply { id = 11L }
        val uc1 = UserCard(telegramUser = u1, cardTemplate = template(), usesRemaining = 3).apply { id = 101L }
        val uc2 = UserCard(telegramUser = u2, cardTemplate = template(), usesRemaining = 2).apply { id = 102L }
        val league = League(code = "MAIN", name = "Main", leagueType = LeagueType.SYSTEM).apply { id = 1L }
        val seriesLeague = SeriesLeague(series = s, league = league, enabled = true).apply { id = 21L }
        val ft1 = FantasyTeam(telegramUser = u1, series = s, seriesLeague = seriesLeague, totalScore = 10.0).apply { id = 1L }
        val ft2 = FantasyTeam(telegramUser = u2, series = s, seriesLeague = seriesLeague, totalScore = 5.0).apply { id = 2L }
        val ftc1 = FantasyTeamCard(fantasyTeam = ft1, userCard = uc1, slot = 1).apply { id = 1L }
        val ftc2 = FantasyTeamCard(fantasyTeam = ft2, userCard = uc2, slot = 1).apply { id = 2L }
        ft1.cards.add(ftc1)
        ft2.cards.add(ftc2)

        `when`(fantasyTeamRepository.findAllWithCardsForScoring(7L)).thenReturn(listOf(ft1, ft2))
        `when`(userCardRepository.findAllByIdInForUpdate(setOf(101L, 102L))).thenReturn(listOf(uc1, uc2))
        `when`(seriesLeagueRepository.findById(21L)).thenReturn(Optional.of(seriesLeague))
        `when`(fantasyTeamRepository.findLeaderboardForSeriesLeague(21L)).thenReturn(listOf(ft1, ft2))
        `when`(leagueService.getEffectiveRewardScale(seriesLeague)).thenReturn(50)
        `when`(economyConfigService.getSeriesReward(1, 2)).thenReturn(100L)
        `when`(economyConfigService.getSeriesReward(2, 2)).thenReturn(70L)
        `when`(userService.getBalance(10L)).thenReturn(134L)
        `when`(userService.getBalance(11L)).thenReturn(124L)

        val result = service.finalizeSeries(7L)

        assertEquals(2, result.cardsDecremented)
        assertEquals(2, result.rewardsDistributed)
        assertEquals(2, uc1.usesRemaining)
        assertEquals(1, uc2.usesRemaining)
        assertEquals(true, s.finalized)
        assertEquals(SeriesStatus.FINISHED, s.status)
        verify(userService).addBalance(10L, 17L, FantikiTransactionReason.SERIES_REWARD)
        verify(userService).addBalance(11L, 12L, FantikiTransactionReason.SERIES_REWARD)
        val captor = ArgumentCaptor.forClass(Any::class.java)
        verify(applicationEventPublisher, times(2)).publishEvent(captor.capture())
        val event = captor.allValues.filterIsInstance<SeriesFinalizedNotificationEvent>().single()
        assertEquals(7L, event.seriesId)
        assertEquals(2, event.rewardedUsers)
        assertEquals(2, event.cardUsesDecremented)
        assertEquals(2, event.recipients.size)
        assertEquals(1, event.recipients[0].leagueResults.first().place)
        assertEquals(2, event.recipients[1].leagueResults.first().place)
        assertEquals(17L, event.recipients[0].totalReward)
        assertEquals(12L, event.recipients[1].totalReward)
        assertEquals(134L, event.recipients[0].balanceAfter)
        assertEquals(124L, event.recipients[1].balanceAfter)
    }

    @Test
    fun `finalize throws conflict when card has fewer uses than league slots`() {
        val t = Tournament().apply { id = 1L }
        val s = Series(tournament = t, finalized = false).apply { id = 8L }
        `when`(seriesRepository.findByIdForUpdate(8L)).thenReturn(s)
        `when`(seriesCompletionService.evaluateForFinalization(8L)).thenReturn(
            SeriesCompletion(SeriesCompletionStatus.READY, "b".repeat(64)),
        )

        val user = TelegramUser(telegramId = 1L).apply { id = 10L }
        val uc = UserCard(telegramUser = user, cardTemplate = template(), usesRemaining = 1).apply { id = 101L }
        val mainLeague = League(code = "MAIN", name = "Main", leagueType = LeagueType.SYSTEM).apply { id = 1L }
        val budgetLeague = League(code = "BUDGET", name = "Budget", leagueType = LeagueType.SYSTEM).apply { id = 2L }
        val mainSeriesLeague = SeriesLeague(series = s, league = mainLeague, enabled = true).apply { id = 21L }
        val budgetSeriesLeague = SeriesLeague(series = s, league = budgetLeague, enabled = true).apply { id = 22L }
        val mainTeam = FantasyTeam(telegramUser = user, series = s, seriesLeague = mainSeriesLeague).apply { id = 1L }
        val budgetTeam = FantasyTeam(telegramUser = user, series = s, seriesLeague = budgetSeriesLeague).apply { id = 2L }
        mainTeam.cards.add(FantasyTeamCard(fantasyTeam = mainTeam, userCard = uc, slot = 1).apply { id = 1L })
        budgetTeam.cards.add(FantasyTeamCard(fantasyTeam = budgetTeam, userCard = uc, slot = 1).apply { id = 2L })

        `when`(fantasyTeamRepository.findAllWithCardsForScoring(8L)).thenReturn(listOf(mainTeam, budgetTeam))
        `when`(userCardRepository.findAllByIdInForUpdate(setOf(101L))).thenReturn(listOf(uc))

        val ex = assertThrows(ResponseStatusException::class.java) {
            service.finalizeSeries(8L)
        }

        assertEquals(409, ex.statusCode.value())
        assertEquals(1, uc.usesRemaining)
        assertEquals(false, s.finalized)
    }

    @Test
    fun `scale reward full roster`() {
        assertEquals(100L, service.scaleSeriesRewardByRosterSize(100L, 3))
    }

    @Test
    fun `scale reward two cards rounds up two thirds`() {
        assertEquals(67L, service.scaleSeriesRewardByRosterSize(100L, 2))
        assertEquals(7L, service.scaleSeriesRewardByRosterSize(10L, 2))
    }

    @Test
    fun `scale reward one card rounds up one third`() {
        assertEquals(34L, service.scaleSeriesRewardByRosterSize(100L, 1))
        assertEquals(4L, service.scaleSeriesRewardByRosterSize(10L, 1))
    }

    @Test
    fun `scale reward zero cards or base yields zero`() {
        assertEquals(0L, service.scaleSeriesRewardByRosterSize(100L, 0))
        assertEquals(0L, service.scaleSeriesRewardByRosterSize(0L, 2))
    }
}
