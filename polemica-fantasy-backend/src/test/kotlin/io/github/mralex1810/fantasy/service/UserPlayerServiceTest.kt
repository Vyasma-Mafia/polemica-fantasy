package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.controller.user.PlayerController
import io.github.mralex1810.fantasy.entity.FantasyPlayer
import io.github.mralex1810.fantasy.repository.FantasyPlayerRepository
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.util.Optional

class UserPlayerServiceTest {
    private val repository = mock<FantasyPlayerRepository>()
    private val mvc = MockMvcBuilders.standaloneSetup(PlayerController(UserPlayerService(repository))).build()

    @Test
    fun `public identity resolves fantasy ID not telegram ID`() {
        whenever(repository.findById(33)).thenReturn(Optional.of(FantasyPlayer(
            id = 33, polemicaUserId = 700001, nickname = "Alias", photoUrl = "https://example.com/photo",
        )))
        mvc.perform(get("/api/v1/players/33"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.fantasyPlayerId").value(33))
            .andExpect(jsonPath("$.polemicaUserId").value(700001))
            .andExpect(jsonPath("$.playerNickname").value("Alias"))
            .andExpect(jsonPath("$.playerPhotoUrl").value("https://example.com/photo"))
            .andExpect(jsonPath("$.telegramId").doesNotExist())
            .andExpect(jsonPath("$.aliases").doesNotExist())
    }

    @Test
    fun `unknown player is 404`() {
        whenever(repository.findById(33)).thenReturn(Optional.empty())
        mvc.perform(get("/api/v1/players/33")).andExpect(status().isNotFound)
    }

    @Test
    fun `nonpositive and malformed IDs are 400 without querying repository`() {
        listOf("0", "-1", "text").forEach {
            mvc.perform(get("/api/v1/players/$it")).andExpect(status().isBadRequest)
        }
        verifyNoInteractions(repository)
    }
}
