package io.github.mralex1810.fantasy.repository

import io.github.mralex1810.fantasy.entity.CardMergeOperation
import io.github.mralex1810.fantasy.entity.UserCardMergePreview
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface UserCardMergePreviewRepository : JpaRepository<UserCardMergePreview, Long> {

    fun findByTelegramUser_IdAndOperationAndInputSetHashAndConsumedAtIsNull(
        telegramUserId: Long,
        operation: CardMergeOperation,
        inputSetHash: String,
    ): UserCardMergePreview?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
        SELECT p FROM UserCardMergePreview p
        LEFT JOIN FETCH p.resultUserCard
        WHERE p.id = :id
          AND p.telegramUser.id = :telegramUserId
        """,
    )
    fun findByIdAndTelegramUser_IdForUpdate(
        @Param("id") id: Long,
        @Param("telegramUserId") telegramUserId: Long,
    ): UserCardMergePreview?
}
