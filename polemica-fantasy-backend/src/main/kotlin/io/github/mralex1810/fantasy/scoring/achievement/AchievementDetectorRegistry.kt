package io.github.mralex1810.fantasy.scoring.achievement

import io.github.mralex1810.fantasy.repository.AchievementRepository
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class AchievementDetectorRegistry(
    detectors: List<AchievementDetector>,
    private val achievementRepository: AchievementRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val byId: Map<String, AchievementDetector> = detectors.associateBy { it.type }

    @PostConstruct
    fun validateDetectorsAgainstDatabase() {
        val dbIds = achievementRepository.findAll().map { it.id }.toSet()
        val missingDetectors = dbIds - byId.keys
        if (missingDetectors.isNotEmpty()) {
            log.warn(
                "Achievements present in DB but no AchievementDetector bean for type(s): {}",
                missingDetectors,
            )
        }
        val detectorsWithoutRow = byId.keys - dbIds
        if (detectorsWithoutRow.isNotEmpty()) {
            log.warn(
                "AchievementDetector beans with no row in achievement table: {}",
                detectorsWithoutRow,
            )
        }
    }

    fun detector(achievementId: String): AchievementDetector? = byId[achievementId]
}
