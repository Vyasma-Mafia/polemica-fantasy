package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.dto.user.response.PerkCatalogItemDto
import io.github.mralex1810.fantasy.repository.PerkRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PerkCatalogService(
    private val perkRepository: PerkRepository,
) {

    @Transactional(readOnly = true)
    fun listCatalog(): List<PerkCatalogItemDto> =
        perkRepository.findAllWithApplicableRoles()
            .sortedBy { it.id }
            .map { a ->
                PerkCatalogItemDto(
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
