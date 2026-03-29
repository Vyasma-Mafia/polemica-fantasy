package io.github.mralex1810.fantasy.scoring.achievement

import io.github.mralex1810.fantasy.repository.AchievementRepository
import org.mockito.Mockito
import kotlin.test.Test
import kotlin.test.assertNotNull

class AchievementDetectorRegistryTest {

    private val achievementIds = listOf(
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
    fun `registry covers all achievement ids`() {
        val detectors: List<AchievementDetector> = listOf(
            SniperAchievementDetector(),
            WinThreeToThreeAchievementDetector(),
            FindSheriffAchievementDetector(),
            VoteForBlackAchievementDetector(),
            StrongCityAchievementDetector(),
            FirstKickedFullGuessAchievementDetector(),
            VotingOnlyForBlackAchievementDetector(),
            WinWithoutCriticAchievementDetector(),
        )
        val achievementRepository = Mockito.mock(AchievementRepository::class.java)
        val registry = AchievementDetectorRegistry(detectors, achievementRepository)
        achievementIds.forEach { assertNotNull(registry.detector(it), "missing detector for $it") }
    }
}
