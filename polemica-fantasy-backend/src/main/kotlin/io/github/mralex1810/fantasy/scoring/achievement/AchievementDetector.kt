package io.github.mralex1810.fantasy.scoring.achievement

import com.github.mafia.vyasma.polemica.library.model.game.PolemicaGame
import com.github.mafia.vyasma.polemica.library.model.game.PolemicaPlayer
import io.github.mralex1810.fantasy.entity.AchievementType

interface AchievementDetector {
    val type: AchievementType
    fun detect(game: PolemicaGame, player: PolemicaPlayer): Boolean
}
