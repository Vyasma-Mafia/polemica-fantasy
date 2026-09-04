package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.entity.FantasyPlayer
import io.github.mralex1810.fantasy.entity.FantasyPlayerImportBlock
import io.github.mralex1810.fantasy.repository.FantasyPlayerAliasRepository
import io.github.mralex1810.fantasy.repository.FantasyPlayerImportBlockRepository
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

@ExtendWith(MockitoExtension::class)
class FantasyPlayerImportPolicyServiceTest {
    @Mock
    private lateinit var importBlockRepository: FantasyPlayerImportBlockRepository

    @Mock
    private lateinit var fantasyPlayerAliasRepository: FantasyPlayerAliasRepository

    private lateinit var service: FantasyPlayerImportPolicyService

    @BeforeEach
    fun setUp() {
        service = FantasyPlayerImportPolicyService(importBlockRepository, fantasyPlayerAliasRepository)
    }

    @Test
    fun `rejects a directly blocked Polemica id`() {
        whenever(importBlockRepository.findFirstByPolemicaUserIdIn(any()))
            .thenReturn(FantasyPlayerImportBlock(polemicaUserId = 41582))

        val error = assertThrows(ResponseStatusException::class.java) {
            service.requireImportAllowed(41582)
        }

        assertEquals(HttpStatus.CONFLICT, error.statusCode)
        assertEquals("Polemica user 41582 is blocked from Fantasy imports", error.reason)
    }

    @Test
    fun `rejects an existing fantasy player with a blocked alias`() {
        val player = FantasyPlayer(id = 10, polemicaUserId = 64731, nickname = "Current nickname")
        whenever(fantasyPlayerAliasRepository.findPolemicaUserIdsByFantasyPlayerId(10))
            .thenReturn(listOf(64731, 41582))
        whenever(importBlockRepository.findFirstByPolemicaUserIdIn(any()))
            .thenReturn(FantasyPlayerImportBlock(polemicaUserId = 41582))

        val error = assertThrows(ResponseStatusException::class.java) {
            service.requireImportAllowed(player)
        }

        assertEquals(HttpStatus.CONFLICT, error.statusCode)
        assertEquals("Polemica user 41582 is blocked from Fantasy imports", error.reason)
    }

    @Test
    fun `allows a player when neither primary id nor aliases are blocked`() {
        val player = FantasyPlayer(id = 10, polemicaUserId = 64731, nickname = "Allowed player")
        whenever(fantasyPlayerAliasRepository.findPolemicaUserIdsByFantasyPlayerId(10))
            .thenReturn(listOf(64731, 56102))
        whenever(importBlockRepository.findFirstByPolemicaUserIdIn(any())).thenReturn(null)

        assertDoesNotThrow {
            service.requireImportAllowed(player)
        }
    }
}
