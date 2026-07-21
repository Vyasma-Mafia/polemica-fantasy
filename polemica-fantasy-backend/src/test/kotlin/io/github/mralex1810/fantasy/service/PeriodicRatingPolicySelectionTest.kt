package io.github.mralex1810.fantasy.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PeriodicRatingPolicySelectionTest {

    @Test
    fun `adjacent finalist rewards receive different player options`() {
        val players = (1L..8L).toList()

        val first = circularTake(players, offset = 0, count = 3)
        val second = circularTake(players, offset = 3, count = 3)

        assertThat(first).containsExactly(1L, 2L, 3L)
        assertThat(second).containsExactly(4L, 5L, 6L)
        assertThat(first).doesNotContainAnyElementsOf(second)
    }

    @Test
    fun `player options wrap when finalist count exceeds active pool`() {
        assertThat(circularTake(listOf(10L, 20L, 30L, 40L), offset = 3, count = 3))
            .containsExactly(40L, 10L, 20L)
    }
}
