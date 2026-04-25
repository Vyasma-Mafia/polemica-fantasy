package io.github.mralex1810.fantasy.repository

import io.github.mralex1810.fantasy.entity.TournamentSubscription
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface TournamentSubscriptionRepository : JpaRepository<TournamentSubscription, Long> {
    fun findAllByTelegramUser_Id(telegramUserId: Long): List<TournamentSubscription>

    fun deleteAllByTelegramUser_Id(telegramUserId: Long)

    fun existsByTelegramUser_Id(telegramUserId: Long): Boolean

    @Query("SELECT ts.tournament.id FROM TournamentSubscription ts WHERE ts.telegramUser.id = :telegramUserId")
    fun findTournamentIdsByTelegramUser_Id(@Param("telegramUserId") telegramUserId: Long): List<Long>

    @Query(
        "SELECT ts.telegramUser.id, ts.tournament.id FROM TournamentSubscription ts WHERE ts.telegramUser.id IN :telegramUserIds",
    )
    fun findUserTournamentPairsByTelegramUserIds(@Param("telegramUserIds") telegramUserIds: Collection<Long>): List<Array<Any>>
}
