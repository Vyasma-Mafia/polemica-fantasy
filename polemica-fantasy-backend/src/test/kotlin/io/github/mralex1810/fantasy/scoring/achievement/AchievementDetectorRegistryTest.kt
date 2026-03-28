package io.github.mralex1810.fantasy.scoring.achievement

import io.github.mralex1810.fantasy.entity.AchievementType
import kotlin.test.Test
import kotlin.test.assertNotNull

class AchievementDetectorRegistryTest {

    @Test
    fun `registry covers all achievement types`() {
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
        val registry = AchievementDetectorRegistry(detectors)
        AchievementType.entries.forEach { assertNotNull(registry.detector(it), "missing detector for $it") }
    }
}
