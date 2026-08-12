package io.github.mralex1810.fantasy.dto.internal

import java.time.Instant

data class TelegramLeagueImportEventRequest(
    val sourceChannelPeerId: Long,
    val messageId: Long,
    val revision: Int,
    val sourceVersion: String,
    val postedAt: Instant,
    val editedAt: Instant?,
    val contentHash: String,
    val rawText: String,
    val classification: String? = null,
    val league: String? = null,
)

data class TelegramLeagueImportEventResponse(
    val itemId: Long?,
    val state: String,
    val version: Long?,
    val duplicate: Boolean,
)
