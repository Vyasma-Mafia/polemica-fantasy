package io.github.mralex1810.fantasy.scoring

import com.github.mafia.vyasma.polemica.library.model.game.PolemicaGame
import io.github.mralex1810.fantasy.entity.Perk
import io.github.mralex1810.fantasy.entity.OccurrenceType
import io.github.mralex1810.fantasy.scoring.perk.toApplicableRoleKey
import com.github.mafia.vyasma.polemica.library.model.game.PolemicaPlayer

/** Only games with a settled result (red/black win) are scored; live or unfinished matches have null [PolemicaGame.getResult]. */
internal fun PolemicaGame.isFinishedForScoring(): Boolean = result != null

internal fun appliedOccurrences(raw: Int, occurrenceType: OccurrenceType): Int =
    when (occurrenceType) {
        OccurrenceType.ONCE_PER_GAME -> minOf(1, raw)
        OccurrenceType.MULTIPLE_PER_GAME -> raw
    }

/** Strict: empty applicable roles ⇒ never matches. */
internal fun isRoleApplicable(perk: Perk, player: PolemicaPlayer): Boolean {
    val roles = perk.applicableRoles
    if (roles.isEmpty()) return false
    val key = player.role.toApplicableRoleKey()
    return roles.any { it.role == key }
}

internal fun cardGameTotalScore(basePoints: Double, perkBonus: Double, rarityModifier: Double): Double =
    (basePoints + perkBonus) * rarityModifier
