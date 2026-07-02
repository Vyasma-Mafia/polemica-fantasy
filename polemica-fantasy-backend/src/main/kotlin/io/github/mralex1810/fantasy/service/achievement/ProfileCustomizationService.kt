package io.github.mralex1810.fantasy.service.achievement

import io.github.mralex1810.fantasy.dto.user.request.UpdateProfileCustomizationRequest
import io.github.mralex1810.fantasy.dto.user.response.AchievementBadgeDto
import io.github.mralex1810.fantasy.dto.user.response.FavoriteBadgePlayerOptionDto
import io.github.mralex1810.fantasy.dto.user.response.AchievementItemDto
import io.github.mralex1810.fantasy.dto.user.response.PlayerAchievementShowcaseDto
import io.github.mralex1810.fantasy.dto.user.response.PlayerAchievementSummaryDto
import io.github.mralex1810.fantasy.dto.user.response.PlayerNextAchievementDto
import io.github.mralex1810.fantasy.dto.user.response.ProfileCosmeticDto
import io.github.mralex1810.fantasy.dto.user.response.ProfileCosmeticOptionsDto
import io.github.mralex1810.fantasy.dto.user.response.ProfileCustomizationDto
import io.github.mralex1810.fantasy.dto.user.response.ProfileFrameDto
import io.github.mralex1810.fantasy.entity.AchievementDefinition
import io.github.mralex1810.fantasy.entity.ProfileCosmetic
import io.github.mralex1810.fantasy.entity.TelegramUser
import io.github.mralex1810.fantasy.entity.UserAchievement
import io.github.mralex1810.fantasy.entity.UserProfileCustomization
import io.github.mralex1810.fantasy.entity.UserProfileFeaturedAchievement
import io.github.mralex1810.fantasy.repository.AchievementDefinitionRepository
import io.github.mralex1810.fantasy.repository.ProfileCosmeticRepository
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
    private val profileCosmeticRepository: ProfileCosmeticRepository,
    private val achievementDefinitionRepository: AchievementDefinitionRepository,
    private val telegramUserRepository: TelegramUserRepository,
    private val achievementCatalogService: AchievementCatalogService,
    private val achievementProgressCalculator: AchievementProgressCalculator,
) {
    @Transactional(readOnly = true)
    fun getCustomization(user: TelegramUser): ProfileCustomizationDto {
        val userId = user.id!!
        val customization = customizationRepository.findByTelegramUser_Id(userId)
        val selectedFrame = currentProfileFrameCode(userId, customization)
        val selectedTitle = currentProfileCosmetic(userId, customization?.profileTitleCode, PROFILE_COSMETIC_TITLE)
        val selectedAccent = currentProfileCosmetic(userId, customization?.profileAccentCode, PROFILE_COSMETIC_ACCENT)
        val selectedBackground = currentProfileCosmetic(userId, customization?.profileBackgroundCode, PROFILE_COSMETIC_BACKGROUND)
        val unlockedCosmetics = unlockedProfileCosmetics(userId)
        val favoriteBadgeOptions = favoriteBadgePlayerOptions(userId)
        val favoriteBadgeFantasyPlayerId = customization?.favoriteBadgeFantasyPlayerId
            ?.takeIf { selectedId -> favoriteBadgeOptions.any { it.fantasyPlayerId == selectedId } }
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
            profileTitleCode = selectedTitle?.code,
            profileAccentCode = selectedAccent?.code,
            profileBackgroundCode = selectedBackground?.code,
            unlockedCosmetics = unlockedCosmetics,
            featuredAchievementCodes = featuredCodes,
            availableFeaturedAchievements = claimedByCode.values.map { toBadge(it.achievement!!, userId) },
            favoriteBadgeFantasyPlayerId = favoriteBadgeFantasyPlayerId,
            favoriteBadgePlayerOptions = favoriteBadgeOptions,
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
        val existingCustomization = customizationRepository.findByTelegramUser_Id(userId)
        val selectedFrameCode = request.profileFrameCode
        if (selectedFrameCode != null && !hasProfileFrame(userId, selectedFrameCode)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Profile frame is not unlocked")
        }
        val selectedTitleCode = selectedCosmeticCode(
            userId = userId,
            requestedCode = request.profileTitleCode,
            requested = request.profileTitleCodeSet,
            currentCode = existingCustomization?.profileTitleCode,
            kind = PROFILE_COSMETIC_TITLE,
        )
        val selectedAccentCode = selectedCosmeticCode(
            userId = userId,
            requestedCode = request.profileAccentCode,
            requested = request.profileAccentCodeSet,
            currentCode = existingCustomization?.profileAccentCode,
            kind = PROFILE_COSMETIC_ACCENT,
        )
        val selectedBackgroundCode = selectedCosmeticCode(
            userId = userId,
            requestedCode = request.profileBackgroundCode,
            requested = request.profileBackgroundCodeSet,
            currentCode = existingCustomization?.profileBackgroundCode,
            kind = PROFILE_COSMETIC_BACKGROUND,
        )
        val favoriteBadgeOptions = favoriteBadgePlayerOptions(userId)
        val favoriteBadgeFantasyPlayerId = request.favoriteBadgeFantasyPlayerId
        if (favoriteBadgeFantasyPlayerId != null && favoriteBadgeOptions.none { it.fantasyPlayerId == favoriteBadgeFantasyPlayerId }) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Favorite badge player is not eligible")
        }

        val claimedByCode = userAchievementRepository.findClaimedWithDefinitions(userId)
            .associateBy { it.achievement!!.code }
        val achievements = request.featuredAchievementCodes.map { code ->
            claimedByCode[code]?.achievement
                ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Featured achievement is not claimed")
        }

        val customization = existingCustomization ?: UserProfileCustomization(telegramUser = managedUser)
        customization.profileFrameCode = selectedFrameCode
        customization.profileTitleCode = selectedTitleCode
        customization.profileAccentCode = selectedAccentCode
        customization.profileBackgroundCode = selectedBackgroundCode
        customization.favoriteBadgeFantasyPlayerId = favoriteBadgeFantasyPlayerId
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
                toBadge(definition, internalTelegramUserId)
            }
        val disabledFeaturedClaimed = featuredAchievements.count { it.code !in enabledPublicCodes }
        val customization = customizationRepository.findByTelegramUser_Id(internalTelegramUserId)

        return PlayerAchievementShowcaseDto(
            achievementSummary = PlayerAchievementSummaryDto(
                completed = itemsByDefinition.count { (_, item) -> item.state == "COMPLETED_UNCLAIMED" || item.state == "CLAIMED" },
                claimed = itemsByDefinition.count { (_, item) -> item.state == "CLAIMED" } + disabledFeaturedClaimed,
                totalVisible = definitions.size,
            ),
            profileFrame = currentProfileFrameCode(internalTelegramUserId, customization)?.let { ProfileFrameDto(it, frameName(it), null) },
            profileTitle = currentProfileCosmetic(
                internalTelegramUserId,
                customization?.profileTitleCode,
                PROFILE_COSMETIC_TITLE,
            ),
            profileAccent = currentProfileCosmetic(
                internalTelegramUserId,
                customization?.profileAccentCode,
                PROFILE_COSMETIC_ACCENT,
            ),
            profileBackground = currentProfileCosmetic(
                internalTelegramUserId,
                customization?.profileBackgroundCode,
                PROFILE_COSMETIC_BACKGROUND,
            ),
            featuredAchievements = featuredAchievements,
            nextAchievement = nextAchievement(itemsByDefinition),
        )
    }

    private fun currentProfileFrameCode(
        telegramUserId: Long,
        customization: UserProfileCustomization? = customizationRepository.findByTelegramUser_Id(telegramUserId),
    ): String? {
        val selected = customization?.profileFrameCode ?: return null
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

    private fun selectedCosmeticCode(
        userId: Long,
        requestedCode: String?,
        requested: Boolean,
        currentCode: String?,
        kind: String,
    ): String? {
        val nextCode = if (requested) requestedCode else currentCode
        if (nextCode == null) return null
        if (!requested && currentProfileCosmetic(userId, nextCode, kind) == null) return null
        if (requested) validateProfileCosmetic(userId, nextCode, kind)
        return nextCode
    }

    private fun validateProfileCosmetic(userId: Long, code: String, kind: String) {
        val cosmetic = profileCosmeticRepository.findByCodeAndEnabledTrue(code)
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Profile cosmetic is not available")
        if (cosmetic.kind != kind) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Profile cosmetic has wrong kind")
        }
        if (!hasProfileCosmeticUnlock(userId, code)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Profile cosmetic is not unlocked")
        }
    }

    private fun currentProfileCosmetic(userId: Long, code: String?, kind: String): ProfileCosmeticDto? {
        val selected = code?.takeIf { it.isNotBlank() } ?: return null
        val cosmetic = profileCosmeticRepository.findByCodeAndEnabledTrue(selected) ?: return null
        if (cosmetic.kind != kind) return null
        if (!hasProfileCosmeticUnlock(userId, selected)) return null
        return toProfileCosmeticDto(cosmetic)
    }

    private fun hasProfileCosmeticUnlock(userId: Long, code: String): Boolean =
        userCosmeticUnlockRepository.existsByTelegramUser_IdAndCosmeticTypeAndCosmeticCode(
            userId,
            PROFILE_COSMETIC_UNLOCK_TYPE,
            code,
        )

    private fun unlockedProfileCosmetics(userId: Long): ProfileCosmeticOptionsDto {
        val unlockedCodes = userCosmeticUnlockRepository.findAllByTelegramUser_IdAndCosmeticType(
            userId,
            PROFILE_COSMETIC_UNLOCK_TYPE,
        )
            .map { it.cosmeticCode }
            .toSet()
        if (unlockedCodes.isEmpty()) {
            return ProfileCosmeticOptionsDto(titles = emptyList(), accents = emptyList(), backgrounds = emptyList())
        }
        val cosmetics = profileCosmeticRepository.findAllByCodeInAndEnabledTrue(unlockedCodes)
            .sortedWith(compareBy<ProfileCosmetic> { it.displayOrder }.thenBy { it.code })
        val byKind = cosmetics.groupBy { it.kind }
        return ProfileCosmeticOptionsDto(
            titles = byKind[PROFILE_COSMETIC_TITLE].orEmpty().map { toProfileCosmeticDto(it) },
            accents = byKind[PROFILE_COSMETIC_ACCENT].orEmpty().map { toProfileCosmeticDto(it) },
            backgrounds = byKind[PROFILE_COSMETIC_BACKGROUND].orEmpty().map { toProfileCosmeticDto(it) },
        )
    }

    private fun toProfileCosmeticDto(cosmetic: ProfileCosmetic): ProfileCosmeticDto =
        ProfileCosmeticDto(
            code = cosmetic.code,
            kind = cosmetic.kind,
            name = cosmetic.name,
            description = cosmetic.description,
            styleToken = cosmetic.styleToken,
        )

    private fun favoriteBadgePlayerOptions(telegramUserId: Long): List<FavoriteBadgePlayerOptionDto> {
        val definition = achievementDefinitionRepository.findByCode(FAVORITE_PLAYER_ACHIEVEMENT_CODE) ?: return emptyList()
        return achievementProgressCalculator.samePlayerRarityCollectorCandidates(telegramUserId, definition)
            .map { candidate ->
                FavoriteBadgePlayerOptionDto(
                    fantasyPlayerId = candidate.fantasyPlayerId,
                    nickname = candidate.nickname,
                    rarityCount = candidate.rarityCount,
                )
            }
    }

    private fun toBadge(definition: AchievementDefinition, internalTelegramUserId: Long): AchievementBadgeDto =
        AchievementBadgeDto(
            code = definition.code,
            title = achievementCatalogService.displayTitle(definition, internalTelegramUserId),
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

    private fun frameName(code: String): String = frameNames[code] ?: code.replace('_', ' ')

    companion object {
        private const val MAX_FEATURED_ACHIEVEMENTS = 5
        private const val PROFILE_FRAME_TYPE = "PROFILE_FRAME"
        private const val PROFILE_COSMETIC_UNLOCK_TYPE = "COSMETIC_UNLOCK"
        private const val PROFILE_COSMETIC_TITLE = "TITLE"
        private const val PROFILE_COSMETIC_ACCENT = "ACCENT"
        private const val PROFILE_COSMETIC_BACKGROUND = "BACKGROUND"
        private const val FAVORITE_PLAYER_ACHIEVEMENT_CODE = "same_player_3_rarities"
        private val frameNames = mapOf(
            "budget_master" to "Мастер бюджета",
            "budget_master_elite" to "Элита бюджета",
            "budget_winner" to "Победитель бюджета",
            "collector" to "Коллекционер",
            "dynasty" to "Династия",
            "dynasty_elite" to "Элитная династия",
            "legendary_crafter" to "Легендарный крафтер",
            "pack_hunter" to "Охотник за паками",
            "stable_manager_elite" to "Элитный менеджер",
            "steady_result" to "Стабильный результат",
        )
    }
}
