package io.github.mralex1810.fantasy.scoring.achievement

import io.github.mralex1810.fantasy.repository.AchievementRepository
import org.mockito.Mockito
import kotlin.test.Test
import kotlin.test.assertNotNull

class AchievementDetectorRegistryTest {

    private val achievementIds = listOf(
        "SHERIFF_FOUND_BLACK",
        "DON_FOUND_SHERIFF",
        "FIRST_NIGHT_SURVIVED",
        "WON_GAME",
        "BEST_MOVE",
        "SURVIVED_TILL_END",
        "VOTED_OUT_BLACK",
        "CORRECT_GUESS",
        "NO_FOULS",
    )

    @Test
    fun `registry covers all achievement ids`() {
        val detectors: List<AchievementDetector> = listOf(
            SheriffFoundBlackDetector(),
            DonFoundSheriffDetector(),
            FirstNightSurvivedDetector(),
            WonGameDetector(),
            BestMoveDetector(),
            SurvivedTillEndDetector(),
            VotedOutBlackDetector(),
            CorrectGuessDetector(),
            NoFoulsDetector(),
        )
        val achievementRepository = Mockito.mock(AchievementRepository::class.java)
        val registry = AchievementDetectorRegistry(detectors, achievementRepository)
        achievementIds.forEach { assertNotNull(registry.detector(it), "missing detector for $it") }
    }
}
