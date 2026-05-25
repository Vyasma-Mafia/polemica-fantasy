package io.github.mralex1810.fantasy.service.achievement

import io.github.mralex1810.fantasy.dto.user.request.UpdateProfileCustomizationRequest
import io.github.mralex1810.fantasy.dto.user.response.AchievementBadgeDto
import io.github.mralex1810.fantasy.dto.user.response.AchievementItemDto
import io.github.mralex1810.fantasy.dto.user.response.PlayerAchievementShowcaseDto
import io.github.mralex1810.fantasy.dto.user.response.PlayerAchievementSummaryDto
import io.github.mralex1810.fantasy.dto.user.response.PlayerNextAchievementDto
import io.github.mralex1810.fantasy.dto.user.response.ProfileCustomizationDto
import io.github.mralex1810.fantasy.dto.user.response.ProfileFrameDto
import io.github.mralex1810.fantasy.entity.AchievementDefinition
import io.github.mralex1810.fantasy.entity.TelegramUser
import io.github.mralex1810.fantasy.entity.UserAchievement
import io.github.mralex1810.fantasy.entity.UserProfileCustomization
import io.github.mralex1810.fantasy.entity.UserProfileFeaturedAchievement
import io.github.mralex1810.fantasy.repository.AchievementDefinitionRepository
import io.github.mralex1810.fantasy.repository.TelegramUserRepository
import io.github.mralex1810.fantasy.repository.UserAchievementRepository
import io.github.mralex1810.fantasy.repository.UserCosmeticUnlockRepository
import io.github.mralex1810.fantasy.repository.UserProfileCustomizationRepository
import io.github.mralex1810.fantasy.repository.UserProfileFeaturedAchievementRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

