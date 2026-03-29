package io.github.mralex1810.fantasy.repository

import io.github.mralex1810.fantasy.entity.TelegramUser
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface TelegramUserRepository : JpaRepository<TelegramUser, Long> {
    fun findByTelegramId(telegramId: Long): TelegramUser?

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE TelegramUser u SET u.fantiki = u.fantiki + :amount WHERE u.id = :id")
    fun addFantiki(@Param("id") id: Long, @Param("amount") amount: Long): Int

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        "UPDATE TelegramUser u SET u.fantiki = u.fantiki - :amount WHERE u.id = :id AND u.fantiki >= :amount",
    )
    fun deductFantikiIfSufficient(@Param("id") id: Long, @Param("amount") amount: Long): Int
}
