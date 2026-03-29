package io.github.mralex1810.fantasy.scoring.achievement

import com.github.mafia.vyasma.polemica.library.model.game.PolemicaGame
import com.github.mafia.vyasma.polemica.library.model.game.PolemicaGameResult
import com.github.mafia.vyasma.polemica.library.model.game.PolemicaPlayer
import com.github.mafia.vyasma.polemica.library.model.game.PolemicaUser
import com.github.mafia.vyasma.polemica.library.model.game.Position
import com.github.mafia.vyasma.polemica.library.model.game.Role
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class WonGameDetectorTest {

    private val detector = WonGameDetector()

    @Test
    fun `peace wins when red wins`() {
        val game = minimalGame(result = PolemicaGameResult.RED_WIN)
        val peace = game.players!!.first { it.role == Role.PEACE }
        assertEquals(1, detector.matchCount(game, peace))
    }

    @Test
    fun `mafia loses when red wins`() {
        val game = minimalGame(result = PolemicaGameResult.RED_WIN)
        val maf = game.players!!.first { it.role == Role.MAFIA }
        assertEquals(0, detector.matchCount(game, maf))
    }

    private fun minimalGame(result: PolemicaGameResult): PolemicaGame {
        val referee = PolemicaUser(1L, "ref")
        val p1 = PolemicaPlayer(
            Position.ONE,
            "a",
            Role.PEACE,
            techs = emptyList(),
            fouls = emptyList(),
            guess = null,
            player = PolemicaUser(100L, "peace"),
            disqual = null,
            award = null,
        )
        val p2 = PolemicaPlayer(
            Position.TWO,
            "b",
            Role.MAFIA,
            techs = emptyList(),
            fouls = emptyList(),
            guess = null,
            player = PolemicaUser(200L, "maf"),
            disqual = null,
            award = null,
        )
        return PolemicaGame(
            id = 1L,
            name = "x",
            master = 1L,
            referee = referee,
            scoringVersion = null,
            scoringType = 0,
            version = 1,
            zeroVoting = null,
            tags = null,
            players = listOf(p1, p2),
            checks = emptyList(),
            shots = emptyList(),
            stage = null,
            votes = emptyList(),
            comKiller = null,
            bonuses = null,
            started = LocalDateTime.parse("2025-01-01T12:00:00"),
            stop = null,
            isLive = false,
            result = result,
            num = null,
            table = null,
            phase = null,
            factor = null,
        )
    }
}