@Service
class ProfileCustomizationService(
    private val customizationRepository: UserProfileCustomizationRepository,
    private val featuredRepository: UserProfileFeaturedAchievementRepository,
    private val userAchievementRepository: UserAchievementRepository,
    private val userCosmeticUnlockRepository: UserCosmeticUnlockRepository,
    private val achievementDefinitionRepository: AchievementDefinitionRepository,
    private val telegramUserRepository: TelegramUserRepository,
    private val achievementCatalogService: AchievementCatalogService,
) {
    @Transactional(readOnly = true)
    fun getCustomization(user: TelegramUser): ProfileCustomizationDto {
        val userId = user.id!!
        val selectedFrame = currentProfileFrameCode(userId)
        val claimedByCode = userAchievementRepository.findClaimedWithDefinitions(userId)
            .associateBy { it.achievement!!.code }
        val featuredCodes = featuredRepository.findAllByTelegramUserIdOrdered(userId)
            .mapNotNull { it.achievement }
            .filter { it.code in claimedByCode }
            .filter { it.visibility == "PUBLIC" }
            .map { it.code }

        return ProfileCustomizationDto(
            profileFrameCode = selectedFrame,
            unlockedFrames = unlockedFrames(userId),
            featuredAchievementCodes = featuredCodes,
            availableFeaturedAchievements = claimedByCode.values.map { toBadge(it.achievement!!) },
        )
    }

    @Transactional
    fun updateCustomization(user: TelegramUser, request: UpdateProfileCustomizationRequest): ProfileCustomizationDto {
        val userId = user.id!!
        if (request.featuredAchievementCodes.size > MAX_FEATURED_ACHIEVEMENTS) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "At most 5 featured achievements are allowed")
        }
        if (request.featuredAchievementCodes.distinct().size != request.featuredAchievementCodes.size) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate featured achievements are not allowed")
        }

        val managedUser = telegramUserRepository.findById(userId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "User not found") }
        val selectedFrameCode = request.profileFrameCode
        if (selectedFrameCode != null && !hasProfileFrame(userId, selectedFrameCode)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Profile frame is not unlocked")
        }

        val claimedByCode = userAchievementRepository.findClaimedWithDefinitions(userId)
            .associateBy { it.achievement!!.code }
        val achievements = request.featuredAchievementCodes.map { code ->
            claimedByCode[code]?.achievement
                ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Featured achievement is not claimed")
        }

        val customization = customizationRepository.findByTelegramUser_Id(userId)
            ?: UserProfileCustomization(telegramUser = managedUser)
        customization.profileFrameCode = selectedFrameCode
        customization.updatedAt = Instant.now()
        customizationRepository.save(customization)

        featuredRepository.deleteAllByTelegramUserId(userId)
        featuredRepository.flush()
        featuredRepository.saveAll(
            achievements.mapIndexed { index, achievement ->
                UserProfileFeaturedAchievement(
                    telegramUser = managedUser,
                    achievement = achievement,
                    displayOrder = index,
                )
            },
        )

        return getCustomization(managedUser)
    }

    @Transactional(readOnly = true)
    fun buildPublicShowcase(internalTelegramUserId: Long): PlayerAchievementShowcaseDto {
        val definitions = achievementDefinitionRepository.findAllEnabledPublicWithRewards()
        val progressByDefinitionId = userAchievementRepository.findAllWithDefinitionsByTelegramUserId(internalTelegramUserId)
            .associateBy { it.achievement!!.id!! }
        val itemsByDefinition = definitions.map { definition ->
            definition to achievementCatalogService.toItem(definition, progressByDefinitionId[definition.id!!], internalTelegramUserId)
        }
        val enabledPublicCodes = definitions.map { it.code }.toSet()
        val featuredAchievements = featuredRepository.findAllByTelegramUserIdOrdered(internalTelegramUserId)
            .mapNotNull { featured ->
                val definition = featured.achievement ?: return@mapNotNull null
                val userAchievement = progressByDefinitionId[definition.id!!] ?: return@mapNotNull null
                if (userAchievement.claimedAt == null || definition.visibility != "PUBLIC") return@mapNotNull null
                toBadge(definition)
            }
        val disabledFeaturedClaimed = featuredAchievements.count { it.code !in enabledPublicCodes }

        return PlayerAchievementShowcaseDto(
            achievementSummary = PlayerAchievementSummaryDto(
                completed = itemsByDefinition.count { (_, item) -> item.state == "COMPLETED_UNCLAIMED" || item.state == "CLAIMED" },
                claimed = itemsByDefinition.count { (_, item) -> item.state == "CLAIMED" } + disabledFeaturedClaimed,
                totalVisible = definitions.size,
            ),
            profileFrame = currentProfileFrameCode(internalTelegramUserId)?.let { ProfileFrameDto(it, frameName(it), null) },
            featuredAchievements = featuredAchievements,
            nextAchievement = nextAchievement(itemsByDefinition),
        )
    }

    private fun currentProfileFrameCode(telegramUserId: Long): String? {
        val selected = customizationRepository.findByTelegramUser_Id(telegramUserId)?.profileFrameCode ?: return null
        return selected.takeIf { hasProfileFrame(telegramUserId, it) }
    }

    private fun hasProfileFrame(telegramUserId: Long, code: String): Boolean =
        userCosmeticUnlockRepository.existsByTelegramUser_IdAndCosmeticTypeAndCosmeticCode(
            telegramUserId,
            PROFILE_FRAME_TYPE,
            code,
        )

    private fun unlockedFrames(telegramUserId: Long): List<ProfileFrameDto> =
        userCosmeticUnlockRepository.findAllByTelegramUser_IdAndCosmeticType(telegramUserId, PROFILE_FRAME_TYPE)
            .sortedBy { it.cosmeticCode }
            .map { unlock -> ProfileFrameDto(code = unlock.cosmeticCode, name = frameName(unlock.cosmeticCode), assetUrl = null) }

    private fun toBadge(definition: AchievementDefinition): AchievementBadgeDto =
        AchievementBadgeDto(
            code = definition.code,
            title = definition.title,
            iconUrl = definition.iconUrl,
            rarity = definition.rarity,
            accentColor = definition.accentColor,
        )

    private fun nextAchievement(itemsByDefinition: List<Pair<AchievementDefinition, AchievementItemDto>>): PlayerNextAchievementDto? =
        itemsByDefinition
            .filter { (_, item) -> item.state != "CLAIMED" }
            .sortedWith(
                compareByDescending<Pair<AchievementDefinition, AchievementItemDto>> { (_, item) -> item.progressValue > 0 }
                    .thenByDescending { (_, item) ->
                        if (item.targetValue > 0) item.progressValue.toDouble() / item.targetValue.toDouble() else 0.0
                    }
                    .thenBy { (definition, _) -> definition.displayOrder }
                    .thenBy { (definition, _) -> definition.id ?: Long.MAX_VALUE },
            )
            .firstOrNull()
            ?.let { (_, item) ->
                PlayerNextAchievementDto(
                    code = item.code,
                    title = item.title,
                    progressValue = item.progressValue.coerceAtMost(item.targetValue),
                    targetValue = item.targetValue,
                )
            }

    private fun frameName(code: String): String = when (code) {
        "budget_master" -> "Мастер бюджета"
        "dynasty" -> "Династия"
        else -> code.replace('_', ' ')
    }

    companion object {
        private const val MAX_FEATURED_ACHIEVEMENTS = 5
        private const val PROFILE_FRAME_TYPE = "PROFILE_FRAME"
    }
}
