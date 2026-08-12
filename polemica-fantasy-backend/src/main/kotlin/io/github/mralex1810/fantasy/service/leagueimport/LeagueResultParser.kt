package io.github.mralex1810.fantasy.service.leagueimport

import io.github.mralex1810.fantasy.config.TelegramLeagueImportProperties
import org.springframework.stereotype.Component
import java.security.MessageDigest

enum class AnnouncedGameWinner { CIVILIANS, MAFIA }

data class LeagueResultGame(
    val number: Int,
    val winner: AnnouncedGameWinner,
    /** Kept unsplit because Telegram nicknames themselves can contain commas (for example `Houston, TX`). */
    val mafiaLine: String,
    val sheriff: String,
)

data class LeagueResultDraft(
    val league: String,
    val seriesNumber: Long,
    val tournamentId: Long,
    val expectedGameCount: Int,
    val games: List<LeagueResultGame>,
    val sourceUrl: String,
)

sealed interface LeagueResultParseResult {
    data class Ready(val draft: LeagueResultDraft, val checksum: String) : LeagueResultParseResult
    data class Blocked(val reason: String) : LeagueResultParseResult
}

/** Strict, deterministic parser for the text result format published in the league channel. */
@Component
class LeagueResultParser(
    private val properties: TelegramLeagueImportProperties,
) {
    fun parse(text: String, sourceUrl: String): LeagueResultParseResult {
        val normalized = text.lowercase().replace('ё', 'е')
        val tags = TAG.findAll(normalized).map { it.groupValues[1] }.toList()
        if (tags.size != 1) return LeagueResultParseResult.Blocked("expected exactly one supported result hashtag")
        val league = if (tags.single() == "лп") "ЛП" else "ЗЛ"
        val policy = properties.policy(league) ?: return LeagueResultParseResult.Blocked("league policy is disabled")
        if (policy.tournamentId <= 0 || policy.expectedGameCount !in 1..32) {
            return LeagueResultParseResult.Blocked("league result policy is incomplete")
        }

        val title = if (league == "ЛП") LP_TITLE else ZL_TITLE
        val titleMatches = title.findAll(normalized).mapNotNull { it.groupValues[1].toLongOrNull() }.distinct().toList()
        if (titleMatches.size != 1) return LeagueResultParseResult.Blocked("result series number is missing or ambiguous")

        val blockMatches = GAME_HEADER.findAll(normalized).toList()
        if (blockMatches.isEmpty()) return LeagueResultParseResult.Blocked("result contains no game blocks")
        val games = mutableListOf<LeagueResultGame>()
        for ((index, match) in blockMatches.withIndex()) {
            val number = match.groupValues[1].toIntOrNull()
                ?: return LeagueResultParseResult.Blocked("invalid game number")
            val end = blockMatches.getOrNull(index + 1)?.range?.first ?: normalized.length
            val block = normalized.substring(match.range.first, end)
            val winners = WINNER.findAll(block).mapNotNull { winner(it.groupValues[1]) }.distinct().toList()
            if (winners.size != 1) return LeagueResultParseResult.Blocked("game $number winner is missing or ambiguous")
            val mafiaLines = MAFIA_LINE.findAll(block).map { it.groupValues[1].trim() }.filter { it.isNotBlank() }.toList()
            val sheriffs = SHERIFF_LINE.findAll(block).map { it.groupValues[1].trim() }.filter { it.isNotBlank() }.toList()
            if (mafiaLines.size != 1) return LeagueResultParseResult.Blocked("game $number mafia line is missing or ambiguous")
            if (sheriffs.size != 1) return LeagueResultParseResult.Blocked("game $number sheriff is missing or ambiguous")
            games += LeagueResultGame(number, winners.single(), mafiaLines.single(), sheriffs.single())
        }
        if (games.map { it.number } != (1..policy.expectedGameCount).toList()) {
            return LeagueResultParseResult.Blocked("expected contiguous games 1..${policy.expectedGameCount}")
        }

        val draft = LeagueResultDraft(
            league = league,
            seriesNumber = titleMatches.single(),
            tournamentId = policy.tournamentId,
            expectedGameCount = policy.expectedGameCount,
            games = games,
            sourceUrl = sourceUrl,
        )
        val material = buildList {
            add(draft.league)
            add(draft.seriesNumber)
            add(draft.tournamentId)
            add(draft.expectedGameCount)
            draft.games.forEach { add("${it.number}:${it.winner}:${it.mafiaLine}:${it.sheriff}") }
            add(draft.sourceUrl)
        }.joinToString("|")
        return LeagueResultParseResult.Ready(draft, sha256(material))
    }

    private fun winner(value: String): AnnouncedGameWinner? = when (value) {
        "мирные", "мирных", "красные", "красных" -> AnnouncedGameWinner.CIVILIANS
        "мафия", "мафии", "черные", "черных" -> AnnouncedGameWinner.MAFIA
        else -> null
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    private companion object {
        val TAG = Regex("(?<![\\p{L}\\p{N}_])#результаты_(лп|зл)(?![\\p{L}\\p{N}_])")
        val LP_TITLE = Regex("лига\\s+претендентов\\s*:\\s*серия\\s*(?:№\\s*)?(\\d+)\\s*[.]?\\s*результаты")
        val ZL_TITLE = Regex("закрытая\\s+лига\\s*:\\s*серия\\s*(?:№\\s*)?(\\d+)\\s*[.]?\\s*результаты")
        val GAME_HEADER = Regex("(?m)^\\s*(?:[-–—*]\\s*)?игра\\s*(?:№\\s*)?(\\d+)(?!\\d)")
        val WINNER = Regex("(?<![\\p{L}\\p{N}_])(?:победа|победили|победитель)\\s*:?\\s*(мирные|мирных|красные|красных|мафия|мафии|черные|черных)(?![\\p{L}\\p{N}_])")
        val MAFIA_LINE = Regex("(?m)^\\s*мафия\\s*:\\s*(.+?)\\s*$")
        val SHERIFF_LINE = Regex("(?m)^\\s*шериф\\s*:\\s*(.+?)\\s*$")
    }
}
