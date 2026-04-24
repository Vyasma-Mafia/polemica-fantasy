package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.entity.Rarity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class CardValueServiceTest {

    @Mock
    private lateinit var economyConfigService: EconomyConfigService

    @InjectMocks
    private lateinit var cardValueService: CardValueService

    @Test
    fun `calculateValue for rarity and achievement count follows base plus bonus`() {
        val bonus = 10L
        whenever(economyConfigService.getCardAchievementBonus()).thenReturn(bonus)
        whenever(economyConfigService.getCardBaseValue(Rarity.COMMON)).thenReturn(25L)
        whenever(economyConfigService.getCardBaseValue(Rarity.RARE)).thenReturn(40L)
        whenever(economyConfigService.getCardBaseValue(Rarity.EPIC)).thenReturn(80L)
        whenever(economyConfigService.getCardBaseValue(Rarity.LEGENDARY)).thenReturn(370L)

        assertEquals(25L, cardValueService.calculateValue(Rarity.COMMON, 0))
        assertEquals(25L + 2 * bonus, cardValueService.calculateValue(Rarity.COMMON, 2))
        assertEquals(40L + 1 * bonus, cardValueService.calculateValue(Rarity.RARE, 1))
        assertEquals(80L + 2 * bonus, cardValueService.calculateValue(Rarity.EPIC, 2))
        assertEquals(370L + 3 * bonus, cardValueService.calculateValue(Rarity.LEGENDARY, 3))
    }
}
