package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.entity.CardTemplate
import io.github.mralex1810.fantasy.entity.FantasyPlayer
import io.github.mralex1810.fantasy.entity.FantikiTransactionReason
import io.github.mralex1810.fantasy.entity.Rarity
import io.github.mralex1810.fantasy.entity.TelegramUser
import io.github.mralex1810.fantasy.entity.UserCard
import io.github.mralex1810.fantasy.repository.FantasyTeamCardRepository
import io.github.mralex1810.fantasy.repository.UserCardRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.web.server.ResponseStatusException

@ExtendWith(MockitoExtension::class)
class CardLifecycleServiceTest {

    @Mock
    private lateinit var userCardRepository: UserCardRepository

    @Mock
    private lateinit var fantasyTeamCardRepository: FantasyTeamCardRepository

    @Mock
    private lateinit var economyConfigService: EconomyConfigService

    @Mock
    private lateinit var userService: UserService

    @InjectMocks
    private lateinit var service: CardLifecycleService

    private fun commonTemplate() = CardTemplate(
        fantasyPlayer = FantasyPlayer(polemicaUserId = 1L, nickname = "t"),
        rarity = Rarity.COMMON,
    )

    @Test
    fun `recycle throws when card is in non-finalized series`() {
        val user = TelegramUser(telegramId = 1L).apply { id = 10L }
        val uc = UserCard(telegramUser = user, cardTemplate = commonTemplate(), usesRemaining = 2).apply { id = 50L }
        `when`(userCardRepository.findByIdAndTelegramUser_Id(50L, 10L)).thenReturn(uc)
        `when`(fantasyTeamCardRepository.countInNonFinalizedSeries(50L)).thenReturn(1L)

        val ex = assertThrows(ResponseStatusException::class.java) {
            service.recycleCard(user, 50L)
        }
        assertEquals(400, ex.statusCode.value())
    }

    @Test
    fun `recycle succeeds and grants fantiki`() {
        val user = TelegramUser(telegramId = 1L).apply { id = 10L }
        val uc = UserCard(telegramUser = user, cardTemplate = commonTemplate(), usesRemaining = 2).apply { id = 50L }
        `when`(userCardRepository.findByIdAndTelegramUser_Id(50L, 10L)).thenReturn(uc)
        `when`(fantasyTeamCardRepository.countInNonFinalizedSeries(50L)).thenReturn(0L)
        `when`(economyConfigService.getRecycleValue(Rarity.COMMON)).thenReturn(10L)
        `when`(userService.getBalance(10L)).thenReturn(1010L)

        val result = service.recycleCard(user, 50L)

        assertEquals(10L, result.fantikiEarned)
        assertEquals(1010L, result.newBalance)
        verify(userService).addBalance(10L, 10L, FantikiTransactionReason.CARD_RECYCLE)
        verify(userCardRepository).delete(uc)
    }

    @Test
    fun `renew throws when uses remaining positive`() {
        val user = TelegramUser(telegramId = 1L).apply { id = 10L }
        val uc = UserCard(telegramUser = user, cardTemplate = commonTemplate(), usesRemaining = 1).apply { id = 50L }
        `when`(userCardRepository.findByIdAndTelegramUser_Id(50L, 10L)).thenReturn(uc)

        val ex = assertThrows(ResponseStatusException::class.java) {
            service.renewCard(user, 50L)
        }
        assertEquals(400, ex.statusCode.value())
    }

    @Test
    fun `renew throws when max renewals reached`() {
        val user = TelegramUser(telegramId = 1L).apply { id = 10L }
        val uc = UserCard(
            telegramUser = user,
            cardTemplate = commonTemplate(),
            usesRemaining = 0,
            timesRenewed = 2,
        ).apply { id = 50L }
        `when`(userCardRepository.findByIdAndTelegramUser_Id(50L, 10L)).thenReturn(uc)
        `when`(economyConfigService.getMaxRenewals()).thenReturn(2)

        val ex = assertThrows(ResponseStatusException::class.java) {
            service.renewCard(user, 50L)
        }
        assertEquals(400, ex.statusCode.value())
    }
}
