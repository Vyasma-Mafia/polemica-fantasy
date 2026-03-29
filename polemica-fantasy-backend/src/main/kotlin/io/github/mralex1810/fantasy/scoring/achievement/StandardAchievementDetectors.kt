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
import org.springframework.stereotype.Component

@Component
class SheriffFoundBlackDetector : AchievementDetector {
    override val type = "SHERIFF_FOUND_BLACK"
    override fun matchCount(game: PolemicaGame, player: PolemicaPlayer): Int {
        if (player.role != Role.SHERIFF) return 0
        return game.checks.orEmpty().count { check ->
            check.role == Role.SHERIFF && game.getRole(check.player).isBlack()
        }
    }
}

@Component
class DonFoundSheriffDetector : AchievementDetector {
    override val type = "DON_FOUND_SHERIFF"
    override fun matchCount(game: PolemicaGame, player: PolemicaPlayer): Int {
        if (player.role != Role.DON) return 0
        return game.checks.orEmpty().count { check ->
            check.role == Role.DON && game.getRole(check.player) == Role.SHERIFF
        }
    }
}

@Component
class FirstNightSurvivedDetector : AchievementDetector {
    override val type = "FIRST_NIGHT_SURVIVED"
    override fun matchCount(game: PolemicaGame, player: PolemicaPlayer): Int {
        val first = game.getFirstKilled() ?: return 1
        return if (player.position != first) 1 else 0
    }
}

@Component
class WonGameDetector : AchievementDetector {
    override val type = "WON_GAME"
    override fun matchCount(game: PolemicaGame, player: PolemicaPlayer): Int =
        when {
            game.isRedWin() -> if (player.role.isRed()) 1 else 0
            game.isBlackWin() -> if (player.role.isBlack()) 1 else 0
            else -> 0
        }
}

@Component
class BestMoveDetector : AchievementDetector {
    override val type = "BEST_MOVE"
    override fun matchCount(game: PolemicaGame, player: PolemicaPlayer): Int =
        if ((player.award ?: 0.0) > 0.0) 1 else 0
}

@Component
class SurvivedTillEndDetector : AchievementDetector {
    override val type = "SURVIVED_TILL_END"
    override fun matchCount(game: PolemicaGame, player: PolemicaPlayer): Int =
        if (player.position in game.playersOnTable()) 1 else 0
}

@Component
class VotedOutBlackDetector : AchievementDetector {
    override val type = "VOTED_OUT_BLACK"
    override fun matchCount(game: PolemicaGame, player: PolemicaPlayer): Int {
        return game.getFinalVotes(null).count { fv ->
            fv.position == player.position &&
                fv.expelled &&
                fv.convicted.any { convicted -> game.getRole(convicted).isBlack() }
        }
    }
}

@Component
class CorrectGuessDetector : AchievementDetector {
    override val type = "CORRECT_GUESS"
    override fun matchCount(game: PolemicaGame, player: PolemicaPlayer): Int {
        val guess = player.guess ?: return 0
        val mafs = guess.mafs ?: return 0
        if (mafs.size < 3) return 0
        val actualBlacks = game.playersWithRoles(listOf(Role.MAFIA, Role.DON)).toSet()
        return if (mafs.toSet() == actualBlacks) 1 else 0
    }
}

@Component
class NoFoulsDetector : AchievementDetector {
    override val type = "NO_FOULS"
    override fun matchCount(game: PolemicaGame, player: PolemicaPlayer): Int =
        if (player.fouls.isEmpty() && player.techs.isEmpty()) 1 else 0
}
