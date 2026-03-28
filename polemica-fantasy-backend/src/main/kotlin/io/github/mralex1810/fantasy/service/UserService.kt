package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.dto.user.response.UserProfileDto
import io.github.mralex1810.fantasy.entity.TelegramUser
import io.github.mralex1810.fantasy.repository.TelegramUserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserService(
    private val telegramUserRepository: TelegramUserRepository,
) {

    @Transactional
    fun getOrCreateAndUpdateProfile(telegramId: Long, username: String?, firstName: String?): TelegramUser {
        val existing = telegramUserRepository.findByTelegramId(telegramId)
        return if (existing != null) {
            username?.let { existing.username = it }
            firstName?.let { existing.firstName = it }
            telegramUserRepository.save(existing)
        } else {
            telegramUserRepository.save(
                TelegramUser(
                    telegramId = telegramId,
                    username = username,
                    firstName = firstName,
                ),
            )
        }
    }

    fun toProfileDto(user: TelegramUser): UserProfileDto =
        UserProfileDto(
            id = user.id!!,
            telegramId = user.telegramId,
            username = user.username,
            firstName = user.firstName,
            createdAt = user.createdAt,
        )
}
