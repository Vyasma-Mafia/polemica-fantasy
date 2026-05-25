package io.github.mralex1810.fantasy.service.achievement

import io.github.mralex1810.fantasy.repository.UserCosmeticUnlockRepository
import io.github.mralex1810.fantasy.repository.UserProfileCustomizationRepository
import org.springframework.stereotype.Service

@Service
class ProfileFrameVisibilityService(
    private val customizationRepository: UserProfileCustomizationRepository,
    private val userCosmeticUnlockRepository: UserCosmeticUnlockRepository,
) {
    fun selectedFrameCodes(telegramUserIds: Collection<Long>): Map<Long, String> {
        if (telegramUserIds.isEmpty()) return emptyMap()
        val distinctUserIds = telegramUserIds.distinct()
        val selectedByUserId = customizationRepository.findAllByTelegramUserIdIn(distinctUserIds)
            .mapNotNull { customization ->
                val userId = customization.telegramUserId ?: return@mapNotNull null
                val frameCode = customization.profileFrameCode?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                userId to frameCode
            }
            .toMap()
        if (selectedByUserId.isEmpty()) return emptyMap()

        val unlockedPairs = userCosmeticUnlockRepository
            .findAllByTelegramUser_IdInAndCosmeticType(selectedByUserId.keys, PROFILE_FRAME_TYPE)
            .mapNotNull { unlock ->
                val userId = unlock.telegramUser?.id ?: return@mapNotNull null
                userId to unlock.cosmeticCode
            }
            .toSet()

        return selectedByUserId.filter { (userId, frameCode) -> userId to frameCode in unlockedPairs }
    }

    fun selectedFrameCode(telegramUserId: Long): String? = selectedFrameCodes(listOf(telegramUserId))[telegramUserId]

    private companion object {
        private const val PROFILE_FRAME_TYPE = "PROFILE_FRAME"
    }
}
