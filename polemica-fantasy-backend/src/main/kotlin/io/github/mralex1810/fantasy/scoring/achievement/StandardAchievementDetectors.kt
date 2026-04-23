package io.github.mralex1810.fantasy.scoring.achievement

import com.github.mafia.vyasma.polemica.library.model.game.PolemicaGame
import com.github.mafia.vyasma.polemica.library.model.game.PolemicaGameResult
import com.github.mafia.vyasma.polemica.library.model.game.PolemicaPlayer
import com.github.mafia.vyasma.polemica.library.model.game.Position
import com.github.mafia.vyasma.polemica.library.model.game.Role
import com.github.mafia.vyasma.polemica.library.utils.check
import com.github.mafia.vyasma.polemica.library.utils.getBlacksOnTable
import com.github.mafia.vyasma.polemica.library.utils.getCriticDay
import com.github.mafia.vyasma.polemica.library.utils.getFinalVotes
import com.github.mafia.vyasma.polemica.library.utils.getKickedFromTable
import com.github.mafia.vyasma.polemica.library.utils.getKilled
import com.github.mafia.vyasma.polemica.library.utils.getRealComKiller
import com.github.mafia.vyasma.polemica.library.utils.getRole
import com.github.mafia.vyasma.polemica.library.utils.playersOnTable
import com.github.mafia.vyasma.polemica.library.utils.isBlack
import com.github.mafia.vyasma.polemica.library.utils.isBlackWin
import com.github.mafia.vyasma.polemica.library.utils.isRed
import com.github.mafia.vyasma.polemica.library.utils.isRedWin
import org.springframework.stereotype.Component

private fun boolToInt(value: Boolean): Int = if (value) 1 else 0

/** Цепочка «руль» по полю [PolemicaPlayer.guess] / vice; промежуточные звенья — любая роль, старт только с красного. */
private fun redViceChainReachCount(game: PolemicaGame, target: PolemicaPlayer): Int {
    val byPosition = game.players.orEmpty().associateBy { it.position }
    fun redStartsChainToPlayer(start: PolemicaPlayer): Boolean {
        if (!start.role.isRed()) return false
        var current: Position? = start.guess?.vice ?: return false
        val visited = mutableSetOf<Position>()
        while (current != null) {
            if (current == target.position) return true
            if (!visited.add(current)) return false
            val holder = byPosition[current] ?: return false
            current = holder.guess?.vice
        }
        return false
    }
    return game.players.orEmpty().count(::redStartsChainToPlayer)
}

/** Отстрелите шерифа в первую ночь — [polemica-achivement-service SniperAchievement] */
@Component
class SniperAchievementDetector : AchievementDetector {
    override val type = "sniper"
    override fun matchCount(game: PolemicaGame, player: PolemicaPlayer): Int {
        if (game.getRealComKiller() != player.position) return 0
        val sheriffShotNight1 =
            game.getKilled(null).any { killed ->
                val pos = killed.position
                killed.night == 1 && pos != null && game.getRole(pos) == Role.SHERIFF
            }
        return boolToInt(sheriffShotNight1)
    }
}

/** Выиграйте за черного 3 в 3 — [WinThreeToThreeLastAchievement] */
@Component
class WinThreeToThreeAchievementDetector : AchievementDetector {
    override val type = "winThreeToThree"
    override fun matchCount(game: PolemicaGame, player: PolemicaPlayer): Int =
        game.check {
            assert { player.role.isBlack() }
            assert { game.isBlackWin() }
            val blackOnTable = game.getBlacksOnTable()
            return boolToInt(blackOnTable.size == 3)
        }
}

/** Найдите шерифа за дона в первую ночь — [FindSheriffAchievement] */
@Component
class FindSheriffAchievementDetector : AchievementDetector {
    override val type = "findSheriff"
    override fun matchCount(game: PolemicaGame, player: PolemicaPlayer): Int {
        if (player.role != Role.DON) return 0
        return boolToInt(
            game.checks.orEmpty().any { check ->
                check.role == Role.DON &&
                    check.night == 1 &&
                    game.getRole(check.player) == Role.SHERIFF
            },
        )
    }
}

/**
 * На красном проголосуйте за уход черного (последнее голосование на круге; при попиле — рука за подъем).
 * [VoteForBlackAchievement] — несколько раз за игру.
 */
