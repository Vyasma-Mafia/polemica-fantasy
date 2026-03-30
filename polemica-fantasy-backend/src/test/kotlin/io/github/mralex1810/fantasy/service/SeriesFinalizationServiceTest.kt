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
import io.github.mralex1810.fantasy.repository.FantasyTeamRepository
import io.github.mralex1810.fantasy.repository.SeriesRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.Mockito.`when`
import org.springframework.web.server.ResponseStatusException
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class SeriesFinalizationServiceTest {

    @Mock
    private lateinit var seriesRepository: SeriesRepository

    @Mock
    private lateinit var fantasyTeamRepository: FantasyTeamRepository

    @Mock
    private lateinit var economyConfigService: EconomyConfigService

    @Mock
    private lateinit var userService: UserService

    @InjectMocks
    private lateinit var service: SeriesFinalizationService

    private fun template() = CardTemplate(
        fantasyPlayer = FantasyPlayer(polemicaUserId = 1L, nickname = "p"),
        rarity = Rarity.COMMON,
    )

    @Test
    fun `finalize throws when already finalized`() {
        val s = Series(tournament = Tournament(), finalized = true).apply { id = 1L }
        `when`(seriesRepository.findById(1L)).thenReturn(Optional.of(s))

        val ex = assertThrows(ResponseStatusException::class.java) {
            service.finalizeSeries(1L)
        }
        assertEquals(400, ex.statusCode.value())
    }

    @Test
    fun `finalize decrements uses and distributes rewards`() {
        val t = Tournament().apply { id = 1L }
        val s = Series(tournament = t, finalized = false).apply { id = 7L }
        `when`(seriesRepository.findById(7L)).thenReturn(Optional.of(s))

        val u1 = TelegramUser(telegramId = 1L).apply { id = 10L }
        val u2 = TelegramUser(telegramId = 2L).apply { id = 11L }
        val uc1 = UserCard(telegramUser = u1, cardTemplate = template(), usesRemaining = 3).apply { id = 101L }
        val uc2 = UserCard(telegramUser = u2, cardTemplate = template(), usesRemaining = 2).apply { id = 102L }
        val ft1 = FantasyTeam(telegramUser = u1, series = s, totalScore = 10.0).apply { id = 1L }
        val ft2 = FantasyTeam(telegramUser = u2, series = s, totalScore = 5.0).apply { id = 2L }
        val ftc1 = FantasyTeamCard(fantasyTeam = ft1, userCard = uc1, slot = 1).apply { id = 1L }
        val ftc2 = FantasyTeamCard(fantasyTeam = ft2, userCard = uc2, slot = 1).apply { id = 2L }
        ft1.cards.add(ftc1)
        ft2.cards.add(ftc2)

        `when`(fantasyTeamRepository.findAllWithCardsForScoring(7L)).thenReturn(listOf(ft1, ft2))
        `when`(fantasyTeamRepository.findLeaderboardForSeries(7L)).thenReturn(listOf(ft1, ft2))
        `when`(economyConfigService.getSeriesReward(1, 2)).thenReturn(100L)
        `when`(economyConfigService.getSeriesReward(2, 2)).thenReturn(70L)

        val result = service.finalizeSeries(7L)

        assertEquals(2, result.cardsDecremented)
        assertEquals(2, result.rewardsDistributed)
        assertEquals(2, uc1.usesRemaining)
        assertEquals(1, uc2.usesRemaining)
        assertEquals(true, s.finalized)
    }
}
