package io.github.mralex1810.fantasy.scoring.perk

import io.github.mralex1810.fantasy.repository.PerkRepository
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class PerkDetectorRegistry(
    detectors: List<PerkDetector>,
    private val perkRepository: PerkRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val byId: Map<String, PerkDetector> = detectors.associateBy { it.type }

    @PostConstruct
    fun validateDetectorsAgainstDatabase() {
        val dbIds = perkRepository.findAll().map { it.id }.toSet()
        val missingDetectors = dbIds - byId.keys
        if (missingDetectors.isNotEmpty()) {
            log.warn(
                "Perks present in DB but no PerkDetector bean for type(s): {}",
                missingDetectors,
            )
        }
        val detectorsWithoutRow = byId.keys - dbIds
        if (detectorsWithoutRow.isNotEmpty()) {
            log.warn(
                "PerkDetector beans with no row in perk table: {}",
                detectorsWithoutRow,
            )
        }
    }

    fun detector(perkId: String): PerkDetector? = byId[perkId]
}
