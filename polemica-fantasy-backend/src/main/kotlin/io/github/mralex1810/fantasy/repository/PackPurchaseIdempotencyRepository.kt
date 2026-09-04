package io.github.mralex1810.fantasy.repository

import io.github.mralex1810.fantasy.entity.PackPurchaseIdempotency
import org.springframework.data.jpa.repository.JpaRepository

interface PackPurchaseIdempotencyRepository : JpaRepository<PackPurchaseIdempotency, Long> {
    fun findByTelegramUser_IdAndKeyHash(telegramUserId: Long, keyHash: String): PackPurchaseIdempotency?
}
