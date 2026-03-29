package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.dto.admin.request.CardPackRarityConfigDto
import io.github.mralex1810.fantasy.entity.Rarity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

class CardPackRarityConfigValidationTest {

    @Test
    fun `validateRarityConfigs accepts distinct rarities with positive counts`() {
        CardPackService.validateRarityConfigs(
            listOf(
                CardPackRarityConfigDto(Rarity.COMMON, 1),
                CardPackRarityConfigDto(Rarity.RARE, 1),
            ),
        )
    }

    @Test
    fun `validateRarityConfigs rejects duplicate rarity`() {
        val ex = assertThrows<ResponseStatusException> {
            CardPackService.validateRarityConfigs(
                listOf(
                    CardPackRarityConfigDto(Rarity.COMMON, 1),
                    CardPackRarityConfigDto(Rarity.COMMON, 1),
                ),
            )
        }
        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
    }

    @Test
    fun `validateRarityConfigs rejects empty list`() {
        val ex = assertThrows<ResponseStatusException> {
            CardPackService.validateRarityConfigs(emptyList())
        }
        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
    }

    @Test
    fun `validateRarityConfigs rejects non positive cardsCount`() {
        val ex = assertThrows<ResponseStatusException> {
            CardPackService.validateRarityConfigs(
                listOf(CardPackRarityConfigDto(Rarity.COMMON, 0)),
            )
        }
        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
    }

    @Test
    fun `validateRarityConfigs rejects LEGENDARY`() {
        val ex = assertThrows<ResponseStatusException> {
            CardPackService.validateRarityConfigs(
                listOf(CardPackRarityConfigDto(Rarity.LEGENDARY, 1)),
            )
        }
        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
    }
}
