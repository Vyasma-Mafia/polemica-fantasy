package io.github.mralex1810.fantasy.scoring

import com.github.mafia.vyasma.polemica.library.model.game.PolemicaGame
import io.github.mralex1810.fantasy.entity.Achievement
import io.github.mralex1810.fantasy.entity.OccurrenceType
import io.github.mralex1810.fantasy.scoring.achievement.toApplicableRoleKey
import com.github.mafia.vyasma.polemica.library.model.game.PolemicaPlayer

/** Only games with a settled result (red/black win) are scored; live or unfinished matches have null [PolemicaGame.getResult]. */
internal fun PolemicaGame.isFinishedForScoring(): Boolean = result != null

internal fun appliedOccurrences(raw: Int, occurrenceType: OccurrenceType): Int =
    when (occurrenceType) {
        OccurrenceType.ONCE_PER_GAME -> minOf(1, raw)
        OccurrenceType.MULTIPLE_PER_GAME -> raw
    }

/** Strict: empty applicable roles ⇒ never matches. */
internal fun isRoleApplicable(achievement: Achievement, player: PolemicaPlayer): Boolean {
    val roles = achievement.applicableRoles
    if (roles.isEmpty()) return false
    val key = player.role.toApplicableRoleKey()
    return roles.any { it.role == key }
}

internal fun cardGameTotalScore(basePoints: Double, achievementBonus: Double, rarityModifier: Double): Double =
    (basePoints + achievementBonus) * rarityModifier
