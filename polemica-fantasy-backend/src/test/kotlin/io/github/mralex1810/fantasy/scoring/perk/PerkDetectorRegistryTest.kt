package io.github.mralex1810.fantasy.scoring.perk

import io.github.mralex1810.fantasy.repository.PerkRepository
import org.mockito.Mockito
import kotlin.test.Test
import kotlin.test.assertNotNull

class PerkDetectorRegistryTest {

    private val perkIds = listOf(
        "sniper",
        "winThreeToThree",
        "findSheriff",
        "voteForBlack",
        "strongCity",
        "firstKickedFullGuess",
        "votingOnlyForBlack",
        "winWithoutCritic",
    )

    @Test
    fun `registry covers all perk ids`() {
        val detectors: List<PerkDetector> = listOf(
            SniperPerkDetector(),
            WinThreeToThreePerkDetector(),
            FindSheriffPerkDetector(),
            VoteForBlackPerkDetector(),
            StrongCityPerkDetector(),
            FirstKickedFullGuessPerkDetector(),
            VotingOnlyForBlackPerkDetector(),
            WinWithoutCriticPerkDetector(),
        )
        val perkRepository = Mockito.mock(PerkRepository::class.java)
        val registry = PerkDetectorRegistry(detectors, perkRepository)
        perkIds.forEach { assertNotNull(registry.detector(it), "missing detector for $it") }
    }
}
