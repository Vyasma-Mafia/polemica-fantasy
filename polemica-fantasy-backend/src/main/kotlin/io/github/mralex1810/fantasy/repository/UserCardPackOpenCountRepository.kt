package io.github.mralex1810.fantasy.repository

import io.github.mralex1810.fantasy.entity.UserCardPackOpenCount
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface UserCardPackOpenCountRepository : JpaRepository<UserCardPackOpenCount, Long> {

    fun findAllByTelegramUser_IdAndCardPack_IdIn(
        telegramUserId: Long,
        cardPackIds: Collection<Long>,
    ): List<UserCardPackOpenCount>

    fun findByTelegramUser_IdAndCardPack_Id(
        telegramUserId: Long,
        cardPackId: Long,
    ): UserCardPackOpenCount?

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        value =
            """
            INSERT INTO user_card_pack_opens (telegram_user_id, card_pack_id, open_count)
            VALUES (:uid, :packId, 1)
            ON CONFLICT (telegram_user_id, card_pack_id)
            DO UPDATE SET open_count = user_card_pack_opens.open_count + 1
            """,
        nativeQuery = true,
    )
    fun incrementOpenCount(@Param("uid") uid: Long, @Param("packId") packId: Long)
}
