package io.github.mralex1810.fantasy.scoring.achievement

import io.github.mralex1810.fantasy.entity.AchievementType
import org.springframework.stereotype.Component

@Component
class AchievementDetectorRegistry(
    detectors: List<AchievementDetector>,
) {
    private val byType: Map<AchievementType, AchievementDetector> = detectors.associateBy { it.type }

    fun detector(type: AchievementType): AchievementDetector? = byType[type]
}
