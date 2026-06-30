package io.github.mralex1810.fantasy.scoring.perk

import com.github.mafia.vyasma.polemica.library.model.game.PolemicaCheck
import com.github.mafia.vyasma.polemica.library.model.game.PolemicaGame
import com.github.mafia.vyasma.polemica.library.model.game.PolemicaGameResult
import com.github.mafia.vyasma.polemica.library.model.game.PolemicaPlayer
import com.github.mafia.vyasma.polemica.library.model.game.PolemicaUser
import com.github.mafia.vyasma.polemica.library.model.game.PolemicaVote
import com.github.mafia.vyasma.polemica.library.model.game.Position
import com.github.mafia.vyasma.polemica.library.model.game.Role
import org.mockito.Mockito.mock
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class StandardPerkDetectorsTest {

    private val game = mock(PolemicaGame::class.java)
    private val player = mock(PolemicaPlayer::class.java)
    private val ninja = NinjaPerkDetector()
    private val sheriffCheckBlack = SheriffCheckBlackPerkDetector()
    private val voteOutSheriff = VoteOutSheriffDay1Or2PerkDetector()

    @Test
    fun `ninja treats tiny floating point noise as zero base points`() {
        assertEquals(1, ninja.matchCount(game, player, ScoringContext(0.0)))
        assertEquals(1, ninja.matchCount(game, player, ScoringContext(0.000009)))
        assertEquals(1, ninja.matchCount(game, player, ScoringContext(-0.000009)))
    }

    @Test
    fun `ninja does not match real non-zero base points`() {
        assertEquals(0, ninja.matchCount(game, player, ScoringContext(0.0001)))
        assertEquals(0, ninja.matchCount(game, player, ScoringContext(-0.0001)))
        assertEquals(0, ninja.matchCount(game, player, ScoringContext(1.0)))
    }

    @Test
    fun `sheriffCheckBlack counts unique checked black players`() {
        val sheriff = player(Position.ONE, Role.SHERIFF)
        val don = player(Position.TWO, Role.DON)
        val mafia = player(Position.THREE, Role.MAFIA)
        val red = player(Position.FOUR, Role.PEACE)
        val game = game(
            players = listOf(sheriff, don, mafia, red),
            checks = listOf(
                PolemicaCheck(1, Role.SHERIFF, Position.TWO),
                PolemicaCheck(2, Role.SHERIFF, Position.TWO),
                PolemicaCheck(3, Role.SHERIFF, Position.THREE),
                PolemicaCheck(4, Role.SHERIFF, Position.FOUR),
                PolemicaCheck(1, Role.DON, Position.ONE),
            ),
        )

        assertEquals(2, sheriffCheckBlack.matchCount(game, sheriff))
        assertEquals(0, sheriffCheckBlack.matchCount(game, don))
    }

    @Test
    fun `voteOutSheriffDay1Or2 matches black players when sheriff is voted out on first or second day`() {
        val don = player(Position.ONE, Role.DON)
        val mafia = player(Position.TWO, Role.MAFIA)
        val sheriff = player(Position.THREE, Role.SHERIFF)
        val red = player(Position.FOUR, Role.PEACE)
        val game = game(
            players = listOf(don, mafia, sheriff, red),
            votes = listOf(
                PolemicaVote(2, 1, Position.ONE, Position.THREE),
                PolemicaVote(2, 1, Position.TWO, Position.THREE),
                PolemicaVote(2, 1, Position.THREE, Position.ONE),
                PolemicaVote(2, 1, Position.FOUR, Position.THREE),
            ),
        )

        assertEquals(1, voteOutSheriff.matchCount(game, don))
        assertEquals(1, voteOutSheriff.matchCount(game, mafia))
        assertEquals(0, voteOutSheriff.matchCount(game, sheriff))
        assertEquals(0, voteOutSheriff.matchCount(game, red))
    }

    @Test
    fun `voteOutSheriffDay1Or2 ignores sheriff voted out after second day`() {
        val don = player(Position.ONE, Role.DON)
        val sheriff = player(Position.THREE, Role.SHERIFF)
        val game = game(
            players = listOf(don, sheriff, player(Position.FOUR, Role.PEACE)),
            votes = listOf(
                PolemicaVote(3, 1, Position.ONE, Position.THREE),
                PolemicaVote(3, 1, Position.THREE, Position.ONE),
                PolemicaVote(3, 1, Position.FOUR, Position.THREE),
            ),
        )

        assertEquals(0, voteOutSheriff.matchCount(game, don))
    }

    private fun game(
        players: List<PolemicaPlayer>,
        checks: List<PolemicaCheck> = emptyList(),
        votes: List<PolemicaVote> = emptyList(),
    ): PolemicaGame =
        PolemicaGame(
            id = 1L,
            name = "Test game",
            master = 1L,
            referee = null,
            scoringVersion = null,
            scoringType = 0,
            version = 0,
            zeroVoting = null,
            tags = emptyList(),
            players = players,
            checks = checks,
            shots = emptyList(),
            stage = null,
            votes = votes,
            comKiller = null,
            bonuses = emptyList(),
            started = LocalDateTime.parse("2026-01-01T12:00:00"),
            stop = null,
            isLive = false,
            result = PolemicaGameResult.BLACK_WIN,
            num = null,
            table = null,
            phase = null,
            factor = null,
        )

    private fun player(position: Position, role: Role): PolemicaPlayer =
        PolemicaPlayer(
            position = position,
            username = "player-${position.value}",
            role = role,
            techs = emptyList(),
            fouls = emptyList(),
            guess = null,
            player = PolemicaUser(position.value.toLong(), "player-${position.value}"),
            disqual = null,
            award = null,
        )
}
