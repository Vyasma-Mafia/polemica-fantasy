package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.dto.admin.request.CardPackRarityConfigDto
import io.github.mralex1810.fantasy.entity.Rarity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

class CardPackServiceProbabilityTest {

    @Test
    fun `validateProbabilities accepts sum 1`() {
        CardPackService.validateProbabilities(
            listOf(
                CardPackRarityConfigDto(Rarity.COMMON, 0.5, 1),
                CardPackRarityConfigDto(Rarity.RARE, 0.5, 1),
            ),
        )
    }

    @Test
    fun `validateProbabilities rejects wrong sum`() {
        val ex = assertThrows<ResponseStatusException> {
            CardPackService.validateProbabilities(
                listOf(
                    CardPackRarityConfigDto(Rarity.COMMON, 0.5, 1),
                    CardPackRarityConfigDto(Rarity.RARE, 0.4, 1),
                ),
            )
        }
        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
    }
}
