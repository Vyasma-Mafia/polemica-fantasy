package io.github.mralex1810.fantasy.repository

import io.github.mralex1810.fantasy.entity.FantikiTransaction
import io.github.mralex1810.fantasy.entity.FantikiTransactionReason
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface FantikiTransactionRepository : JpaRepository<FantikiTransaction, Long> {
    @Query(
        "SELECT t.telegramUser.id, SUM(t.amount) FROM FantikiTransaction t " +
            "WHERE t.reason = :reason AND t.amount > 0 " +
            "GROUP BY t.telegramUser.id",
    )
    fun sumPositiveAmountsByUserIdForReason(
        @Param("reason") reason: FantikiTransactionReason,
    ): List<Array<Any?>>

    @Query(
        value = """
            SELECT t
            FROM FantikiTransaction t
            JOIN FETCH t.telegramUser u
            WHERE u.telegramId = :telegramUserId
              AND t.reason IN :reasons
            """,
        countQuery = """
            SELECT COUNT(t)
            FROM FantikiTransaction t
            JOIN t.telegramUser u
            WHERE u.telegramId = :telegramUserId
              AND t.reason IN :reasons
            """,
    )
    fun findManualAdjustmentsByTelegramId(
        @Param("telegramUserId") telegramUserId: Long,
        @Param("reasons") reasons: Collection<FantikiTransactionReason>,
        pageable: Pageable,
    ): Page<FantikiTransaction>
}
