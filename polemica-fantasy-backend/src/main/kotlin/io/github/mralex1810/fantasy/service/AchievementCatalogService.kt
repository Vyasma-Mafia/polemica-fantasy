package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.dto.user.response.AchievementCatalogItemDto
import io.github.mralex1810.fantasy.repository.AchievementRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AchievementCatalogService(
    private val achievementRepository: AchievementRepository,
) {

    @Transactional(readOnly = true)
    fun listCatalog(): List<AchievementCatalogItemDto> =
        achievementRepository.findAllWithApplicableRoles()
            .sortedBy { it.id }
            .map { a ->
                AchievementCatalogItemDto(
                    id = a.id,
                    name = a.name,
                    description = a.description,
                    bonusPoints = a.bonusPoints,
                    occurrenceType = a.occurrenceType,
                    applicableRoles = a.applicableRoles.map { it.role }.sorted(),
                    canAppearOnRandomCards = a.canAppearOnRandomCards,
                )
            }
}
