package io.github.mralex1810.fantasy.repository

import io.github.mralex1810.fantasy.entity.ProductEvent
import org.springframework.data.jpa.repository.JpaRepository

interface ProductEventRepository : JpaRepository<ProductEvent, Long> {
    fun existsByTelegramUser_IdAndEventTypeAndSubjectTypeAndSubjectId(
        telegramUserId: Long,
        eventType: String,
        subjectType: String?,
        subjectId: Long?,
    ): Boolean
}
