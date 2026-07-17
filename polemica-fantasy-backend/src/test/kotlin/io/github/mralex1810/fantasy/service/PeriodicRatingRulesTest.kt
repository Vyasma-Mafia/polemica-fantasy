package io.github.mralex1810.fantasy.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

class PeriodicRatingRulesTest {
    private val start = Instant.parse("2026-07-06T21:00:00Z")
    private val end = Instant.parse("2026-07-20T21:00:00Z")

    @Test
    fun `period boundary is start inclusive and end exclusive`() {
        assertTrue(PeriodicRatingRules.contains(start, end, start))
        assertTrue(PeriodicRatingRules.contains(start, end, end.minusMillis(1)))
        assertFalse(PeriodicRatingRules.contains(start, end, end))
    }

    @Test
    fun `touching periods do not overlap`() {
        assertFalse(PeriodicRatingRules.overlaps(start, end, end, end.plusSeconds(3600)))
        assertTrue(PeriodicRatingRules.overlaps(start, end, end.minusSeconds(1), end.plusSeconds(3600)))
    }

    @Test
    fun `double values sum before one final rounding and retain zero and negative scores`() {
        assertEquals(BigDecimal("0.30"), PeriodicRatingRules.roundedSum(listOf(0.1, 0.2, 0.0)))
        assertEquals(BigDecimal("-0.01"), PeriodicRatingRules.roundedSum(listOf(-0.006, 0.0)))
    }

    @Test
    fun `competition rank skips positions after ties`() {
        assertEquals(
            listOf(1, 1, 3, 4),
            PeriodicRatingRules.competitionRanks(listOf(BigDecimal("10.00"), BigDecimal("10.00"), BigDecimal("9.99"), BigDecimal("0.00"))),
        )
    }

    @Test
    fun `unfinished and missing effective date block unless explicitly excluded`() {
        assertTrue(PeriodicRatingRules.isBlocker(false, start, true))
        assertTrue(PeriodicRatingRules.isBlocker(true, null, true))
        assertFalse(PeriodicRatingRules.isBlocker(false, start, false))
        assertFalse(PeriodicRatingRules.isBlocker(true, start, true))
    }

    @Test
    fun `top ten ladder freezes rarity tier skins and fantiki`() {
        assertEquals("EPIC", PeriodicRatingRules.rewardRarity(1))
        assertEquals("RARE", PeriodicRatingRules.rewardRarity(5))
        assertEquals("COMMON", PeriodicRatingRules.rewardRarity(10))
        assertEquals(0L, PeriodicRatingRules.rewardFantiki(5))
        assertEquals(50L, PeriodicRatingRules.rewardFantiki(6))
        assertEquals(
            listOf(
                "rating_bronze_edition_aurora",
                "rating_bronze_edition_crimson",
                "rating_bronze_edition_nocturne",
            ),
            PeriodicRatingRules.rewardSkinCodes(3),
        )
        assertFalse(PeriodicRatingRules.isRewarded(11))
    }
}
