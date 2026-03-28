package io.github.mralex1810.fantasy.scoring.achievement

import com.github.mafia.vyasma.polemica.library.model.game.PolemicaGame
import com.github.mafia.vyasma.polemica.library.model.game.PolemicaPlayer
import com.github.mafia.vyasma.polemica.library.model.game.Role
import com.github.mafia.vyasma.polemica.library.utils.getFinalVotes
import com.github.mafia.vyasma.polemica.library.utils.getFirstKilled
import com.github.mafia.vyasma.polemica.library.utils.getRole
import com.github.mafia.vyasma.polemica.library.utils.isBlackWin
import com.github.mafia.vyasma.polemica.library.utils.isRedWin
import com.github.mafia.vyasma.polemica.library.utils.playersOnTable
import com.github.mafia.vyasma.polemica.library.utils.playersWithRoles
import com.github.mafia.vyasma.polemica.library.utils.isBlack
import com.github.mafia.vyasma.polemica.library.utils.isRed
import io.github.mralex1810.fantasy.entity.AchievementType
import org.springframework.stereotype.Component

@Component
class SheriffFoundBlackDetector : AchievementDetector {
    override val type = AchievementType.SHERIFF_FOUND_BLACK
    override fun detect(game: PolemicaGame, player: PolemicaPlayer): Boolean {
        if (player.role != Role.SHERIFF) return false
        return game.checks.orEmpty().any { check ->
            check.role == Role.SHERIFF && game.getRole(check.player).isBlack()
        }
    }
}

@Component
class DonFoundSheriffDetector : AchievementDetector {
    override val type = AchievementType.DON_FOUND_SHERIFF
    override fun detect(game: PolemicaGame, player: PolemicaPlayer): Boolean {
        if (player.role != Role.DON) return false
        return game.checks.orEmpty().any { check ->
            check.role == Role.DON && game.getRole(check.player) == Role.SHERIFF
        }
    }
}

@Component
class FirstNightSurvivedDetector : AchievementDetector {
    override val type = AchievementType.FIRST_NIGHT_SURVIVED
    override fun detect(game: PolemicaGame, player: PolemicaPlayer): Boolean {
        val first = game.getFirstKilled() ?: return true
        return player.position != first
    }
}

@Component
class WonGameDetector : AchievementDetector {
    override val type = AchievementType.WON_GAME
    override fun detect(game: PolemicaGame, player: PolemicaPlayer): Boolean {
        return when {
            game.isRedWin() -> player.role.isRed()
            game.isBlackWin() -> player.role.isBlack()
            else -> false
        }
    }
}

@Component
class BestMoveDetector : AchievementDetector {
    override val type = AchievementType.BEST_MOVE
    override fun detect(game: PolemicaGame, player: PolemicaPlayer): Boolean =
        (player.award ?: 0.0) > 0.0
}

@Component
class SurvivedTillEndDetector : AchievementDetector {
    override val type = AchievementType.SURVIVED_TILL_END
    override fun detect(game: PolemicaGame, player: PolemicaPlayer): Boolean =
        player.position in game.playersOnTable()
}

@Component
class VotedOutBlackDetector : AchievementDetector {
    override val type = AchievementType.VOTED_OUT_BLACK
    override fun detect(game: PolemicaGame, player: PolemicaPlayer): Boolean {
        return game.getFinalVotes(null).any { fv ->
            fv.position == player.position &&
                fv.expelled &&
                fv.convicted.any { convicted -> game.getRole(convicted).isBlack() }
        }
    }
}

@Component
class CorrectGuessDetector : AchievementDetector {
    override val type = AchievementType.CORRECT_GUESS
    override fun detect(game: PolemicaGame, player: PolemicaPlayer): Boolean {
        val guess = player.guess ?: return false
        val mafs = guess.mafs ?: return false
        if (mafs.size < 3) return false
        val actualBlacks = game.playersWithRoles(listOf(Role.MAFIA, Role.DON)).toSet()
        return mafs.toSet() == actualBlacks
    }
}

@Component
class NoFoulsDetector : AchievementDetector {
    override val type = AchievementType.NO_FOULS
    override fun detect(game: PolemicaGame, player: PolemicaPlayer): Boolean =
        player.fouls.isEmpty() && player.techs.isEmpty()
}
