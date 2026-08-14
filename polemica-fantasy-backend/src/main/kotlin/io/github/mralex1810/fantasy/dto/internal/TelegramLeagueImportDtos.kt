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
    val evidenceHash: String? = null,
    val rawText: String,
    val mediaEvidence: TelegramLeagueImportMediaEvidence? = null,
    val classification: String? = null,
    val league: String? = null,
)

data class TelegramLeagueImportMediaEvidence(
    val telegramMediaId: String,
    val groupedId: Long? = null,
    val mimeType: String? = null,
    val byteSize: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val sha256: String? = null,
    val ocr: TelegramLeagueImportOcrEvidence,
)

data class TelegramLeagueImportOcrEvidence(
    val status: String,
    val provider: String,
    val model: String,
    val languageCodes: List<String>,
    val checksum: String,
    val fullText: String = "",
    val lines: List<TelegramLeagueImportOcrLine> = emptyList(),
    val errorCode: String? = null,
)

data class TelegramLeagueImportOcrLine(
    val text: String,
    val boundingBox: List<TelegramLeagueImportPoint> = emptyList(),
)

data class TelegramLeagueImportPoint(
    val x: Int,
    val y: Int,
)

data class TelegramLeagueImportEventResponse(
    val itemId: Long?,
    val state: String,
    val version: Long?,
    val duplicate: Boolean,
)
