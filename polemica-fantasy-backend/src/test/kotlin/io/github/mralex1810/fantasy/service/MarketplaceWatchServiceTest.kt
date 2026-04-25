package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.dto.user.request.CreateMarketplaceWatchRequest
import io.github.mralex1810.fantasy.entity.FantasyPlayer
import io.github.mralex1810.fantasy.entity.MarketplaceWatchFilter
import io.github.mralex1810.fantasy.entity.Rarity
import io.github.mralex1810.fantasy.entity.TelegramUser
import io.github.mralex1810.fantasy.entity.Tournament
import io.github.mralex1810.fantasy.repository.FantasyPlayerRepository
import io.github.mralex1810.fantasy.repository.MarketplaceWatchFilterRepository
import io.github.mralex1810.fantasy.repository.TelegramUserRepository
import io.github.mralex1810.fantasy.repository.TournamentRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class MarketplaceWatchServiceTest {
    @Mock
    private lateinit var marketplaceWatchFilterRepository: MarketplaceWatchFilterRepository

    @Mock
    private lateinit var telegramUserRepository: TelegramUserRepository

    @Mock
    private lateinit var fantasyPlayerRepository: FantasyPlayerRepository

    @Mock
    private lateinit var tournamentRepository: TournamentRepository

    @InjectMocks
    private lateinit var marketplaceWatchService: MarketplaceWatchService

    @Test
    fun `createWatch validates at least one core criterion`() {
        whenever(marketplaceWatchFilterRepository.countByTelegramUser_Id(1L)).thenReturn(0)

        val ex = assertThrows(ResponseStatusException::class.java) {
            marketplaceWatchService.createWatch(
                internalUserId = 1L,
                request = CreateMarketplaceWatchRequest(
                    fantasyPlayerId = null,
                    tournamentId = null,
                    rarity = null,
                    maxPrice = 300L,
                ),
            )
        }

        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
        assertEquals("At least one filter criterion required", ex.reason)
    }

    @Test
    fun `createWatch saves filter and returns dto`() {
        val user = TelegramUser(telegramId = 1001L).apply { id = 1L }
        val player = FantasyPlayer(polemicaUserId = 11L, nickname = "Игрок").apply { id = 10L }
        val tournament = Tournament(name = "Турнир").apply { id = 20L }
        val createdAt = Instant.parse("2026-04-25T10:15:30Z")
        val saved = MarketplaceWatchFilter(
            id = 99L,
            telegramUser = user,
            fantasyPlayer = player,
            tournament = tournament,
            rarity = Rarity.EPIC,
            maxPrice = 777L,
            createdAt = createdAt,
        )
        whenever(marketplaceWatchFilterRepository.countByTelegramUser_Id(1L)).thenReturn(0)
        whenever(telegramUserRepository.findById(1L)).thenReturn(Optional.of(user))
        whenever(fantasyPlayerRepository.findById(10L)).thenReturn(Optional.of(player))
        whenever(tournamentRepository.findById(20L)).thenReturn(Optional.of(tournament))
        whenever(marketplaceWatchFilterRepository.save(org.mockito.kotlin.any())).thenReturn(saved)

        val dto = marketplaceWatchService.createWatch(
            internalUserId = 1L,
            request = CreateMarketplaceWatchRequest(
                fantasyPlayerId = 10L,
                tournamentId = 20L,
                rarity = Rarity.EPIC,
                maxPrice = 777L,
            ),
        )

        assertEquals(99L, dto.id)
        assertEquals("Игрок", dto.fantasyPlayer?.nickname)
        assertEquals("Турнир", dto.tournament?.name)
        assertEquals("EPIC", dto.rarity)
        assertEquals(777L, dto.maxPrice)
        assertEquals(createdAt, dto.createdAt)
    }

    @Test
    fun `deleteWatch throws not found when filter does not belong to user`() {
        whenever(marketplaceWatchFilterRepository.deleteByIdAndTelegramUser_Id(77L, 1L)).thenReturn(0)

        val ex = assertThrows(ResponseStatusException::class.java) {
            marketplaceWatchService.deleteWatch(1L, 77L)
        }

        assertEquals(HttpStatus.NOT_FOUND, ex.statusCode)
        verify(marketplaceWatchFilterRepository).deleteByIdAndTelegramUser_Id(77L, 1L)
    }
}
