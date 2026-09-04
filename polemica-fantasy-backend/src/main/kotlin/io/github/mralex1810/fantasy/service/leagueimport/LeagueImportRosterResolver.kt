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

data class LeagueImportResolvedRosterSubstitution(
    val announcementText: String,
    val outgoingTournamentPlayerId: Long,
    val outgoingNickname: String,
    val incomingTournamentPlayerId: Long,
    val incomingNickname: String,
)

data class LeagueImportRosterDraft(
    val status: String,
    val expectedCount: Int,
    val resolved: List<LeagueImportResolvedRosterPlayer>,
    val substitutions: List<LeagueImportResolvedRosterSubstitution> = emptyList(),
    val issues: List<String>,
    val mediaSha256: String?,
    val ocrChecksum: String?,
    val resolverVersion: String = RESOLVER_VERSION,
) {
    val ready: Boolean get() = status == "READY"

    companion object {
        const val RESOLVER_VERSION = "exact-tournament-nickname-v4-doc-punctuation-alias-binding"
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
        announcementText: String = "",
    ): LeagueImportRosterResolution {
        val policy = properties.policy(league)
        val expectedCount = policy?.expectedRosterCount ?: 0
        if (media == null) return resolution(
            LeagueImportRosterDraft("NO_MEDIA", expectedCount, emptyList(), issues = emptyList(), mediaSha256 = null, ocrChecksum = null),
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

        if (ROLE_MARKER_PATTERN.containsMatchIn(normalize(media.ocr.fullText))) {
            return review(media, expectedCount, evidenceHash, "commentator or substitution text requires manual review")
        }

        val tournamentPlayers = tournamentPlayerRepository.findAllByTournamentIdWithFantasyPlayer(tournamentId)
        val byNickname = tournamentPlayers.groupBy { normalize(it.fantasyPlayer!!.nickname) }
        val aliasesByOcrText = policy?.rosterNicknameAliases.orEmpty().entries.groupBy { normalize(it.key) }
        val resolved = mutableListOf<LeagueImportResolvedRosterPlayer>()
        val ambiguous = mutableListOf<String>()
        val aliasIssues = mutableListOf<String>()
        media.ocr.lines.forEach { line ->
            val normalized = normalize(line.text)
            if (normalized.isEmpty()) return@forEach
            val exactMatches = byNickname[normalized].orEmpty()
            val configuredAliases = aliasesByOcrText[normalized].orEmpty()
            val matches = if (exactMatches.isNotEmpty()) exactMatches else configuredAliases
                .flatMap { byNickname[normalize(it.value)].orEmpty() }
                .distinctBy { it.id }
            when (matches.size) {
                0 -> if (configuredAliases.isNotEmpty()) {
                    aliasIssues += "configured OCR alias target is not exact-unique in tournament: ${line.text.trim()}"
                } // Headers and club names are expected OCR noise.
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

        val substitutions = mutableListOf<LeagueImportResolvedRosterSubstitution>()
        val substitutionIssues = mutableListOf<String>()
        val captionMarkerLines = announcementText.lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .filter { ROLE_MARKER_PATTERN.containsMatchIn(normalize(it)) }
            .toList()
        captionMarkerLines.forEach { line ->
            val parsed = parseSubstitution(line)
            if (parsed == null) {
                substitutionIssues += "unsupported commentator or substitution text: $line"
                return@forEach
            }
            val incomingMatches = byNickname[normalize(parsed.incoming)].orEmpty()
            val outgoingResolved = resolved.filter {
                normalize(it.productionNickname) == normalize(parsed.outgoing) ||
                    normalize(parsed.outgoing) in russianGenitiveForms(it.productionNickname)
            }.distinctBy { it.tournamentPlayerId }
            if (outgoingResolved.size != 1) {
                substitutionIssues += "substitution outgoing player is not exact-unique: ${parsed.outgoing}"
                return@forEach
            }
            if (incomingMatches.size != 1) {
                substitutionIssues += "substitution incoming player is not exact-unique: ${parsed.incoming}"
                return@forEach
            }
            val outgoingResolvedPlayer = outgoingResolved.single()
            val outgoing = tournamentPlayers.single { it.id == outgoingResolvedPlayer.tournamentPlayerId }
            val incoming = incomingMatches.single()
            val outgoingIndexes = resolved.indices.filter { resolved[it].tournamentPlayerId == outgoing.id }
            if (outgoingIndexes.size != 1) {
                substitutionIssues += "substitution outgoing player is not present exactly once in OCR roster: ${outgoing.fantasyPlayer!!.nickname}"
                return@forEach
            }
            if (resolved.any { it.tournamentPlayerId == incoming.id }) {
                substitutionIssues += "substitution incoming player is already present in OCR roster: ${incoming.fantasyPlayer!!.nickname}"
                return@forEach
            }
            val index = outgoingIndexes.single()
            resolved[index] = LeagueImportResolvedRosterPlayer(
                ocrText = "${incoming.fantasyPlayer!!.nickname} (замена из подписи)",
                tournamentPlayerId = incoming.id!!,
                fantasyPlayerId = incoming.fantasyPlayer!!.id!!,
                productionNickname = incoming.fantasyPlayer!!.nickname,
            )
            substitutions += LeagueImportResolvedRosterSubstitution(
                announcementText = line,
                outgoingTournamentPlayerId = outgoing.id!!,
                outgoingNickname = outgoing.fantasyPlayer!!.nickname,
                incomingTournamentPlayerId = incoming.id!!,
                incomingNickname = incoming.fantasyPlayer!!.nickname,
            )
        }

        val duplicates = resolved.groupBy { it.tournamentPlayerId }.filterValues { it.size > 1 }.values.flatten()
        val issues = buildList {
            if (ambiguous.isNotEmpty()) add("ambiguous exact nicknames: ${ambiguous.distinct().joinToString(", ")}")
            addAll(aliasIssues)
            addAll(substitutionIssues)
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
            substitutions = substitutions,
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
        LeagueImportRosterDraft(
            status, expectedCount, emptyList(), issues = listOf(issue),
            mediaSha256 = media.sha256, ocrChecksum = media.ocr.checksum,
        ),
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
            draft.substitutions.forEach {
                add("substitution:${it.announcementText}|${it.outgoingTournamentPlayerId}|${it.outgoingNickname}|${it.incomingTournamentPlayerId}|${it.incomingNickname}")
            }
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

    private fun russianGenitiveForms(value: String): Set<String> {
        val normalized = normalize(value)
        val separator = normalized.lastIndexOf(' ')
        val prefix = if (separator >= 0) normalized.substring(0, separator + 1) else ""
        val word = if (separator >= 0) normalized.substring(separator + 1) else normalized
        if (word.isEmpty()) return emptySet()
        val inflected = when {
            word.endsWith("й") || word.endsWith("ь") -> word.dropLast(1) + "я"
            word.endsWith("я") -> word.dropLast(1) + "и"
            word.endsWith("а") -> word.dropLast(1) + if (word.dropLast(1).lastOrNull() in RUSSIAN_I_SUFFIX_CONSONANTS) "и" else "ы"
            word.last() in RUSSIAN_CONSONANTS -> word + "а"
            else -> return emptySet()
        }
        return setOf(prefix + inflected)
    }

    private fun parseSubstitution(line: String): ParsedSubstitution? = SUBSTITUTION_PATTERNS.firstNotNullOfOrNull { pattern ->
        pattern.matchEntire(line)?.let { match ->
            ParsedSubstitution(match.groupValues[1].trim(), match.groupValues[2].trim())
        }
    }

    private companion object {
        data class ParsedSubstitution(val outgoing: String, val incoming: String)

        val SUPPORTED_MIME_TYPES = setOf("image/jpeg", "image/png")
        const val MAX_IMAGE_BYTES = 10L * 1024 * 1024
        const val MAX_IMAGE_PIXELS = 20_000_000L
        val WHITESPACE = Regex("\\s+")
        val ROLE_MARKER_PATTERN = Regex(
            """(?iu)(?<!\p{L})(?:комментатор\p{L}*|замен\p{L}*|вместо|replacement)(?!\p{L})""",
        )
        val SUBSTITUTION_PATTERNS = listOf(
            Regex(
                """^[^\p{L}\p{N}]*заменой\s+(.+?)\s+(?:выступит|выступает|сыграет)\s+(.+?)[.!]?\s*$""",
                RegexOption.IGNORE_CASE,
            ),
            Regex(
                """^[^\p{L}\p{N}]*вместо\s+(.+?)\s+(?:выступит|выступает|сыграет)\s+(.+?)[.!]?\s*$""",
                RegexOption.IGNORE_CASE,
            ),
        )
        val RUSSIAN_CONSONANTS = "бвгджзйклмнпрстфхцчшщь".toSet()
        val RUSSIAN_I_SUFFIX_CONSONANTS = "гкхжчшщ".toSet()
    }
}
