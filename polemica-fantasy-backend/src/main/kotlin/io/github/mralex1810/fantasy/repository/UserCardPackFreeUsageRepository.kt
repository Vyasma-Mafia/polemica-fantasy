package io.github.mralex1810.fantasy.repository

import io.github.mralex1810.fantasy.entity.UserCardPackFreeUsage
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface UserCardPackFreeUsageRepository : JpaRepository<UserCardPackFreeUsage, Long> {

    fun findAllByTelegramUser_IdAndCardPack_IdIn(
        telegramUserId: Long,
        cardPackIds: Collection<Long>,
    ): List<UserCardPackFreeUsage>

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        value =
            """
            INSERT INTO user_card_pack_free_usage (telegram_user_id, card_pack_id, free_opens_used)
            VALUES (:uid, :packId, 0)
            ON CONFLICT (telegram_user_id, card_pack_id) DO NOTHING
            """,
        nativeQuery = true,
    )
    fun ensureUsageRow(@Param("uid") uid: Long, @Param("packId") packId: Long): Int

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        UPDATE UserCardPackFreeUsage u
        SET u.freeOpensUsed = u.freeOpensUsed + 1
        WHERE u.telegramUser.id = :uid AND u.cardPack.id = :packId AND u.freeOpensUsed < :limit
        """,
    )
    fun incrementIfBelowLimit(
        @Param("uid") uid: Long,
        @Param("packId") packId: Long,
        @Param("limit") limit: Int,
    ): Int
}
