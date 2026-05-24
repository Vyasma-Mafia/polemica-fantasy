package io.github.mralex1810.fantasy.service

import com.fasterxml.jackson.databind.JsonNode
import io.github.mralex1810.fantasy.dto.user.request.ProductEventRequest
import io.github.mralex1810.fantasy.entity.ProductEvent
import io.github.mralex1810.fantasy.repository.ProductEventRepository
import io.github.mralex1810.fantasy.repository.TelegramUserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class ProductEventService(
    private val productEventRepository: ProductEventRepository,
    private val telegramUserRepository: TelegramUserRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun record(userId: Long, request: ProductEventRequest) {
        record(
            userId = userId,
            eventType = request.eventType,
            campaignId = request.campaignId,
            releaseNoteId = request.releaseNoteId,
            subjectType = request.subjectType,
            subjectId = request.subjectId,
            source = request.source ?: "TMA",
            metadata = request.metadata,
        )
    }

    @Transactional
    fun record(
        userId: Long,
        eventType: String,
        campaignId: Long? = null,
        releaseNoteId: Long? = null,
        subjectType: String? = null,
        subjectId: Long? = null,
        source: String = "SERVER",
        metadata: JsonNode? = null,
    ) {
        val normalizedType = eventType.trim().uppercase()
        if (normalizedType.isBlank()) {
            return
        }
        val user = telegramUserRepository.findById(userId).orElse(null)
        if (user == null) {
            log.debug("Skipping product event {} for missing userId={}", normalizedType, userId)
            return
        }
        productEventRepository.save(
            ProductEvent(
                telegramUser = user,
                eventType = normalizedType.take(64),
                campaignId = campaignId,
                releaseNoteId = releaseNoteId,
                subjectType = subjectType?.trim()?.takeIf { it.isNotEmpty() }?.take(64),
                subjectId = subjectId,
                source = source.trim().ifBlank { "SERVER" }.take(64),
                metadata = metadata,
                createdAt = Instant.now(),
            ),
        )
    }
}
