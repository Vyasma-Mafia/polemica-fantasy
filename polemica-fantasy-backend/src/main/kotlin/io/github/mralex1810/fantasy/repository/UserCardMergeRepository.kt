package io.github.mralex1810.fantasy.repository

import io.github.mralex1810.fantasy.entity.UserCardMerge
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface UserCardMergeRepository : JpaRepository<UserCardMerge, Long> {

    fun findByPreview_Id(previewId: Long): UserCardMerge?

    @Query(
        value = """
        SELECT m FROM UserCardMerge m
        JOIN FETCH m.telegramUser
        JOIN FETCH m.resultUserCard ruc
        JOIN FETCH ruc.cardTemplate rct
        JOIN FETCH rct.fantasyPlayer
        JOIN FETCH m.fantasyPlayer
        WHERE (:telegramUserId IS NULL OR m.telegramUser.telegramId = :telegramUserId)
          AND (:resultUserCardId IS NULL OR ruc.id = :resultUserCardId)
        """,
        countQuery = """
        SELECT COUNT(m) FROM UserCardMerge m
        JOIN m.resultUserCard ruc
        WHERE (:telegramUserId IS NULL OR m.telegramUser.telegramId = :telegramUserId)
          AND (:resultUserCardId IS NULL OR ruc.id = :resultUserCardId)
        """,
    )
    fun findAllFiltered(
        @Param("telegramUserId") telegramUserId: Long?,
        @Param("resultUserCardId") resultUserCardId: Long?,
        pageable: Pageable,
    ): Page<UserCardMerge>

    @Query(
        """
        SELECT DISTINCT m FROM UserCardMerge m
        JOIN FETCH m.telegramUser
        JOIN FETCH m.resultUserCard ruc
        JOIN FETCH ruc.cardTemplate rct
        JOIN FETCH rct.fantasyPlayer
        JOIN FETCH m.fantasyPlayer
        LEFT JOIN FETCH m.preview
        LEFT JOIN FETCH m.inputs i
        LEFT JOIN FETCH i.inputUserCard
        LEFT JOIN FETCH i.inputCardTemplate
        WHERE m.id = :id
        """,
    )
    fun findByIdWithDetails(@Param("id") id: Long): UserCardMerge?
}