@Component
class VoteForBlackAchievementDetector : AchievementDetector {
    override val type = "voteForBlack"
    override fun matchCount(game: PolemicaGame, player: PolemicaPlayer): Int {
        if (!player.role.isRed()) return 0
        return game.getFinalVotes(null)
            .filter { it.position == player.position }
            .sumOf { fv -> fv.convicted.count { game.getRole(it).isBlack() } }
    }
}

/** Выиграйте красным, когда шериф умер в первую ночь — [StrongCityAchievement] */
@Component
class StrongCityAchievementDetector : AchievementDetector {
    override val type = "strongCity"
    override fun matchCount(game: PolemicaGame, player: PolemicaPlayer): Int =
        boolToInt(
            game.getRealComKiller() != null &&
                player.role == Role.PEACE &&
                game.result == PolemicaGameResult.RED_WIN,
        )
}

/** Будучи первым покинувшим стол красным, оставьте три верных цвета в лучший ход — [FirstKickedFullGuessAchievement] */
@Component
class FirstKickedFullGuessAchievementDetector : AchievementDetector {
    override val type = "firstKickedFullGuess"
    override fun matchCount(game: PolemicaGame, player: PolemicaPlayer): Int {
        if (!player.role.isRed()) return 0
        val kicked = game.getKickedFromTable()
        if (kicked.isEmpty()) return 0
        val firstKicked = kicked.first()
        if (kicked.filter { it.gamePhase == firstKicked.gamePhase }.size != 1) return 0
        if (firstKicked.position != player.position) return 0

        val guess = player.guess ?: return 0
        val mafsSize = guess.mafs?.size ?: 0
        val civsSize = guess.civs?.size ?: 0
        if (mafsSize + civsSize != 3) return 0
        val mafsOk = guess.mafs?.all { game.getRole(it).isBlack() } ?: true
        val civsOk = guess.civs?.all { game.getRole(it).isRed() } ?: true
        return boolToInt(mafsOk && civsOk)
    }
}

/** Будучи мирным, голосуйте только за черных (кроме попилов) — [VotingOnlyForBlackAchievement] */
@Component
class VotingOnlyForBlackAchievementDetector : AchievementDetector {
    override val type = "votingOnlyForBlack"
    override fun matchCount(game: PolemicaGame, player: PolemicaPlayer): Int =
        game.check {
            assert { player.role == Role.PEACE }
            val votes = game.getFinalVotes(null)
            val mine = votes.filter { it.position == player.position }
            return boolToInt(
                mine.isNotEmpty() &&
                    mine.all { fv -> fv.convicted.any { game.getRole(it).isBlack() } },
            )
        }
}

/** Выиграйте игру за красного, не переходя в критику — [WinWithoutCriticAchievement] */
@Component
class WinWithoutCriticAchievementDetector : AchievementDetector {
    override val type = "winWithoutCritic"
    override fun matchCount(game: PolemicaGame, player: PolemicaPlayer): Int =
        game.check {
            assert { player.role.isRed() }
            assert { game.isRedWin() }
            return boolToInt(game.getCriticDay() == null)
        }
}

/** Ровно 0 базовых баллов с Polemica — [ninja]. */
@Component
class NinjaAchievementDetector : AchievementDetector {
    override val type = "ninja"
    override fun matchCount(game: PolemicaGame, player: PolemicaPlayer): Int = 0

    override fun matchCount(
        game: PolemicaGame,
        player: PolemicaPlayer,
        context: ScoringContext,
    ): Int = boolToInt(context.basePoints == 0.0)
}

/**
 * «Рулевой» (vice) в угадайке: считаются цепочки, начинающиеся с мирного или шерифа.
 * Транзитивно: если A дал руль B, а B — С, то С получает и от A, и от B (дона/мафия не стартуют цепочку).
 */
@Component
class CrownedAchievementDetector : AchievementDetector {
    override val type = "crowned"
    override fun matchCount(game: PolemicaGame, player: PolemicaPlayer): Int =
        redViceChainReachCount(game, player)
}

/**
 * На столу остались ровно два игрока, текущий всё ещё в игре (угадайка) — [lastHeroGuess].
 */
@Component
class LastHeroGuessAchievementDetector : AchievementDetector {
    override val type = "lastHeroGuess"
    override fun matchCount(game: PolemicaGame, player: PolemicaPlayer): Int {
        val onTable = game.playersOnTable(null)
        return boolToInt(onTable.size == 2 && onTable.contains(player.position))
    }
}
