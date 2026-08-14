package io.github.mralex1810.fantasy.service.leagueimport

import io.github.mralex1810.fantasy.config.TelegramLeagueImportProperties
import io.github.mralex1810.fantasy.dto.internal.TelegramLeagueImportMediaEvidence
import io.github.mralex1810.fantasy.repository.TournamentPlayerRepository
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

data class LeagueImportResolvedRosterPlayer(
    val ocrText: String,
    val tournamentPlayerId: Long,
    val fantasyPlayerId: Long,
    val productionNickname: String,
)

data class LeagueImportRosterDraft(
    val status: String,
    val expectedCount: Int,
    val resolved: List<LeagueImportResolvedRosterPlayer>,
    val issues: List<String>,
    val mediaSha256: String?,
    val ocrChecksum: String?,
    val resolverVersion: String = RESOLVER_VERSION,
) {
    val ready: Boolean get() = status == "READY"

    companion object {
        const val RESOLVER_VERSION = "exact-tournament-nickname-v1"
    }
}

data class LeagueImportRosterResolution(
    val draft: LeagueImportRosterDraft,
    val checksum: String,
)

@Component
class LeagueImportRosterResolver(
    private val properties: TelegramLeagueImportProperties,
    private val tournamentPlayerRepository: TournamentPlayerRepository,
) {
    fun resolve(
        league: String,
        tournamentId: Long,
        evidenceHash: String,
        media: TelegramLeagueImportMediaEvidence?,
    ): LeagueImportRosterResolution {
        val expectedCount = properties.policy(league)?.expectedRosterCount ?: 0
        if (media == null) return resolution(
            LeagueImportRosterDraft("NO_MEDIA", expectedCount, emptyList(), emptyList(), null, null),
            evidenceHash,
        )
        if (media.groupedId != null) return review(media, expectedCount, evidenceHash, "albums are not supported")
        if (media.ocr.status != "SUCCESS") {
            val status = if (media.ocr.status == "UNSUPPORTED") "UNSUPPORTED" else "OCR_FAILED"
            return review(media, expectedCount, evidenceHash, "OCR did not complete successfully: ${media.ocr.errorCode ?: media.ocr.status}", status)
        }
        if (media.mimeType !in SUPPORTED_MIME_TYPES) return review(media, expectedCount, evidenceHash, "unsupported image MIME type", "UNSUPPORTED")
        val byteSize = media.byteSize ?: 0
        val width = media.width ?: 0
        val height = media.height ?: 0
        if (byteSize !in 1..MAX_IMAGE_BYTES || width <= 0 || height <= 0 ||
            width.toLong() * height.toLong() > MAX_IMAGE_PIXELS) {
            return review(media, expectedCount, evidenceHash, "image size or dimensions are outside the safe limits", "UNSUPPORTED")
        }
        if (expectedCount <= 0) return review(media, expectedCount, evidenceHash, "expected roster count is not configured")

        val normalizedFullText = normalize(media.ocr.fullText)
        if (BLOCKING_ROLE_WORDS.any { normalizedFullText.contains(it) }) {
            return review(media, expectedCount, evidenceHash, "commentator or substitution text requires manual review")
        }

        val tournamentPlayers = tournamentPlayerRepository.findAllByTournamentIdWithFantasyPlayer(tournamentId)
        val byNickname = tournamentPlayers.groupBy { normalize(it.fantasyPlayer!!.nickname) }
        val resolved = mutableListOf<LeagueImportResolvedRosterPlayer>()
        val ambiguous = mutableListOf<String>()
        media.ocr.lines.forEach { line ->
            val normalized = normalize(line.text)
            if (normalized.isEmpty()) return@forEach
            val matches = byNickname[normalized].orEmpty()
            when (matches.size) {
                0 -> Unit // Headers and club names are expected OCR noise.
                1 -> {
                    val player = matches.single()
                    resolved += LeagueImportResolvedRosterPlayer(
                        ocrText = line.text.trim(),
                        tournamentPlayerId = player.id!!,
                        fantasyPlayerId = player.fantasyPlayer!!.id!!,
                        productionNickname = player.fantasyPlayer!!.nickname,
                    )
                }
                else -> ambiguous += line.text.trim()
            }
        }

        val duplicates = resolved.groupBy { it.tournamentPlayerId }.filterValues { it.size > 1 }.values.flatten()
        val issues = buildList {
            if (ambiguous.isNotEmpty()) add("ambiguous exact nicknames: ${ambiguous.distinct().joinToString(", ")}")
            if (duplicates.isNotEmpty()) add("duplicate OCR players: ${duplicates.map { it.productionNickname }.distinct().joinToString(", ")}")
            if (resolved.map { it.tournamentPlayerId }.distinct().size != expectedCount) {
                add("expected $expectedCount unique tournament players, found ${resolved.map { it.tournamentPlayerId }.distinct().size}")
            }
        }
        val ready = issues.isEmpty()
        val draft = LeagueImportRosterDraft(
            status = if (ready) "READY" else "NEEDS_REVIEW",
            expectedCount = expectedCount,
            resolved = if (ready) resolved else resolved.distinctBy { it.tournamentPlayerId },
            issues = issues,
            mediaSha256 = media.sha256,
            ocrChecksum = media.ocr.checksum,
        )
        return resolution(draft, evidenceHash)
    }

    private fun review(
        media: TelegramLeagueImportMediaEvidence,
        expectedCount: Int,
        evidenceHash: String,
        issue: String,
        status: String = "NEEDS_REVIEW",
    ) = resolution(
        LeagueImportRosterDraft(status, expectedCount, emptyList(), listOf(issue), media.sha256, media.ocr.checksum),
        evidenceHash,
    )

    private fun resolution(draft: LeagueImportRosterDraft, evidenceHash: String): LeagueImportRosterResolution {
        val material = buildList {
            add("v1")
            add(evidenceHash)
            add(draft.resolverVersion)
            add(draft.status)
            add(draft.expectedCount.toString())
            add(draft.mediaSha256 ?: "-")
            add(draft.ocrChecksum ?: "-")
            draft.resolved.forEach { add("${it.ocrText}|${it.tournamentPlayerId}|${it.fantasyPlayerId}|${it.productionNickname}") }
            draft.issues.forEach { add("issue:$it") }
        }.joinToString("\n")
        return LeagueImportRosterResolution(draft, sha256(material))
    }

    private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT)
        .trim()
        .replace(WHITESPACE, " ")

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    private companion object {
        val SUPPORTED_MIME_TYPES = setOf("image/jpeg", "image/png")
        const val MAX_IMAGE_BYTES = 10L * 1024 * 1024
        const val MAX_IMAGE_PIXELS = 20_000_000L
        val WHITESPACE = Regex("\\s+")
        val BLOCKING_ROLE_WORDS = setOf("комментатор", "комментаторы", "замена", "вместо", "replacement")
    }
}
