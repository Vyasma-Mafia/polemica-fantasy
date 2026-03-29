package io.github.mralex1810.fantasy.scoring.achievement

import com.github.mafia.vyasma.polemica.library.model.game.PolemicaGame
import com.github.mafia.vyasma.polemica.library.model.game.PolemicaPlayer

interface AchievementDetector {
    /** Matches `achievement.id` in the database. */
    val type: String

    /** How many times the achievement condition holds in this game (0 = none). */
    fun matchCount(game: PolemicaGame, player: PolemicaPlayer): Int
}
