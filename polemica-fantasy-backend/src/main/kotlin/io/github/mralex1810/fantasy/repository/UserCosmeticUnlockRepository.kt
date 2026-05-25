package io.github.mralex1810.fantasy.repository

import io.github.mralex1810.fantasy.entity.UserCosmeticUnlock
import io.github.mralex1810.fantasy.entity.UserCosmeticUnlockId
import org.springframework.data.jpa.repository.JpaRepository

interface UserCosmeticUnlockRepository : JpaRepository<UserCosmeticUnlock, UserCosmeticUnlockId> {
    fun findAllByTelegramUser_IdAndCosmeticType(telegramUserId: Long, cosmeticType: String): List<UserCosmeticUnlock>

    fun findAllByTelegramUser_IdInAndCosmeticType(
        telegramUserIds: Collection<Long>,
        cosmeticType: String,
    ): List<UserCosmeticUnlock>

    fun existsByTelegramUser_IdAndCosmeticTypeAndCosmeticCode(
        telegramUserId: Long,
        cosmeticType: String,
        cosmeticCode: String,
    ): Boolean
}
