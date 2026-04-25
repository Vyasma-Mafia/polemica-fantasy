package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.dto.user.request.UpdateNotificationSettingsRequest
import io.github.mralex1810.fantasy.dto.user.response.NotificationCategoryDto
import io.github.mralex1810.fantasy.dto.user.response.NotificationSettingsResponse
import io.github.mralex1810.fantasy.entity.NotificationCategory
import io.github.mralex1810.fantasy.entity.NotificationPreference
import io.github.mralex1810.fantasy.repository.NotificationPreferenceRepository
import io.github.mralex1810.fantasy.repository.TelegramUserRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class NotificationSettingsService(
    private val notificationPreferenceRepository: NotificationPreferenceRepository,
    private val telegramUserRepository: TelegramUserRepository,
) {
    @Transactional(readOnly = true)
    fun getSettings(internalUserId: Long): NotificationSettingsResponse {
        val overrides = notificationPreferenceRepository
            .findAllByTelegramUser_Id(internalUserId)
            .associateBy { it.category }
        val categories = NotificationCategory.entries.map { category ->
            NotificationCategoryDto(
                category = category.name,
                enabled = overrides[category]?.enabled ?: category.enabledByDefault,
                toggleable = category.userToggleable,
                description = category.description,
            )
        }
        return NotificationSettingsResponse(categories = categories)
    }

    @Transactional
    fun updateSettings(
        internalUserId: Long,
        request: UpdateNotificationSettingsRequest,
    ): NotificationSettingsResponse {
        val user = telegramUserRepository.findById(internalUserId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "User $internalUserId not found")
        }
        for ((categoryNameRaw, enabled) in request.categories) {
            val categoryName = categoryNameRaw.trim()
            val category = try {
                NotificationCategory.valueOf(categoryName)
            } catch (_: IllegalArgumentException) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown notification category: $categoryName")
            }
            if (!category.userToggleable) {
                continue
            }
            val existing = notificationPreferenceRepository
                .findByTelegramUser_IdAndCategory(internalUserId, category)
            if (enabled == category.enabledByDefault) {
                if (existing != null) {
                    notificationPreferenceRepository.delete(existing)
                }
                continue
            }
            if (existing == null) {
                notificationPreferenceRepository.save(
                    NotificationPreference(
                        telegramUser = user,
                        category = category,
                        enabled = enabled,
                    ),
                )
            } else if (existing.enabled != enabled) {
                existing.enabled = enabled
                notificationPreferenceRepository.save(existing)
            }
        }
        return getSettings(internalUserId)
    }
}
