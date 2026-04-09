package io.github.mralex1810.fantasy.repository

import io.github.mralex1810.fantasy.entity.UserCardOwnershipHistory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface UserCardOwnershipHistoryRepository : JpaRepository<UserCardOwnershipHistory, Long> {
    fun existsByUserCard_IdAndTelegramUser_Id(userCardId: Long, telegramUserId: Long): Boolean

    @Query(
        """
        SELECT h FROM UserCardOwnershipHistory h
        JOIN FETCH h.telegramUser
        WHERE h.userCard.id = :userCardId
        ORDER BY h.acquiredAt ASC
        """,
    )
    fun findAllByUserCard_IdOrderByAcquiredAtAsc(@Param("userCardId") userCardId: Long): List<UserCardOwnershipHistory>
}
