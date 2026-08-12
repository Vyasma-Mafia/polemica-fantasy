package io.github.mralex1810.fantasy.service.leagueimport

import io.github.mralex1810.fantasy.config.TelegramLeagueImportProperties
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

data class LeagueAnnouncementDraft(
    val league: String,
    val seriesNumber: Long,
    val tournamentId: Long,
    val name: String,
    val namePrefix: String,
    val gameStartedOn: LocalDate,
    val startsAt: Instant,
    val teamDeadline: Instant,
    val expectedGameCount: Int,
    val sourceUrl: String,
)

sealed interface LeagueAnnouncementParseResult {
    data class Ready(val draft: LeagueAnnouncementDraft, val checksum: String) : LeagueAnnouncementParseResult
    data class Blocked(val reason: String) : LeagueAnnouncementParseResult
}

@Component
class LeagueAnnouncementParser(
    private val properties: TelegramLeagueImportProperties,
) {
    fun parse(text: String, postedAt: Instant, sourceUrl: String): LeagueAnnouncementParseResult {
        val normalized = text.lowercase().replace('ё', 'е')
        val tags = TAG.findAll(normalized).map { it.groupValues[1] }.toList()
        if (tags.size != 1) return LeagueAnnouncementParseResult.Blocked("expected exactly one supported announcement hashtag")
        val league = when (tags.single()) {
            "лп" -> "ЛП"
            "зл" -> "ЗЛ"
            else -> return LeagueAnnouncementParseResult.Blocked("unsupported league")
        }
        val policy = properties.policy(league)
            ?: return LeagueAnnouncementParseResult.Blocked("league policy is disabled")
        if (policy.tournamentId <= 0 || policy.namePrefix.isBlank() || !policy.nameTemplate.contains("%d")) {
            return LeagueAnnouncementParseResult.Blocked("league policy is incomplete")
        }
        if (policy.timezone != MOSCOW_ZONE.id) {
            return LeagueAnnouncementParseResult.Blocked("only Europe/Moscow policy is supported")
        }
        if (policy.expectedGameCount !in 1..32) {
            return LeagueAnnouncementParseResult.Blocked("expected game count must be between 1 and 32")
        }

        val seriesNumbers = SERIES_NUMBER.findAll(normalized).mapNotNull { it.groupValues[1].toLongOrNull() }.distinct().toList()
        if (seriesNumbers.size != 1) return LeagueAnnouncementParseResult.Blocked("series number is missing or ambiguous")
        val dates = parseDates(normalized, postedAt.atZone(MOSCOW_ZONE).year)
        if (dates.size != 1) return LeagueAnnouncementParseResult.Blocked("date is missing, invalid or ambiguous")
        val times = TIME.findAll(normalized).mapNotNull {
            runCatching { LocalTime.of(it.groupValues[1].toInt(), it.groupValues[2].toInt()) }.getOrNull()
        }.distinct().toList()
        if (times.size != 1) return LeagueAnnouncementParseResult.Blocked("time is missing, invalid or ambiguous")

        val startsAt = LocalDateTime.of(dates.single(), times.single()).atZone(MOSCOW_ZONE).toInstant()
        val name = runCatching { policy.nameTemplate.format(seriesNumbers.single()) }.getOrNull()
            ?.trim()?.takeIf { it.isNotEmpty() }
            ?: return LeagueAnnouncementParseResult.Blocked("invalid series name template")
        val draft = LeagueAnnouncementDraft(
            league = league,
            seriesNumber = seriesNumbers.single(),
            tournamentId = policy.tournamentId,
            name = name,
            namePrefix = policy.namePrefix.trim(),
            gameStartedOn = dates.single(),
            startsAt = startsAt,
            teamDeadline = startsAt.plusSeconds(policy.teamDeadlineOffsetMinutes * 60),
            expectedGameCount = policy.expectedGameCount,
            sourceUrl = sourceUrl,
        )
        val checksumMaterial = listOf(
            draft.league,
            draft.seriesNumber,
            draft.tournamentId,
            draft.name,
            draft.namePrefix,
            draft.gameStartedOn,
            draft.startsAt,
            draft.teamDeadline,
            draft.expectedGameCount,
            draft.sourceUrl,
        ).joinToString("|")
        return LeagueAnnouncementParseResult.Ready(draft, sha256(checksumMaterial))
    }

    private fun parseDates(text: String, sourceYear: Int): List<LocalDate> {
        val values = mutableListOf<LocalDate>()
        NUMERIC_DATE.findAll(text).forEach { match ->
            val day = match.groupValues[1].toInt()
            val month = match.groupValues[2].toInt()
            val yearText = match.groupValues[3]
            val year = when {
                yearText.isBlank() -> sourceYear
                yearText.length == 2 -> 2000 + yearText.toInt()
                else -> yearText.toInt()
            }
            runCatching { LocalDate.of(year, month, day) }.getOrNull()?.let(values::add)
        }
        RUSSIAN_DATE.findAll(text).forEach { match ->
            val day = match.groupValues[1].toInt()
            val month = MONTHS[match.groupValues[2]] ?: return@forEach
            val year = match.groupValues[3].takeIf { it.isNotBlank() }?.toInt() ?: sourceYear
            runCatching { LocalDate.of(year, month, day) }.getOrNull()?.let(values::add)
        }
        return values.distinct()
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    companion object {
        private val MOSCOW_ZONE = ZoneId.of("Europe/Moscow")
        private val TAG = Regex("(?<![\\p{L}\\p{N}_])#анонс_(лп|зл)(?![\\p{L}\\p{N}_])")
        private val SERIES_NUMBER = Regex("(?<![\\p{L}\\p{N}_])серия\\s*(?:№\\s*)?(\\d+)(?!\\d)")
        private val NUMERIC_DATE = Regex("(?<!\\d)([0-3]?\\d)[./-]([01]?\\d)(?:[./-](\\d{2}|\\d{4}))?(?!\\d)")
        private val RUSSIAN_DATE = Regex("(?<!\\d)([1-9]|[12]\\d|3[01])\\s+(января|февраля|марта|апреля|мая|июня|июля|августа|сентября|октября|ноября|декабря)(?:\\s+(20\\d{2}))?(?!\\d)")
        private val TIME = Regex("(?<!\\d)([01]?\\d|2[0-3])[:.]([0-5]\\d)(?!\\d)")
        private val MONTHS = mapOf(
            "января" to 1, "февраля" to 2, "марта" to 3, "апреля" to 4,
            "мая" to 5, "июня" to 6, "июля" to 7, "августа" to 8,
            "сентября" to 9, "октября" to 10, "ноября" to 11, "декабря" to 12,
        )
    }
}
