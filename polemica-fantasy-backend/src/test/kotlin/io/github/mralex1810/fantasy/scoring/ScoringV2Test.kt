package io.github.mralex1810.fantasy.scoring

import com.github.mafia.vyasma.polemica.library.model.game.PolemicaPlayer
import com.github.mafia.vyasma.polemica.library.model.game.PolemicaUser
import com.github.mafia.vyasma.polemica.library.model.game.Position
import com.github.mafia.vyasma.polemica.library.model.game.Role
import io.github.mralex1810.fantasy.entity.Achievement
import io.github.mralex1810.fantasy.entity.AchievementApplicableRole
import io.github.mralex1810.fantasy.entity.OccurrenceType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScoringV2Test {

    @Test
    fun `card game total uses rarity modifier`() {
        assertEquals(12.65, cardGameTotalScore(5.0, 6.0, 1.15), 1e-9)
        assertEquals(10.0, cardGameTotalScore(4.0, 6.0, 1.0), 1e-9)
    }

    @Test
    fun `ONCE_PER_GAME caps match count at 1`() {
        assertEquals(1, appliedOccurrences(5, OccurrenceType.ONCE_PER_GAME))
        assertEquals(1, appliedOccurrences(1, OccurrenceType.ONCE_PER_GAME))
        assertEquals(0, appliedOccurrences(0, OccurrenceType.ONCE_PER_GAME))
    }

    @Test
    fun `MULTIPLE_PER_GAME uses raw count`() {
        assertEquals(3, appliedOccurrences(3, OccurrenceType.MULTIPLE_PER_GAME))
        assertEquals(0, appliedOccurrences(0, OccurrenceType.MULTIPLE_PER_GAME))
    }

    @Test
    fun `role filter rejects when role not in applicable set`() {
        val achievement = Achievement().apply {
            id = "X"
            applicableRoles.add(roleRow("SHERIFF"))
        }
        val peacePlayer = polemicaPlayer(Role.PEACE)
        assertFalse(isRoleApplicable(achievement, peacePlayer))
    }

    @Test
    fun `role filter accepts matching role`() {
        val achievement = Achievement().apply {
            id = "X"
            applicableRoles.add(roleRow("PEACE"))
        }
        val peacePlayer = polemicaPlayer(Role.PEACE)
        assertTrue(isRoleApplicable(achievement, peacePlayer))
    }

    @Test
    fun `empty applicable roles never matches`() {
        val achievement = Achievement().apply { id = "X" }
        assertFalse(isRoleApplicable(achievement, polemicaPlayer(Role.PEACE)))
    }

    private fun roleRow(role: String) = AchievementApplicableRole().apply {
        achievementId = "X"
        this.role = role
    }

    private fun polemicaPlayer(role: Role) = PolemicaPlayer(
        Position.ONE,
        "n",
        role,
        techs = emptyList(),
        fouls = emptyList(),
        guess = null,
        player = PolemicaUser(1L, "u"),
        disqual = null,
        award = null,
    )
}
