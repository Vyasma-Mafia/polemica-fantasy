package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.dto.admin.request.UpdatePerkRequest
import io.github.mralex1810.fantasy.dto.admin.response.PerkAdminDto
import io.github.mralex1810.fantasy.entity.PerkApplicableRole
import io.github.mralex1810.fantasy.repository.PerkApplicableRoleRepository
import io.github.mralex1810.fantasy.repository.PerkRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class PerkAdminService(
    private val perkRepository: PerkRepository,
    private val perkApplicableRoleRepository: PerkApplicableRoleRepository,
) {

    @Transactional(readOnly = true)
    fun listPerks(): List<PerkAdminDto> =
        perkRepository.findAllWithApplicableRoles()
            .sortedBy { it.id }
            .map { it.toDto() }

    @Transactional
    fun updatePerk(id: String, request: UpdatePerkRequest): PerkAdminDto {
        val perk = perkRepository.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Perk $id not found")
        }
        request.name?.let { perk.name = it.trim() }
        request.description?.let { perk.description = it }
        request.bonusPoints?.let { perk.bonusPoints = it }
        request.occurrenceType?.let { perk.occurrenceType = it }
        request.canAppearOnRandomCards?.let { perk.canAppearOnRandomCards = it }
        request.applicableRoles?.let { roles ->
            perkApplicableRoleRepository.deleteAllByPerkId(id)
            perk.applicableRoles.clear()
            roles.distinct().sorted().forEach { role ->
                val row = PerkApplicableRole(
                    perkId = id,
                    role = role.trim(),
                )
                perkApplicableRoleRepository.save(row)
                perk.applicableRoles.add(row)
            }
        }
        perkRepository.save(perk)
        val refreshed = perkRepository.findWithApplicableRolesById(id)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Perk $id not found")
        return refreshed.toDto()
    }

    private fun io.github.mralex1810.fantasy.entity.Perk.toDto() = PerkAdminDto(
        id = id,
        name = name,
        description = description,
        bonusPoints = bonusPoints,
        occurrenceType = occurrenceType,
        applicableRoles = applicableRoles.map { it.role }.sorted(),
        canAppearOnRandomCards = canAppearOnRandomCards,
    )
}
