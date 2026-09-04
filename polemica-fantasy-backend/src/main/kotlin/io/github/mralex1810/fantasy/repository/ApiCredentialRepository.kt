package io.github.mralex1810.fantasy.repository

import io.github.mralex1810.fantasy.entity.ApiCredential
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ApiCredentialRepository : JpaRepository<ApiCredential, Long> {
    @Query("SELECT c FROM ApiCredential c JOIN FETCH c.telegramUser WHERE c.tokenHash = :tokenHash")
    fun findByTokenHash(@Param("tokenHash") tokenHash: String): ApiCredential?

    fun findAllByTelegramUser_IdOrderByCreatedAtDesc(telegramUserId: Long): List<ApiCredential>

    fun findByIdAndTelegramUser_Id(id: Long, telegramUserId: Long): ApiCredential?
}
