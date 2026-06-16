package io.github.mralex1810.fantasy.repository

import io.github.mralex1810.fantasy.entity.UserCardPackChoice
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface UserCardPackChoiceRepository : JpaRepository<UserCardPackChoice, Long> {

    fun findByTelegramUser_IdAndCardPack_IdAndSelectedAtIsNull(
        telegramUserId: Long,
        cardPackId: Long,
    ): UserCardPackChoice?

    fun findAllByTelegramUser_IdAndCardPack_IdInAndSelectedAtIsNull(
        telegramUserId: Long,
        cardPackIds: Collection<Long>,
    ): List<UserCardPackChoice>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM UserCardPackChoice c WHERE c.id = :id AND c.telegramUser.id = :telegramUserId")
    fun findByIdAndTelegramUserIdForUpdate(
        @Param("id") id: Long,
        @Param("telegramUserId") telegramUserId: Long,
    ): UserCardPackChoice?
}
