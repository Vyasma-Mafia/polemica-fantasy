package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.dto.admin.request.UpdateAchievementRequest
import io.github.mralex1810.fantasy.dto.admin.response.AchievementAdminDto
import io.github.mralex1810.fantasy.entity.AchievementApplicableRole
import io.github.mralex1810.fantasy.repository.AchievementApplicableRoleRepository
import io.github.mralex1810.fantasy.repository.AchievementRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class AchievementAdminService(
    private val achievementRepository: AchievementRepository,
    private val achievementApplicableRoleRepository: AchievementApplicableRoleRepository,
) {

    @Transactional(readOnly = true)
    fun listAchievements(): List<AchievementAdminDto> =
        achievementRepository.findAllWithApplicableRoles()
            .sortedBy { it.id }
            .map { it.toDto() }

    @Transactional
    fun updateAchievement(id: String, request: UpdateAchievementRequest): AchievementAdminDto {
        val achievement = achievementRepository.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Achievement $id not found")
        }
        request.name?.let { achievement.name = it.trim() }
        request.description?.let { achievement.description = it }
        request.bonusPoints?.let { achievement.bonusPoints = it }
        request.occurrenceType?.let { achievement.occurrenceType = it }
        request.canAppearOnRandomCards?.let { achievement.canAppearOnRandomCards = it }
        request.applicableRoles?.let { roles ->
            achievementApplicableRoleRepository.deleteAllByAchievementId(id)
            achievement.applicableRoles.clear()
            roles.distinct().sorted().forEach { role ->
                val row = AchievementApplicableRole(
                    achievementId = id,
                    role = role.trim(),
                )
                achievementApplicableRoleRepository.save(row)
                achievement.applicableRoles.add(row)
            }
        }
        achievementRepository.save(achievement)
        val refreshed = achievementRepository.findWithApplicableRolesById(id)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Achievement $id not found")
        return refreshed.toDto()
    }

    private fun io.github.mralex1810.fantasy.entity.Achievement.toDto() = AchievementAdminDto(
        id = id,
        name = name,
        description = description,
        bonusPoints = bonusPoints,
        occurrenceType = occurrenceType,
        applicableRoles = applicableRoles.map { it.role }.sorted(),
        canAppearOnRandomCards = canAppearOnRandomCards,
    )
}
