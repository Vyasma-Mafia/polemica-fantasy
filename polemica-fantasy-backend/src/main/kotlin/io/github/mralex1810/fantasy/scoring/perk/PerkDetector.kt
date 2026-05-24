package io.github.mralex1810.fantasy.scoring.perk

import com.github.mafia.vyasma.polemica.library.model.game.PolemicaGame
import com.github.mafia.vyasma.polemica.library.model.game.PolemicaPlayer

data class ScoringContext(val basePoints: Double)

interface PerkDetector {
    /** Matches `perk.id` in the database. */
    val type: String

    /** How many times the perk condition holds in this game (0 = none). */
    fun matchCount(game: PolemicaGame, player: PolemicaPlayer): Int

    /** Optional context (e.g. base points from Polemica API) for detectors that need it. */
    fun matchCount(
        game: PolemicaGame,
        player: PolemicaPlayer,
        context: ScoringContext,
    ): Int = matchCount(game, player)
}
