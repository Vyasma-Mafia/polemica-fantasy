package io.github.mralex1810.fantasy.scoring.perk

import com.github.mafia.vyasma.polemica.library.model.game.PolemicaGame
import com.github.mafia.vyasma.polemica.library.model.game.PolemicaPlayer
import org.mockito.Mockito.mock
import kotlin.test.Test
import kotlin.test.assertEquals

class StandardPerkDetectorsTest {

    private val game = mock(PolemicaGame::class.java)
    private val player = mock(PolemicaPlayer::class.java)
    private val ninja = NinjaPerkDetector()

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
}
