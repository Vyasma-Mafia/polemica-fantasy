package io.github.mralex1810.fantasy.repository

import io.github.mralex1810.fantasy.entity.TelegramSupportTopic
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface TelegramSupportTopicRepository : JpaRepository<TelegramSupportTopic, Long> {
    fun findByTelegramUser_Id(telegramUserId: Long): TelegramSupportTopic?

    @Query(
        """
        SELECT t FROM TelegramSupportTopic t
        JOIN FETCH t.telegramUser
        WHERE t.forumMessageThreadId = :forumMessageThreadId
        """,
    )
    fun findByForumMessageThreadIdWithUser(@Param("forumMessageThreadId") forumMessageThreadId: Int): TelegramSupportTopic?
}
