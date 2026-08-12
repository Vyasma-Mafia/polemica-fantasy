package io.github.mralex1810.fantasy.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.mafia.vyasma.polemica.library.model.game.PolemicaGame
import com.github.mafia.vyasma.polemica.library.model.game.PolemicaGameResult
import com.github.mafia.vyasma.polemica.library.model.game.Role
import io.github.mralex1810.fantasy.entity.SeriesStatus
import io.github.mralex1810.fantasy.entity.TournamentKind
import io.github.mralex1810.fantasy.config.TelegramLeagueImportProperties
import io.github.mralex1810.fantasy.repository.LeagueImportRepository
import io.github.mralex1810.fantasy.repository.SeriesGameRepository
import io.github.mralex1810.fantasy.repository.SeriesRepository
import io.github.mralex1810.fantasy.service.leagueimport.AnnouncedGameWinner
import io.github.mralex1810.fantasy.service.leagueimport.LeagueResultDraft
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.text.Normalizer

enum class SeriesCompletionStatus { READY, NOT_READY }

data class SeriesCompletion(
    val status: SeriesCompletionStatus,
    val checksum: String? = null,
    val reason: String? = null,
) {
    val ready: Boolean get() = status == SeriesCompletionStatus.READY
}

/** One conservative readiness gate shared by admin and Telegram finalization. */
@Service
class SeriesCompletionService(
    private val seriesRepository: SeriesRepository,
    private val seriesGameRepository: SeriesGameRepository,
    private val leagueImportRepository: LeagueImportRepository,
    private val objectMapper: ObjectMapper,
    private val properties: TelegramLeagueImportProperties,
    private val scoringContextFingerprintService: SeriesScoringContextFingerprintService,
    private val economyFingerprintService: SeriesEconomyFingerprintService,
    private val selectorFingerprintService: SeriesGameSelectorFingerprintService,
) {
    fun evaluate(seriesId: Long): SeriesCompletion = evaluate(seriesId, lockEvidence = false)

    /** Caller must already hold the series row lock; this fences concurrent RESULT edits. */
    fun evaluateForFinalization(seriesId: Long): SeriesCompletion = evaluate(seriesId, lockEvidence = true)

    private fun evaluate(seriesId: Long, lockEvidence: Boolean): SeriesCompletion {
        val series = seriesRepository.findByIdWithTournament(seriesId)
            ?: return notReady("series does not exist")
        if (series.finalized || series.status == SeriesStatus.FINISHED) return notReady("series is already finalized")
        if (series.status != SeriesStatus.SCORING) return notReady("series status must be SCORING")
        if (series.tournament?.kind != TournamentKind.STANDALONE) return notReady("only STANDALONE series can use result finalization")
        val expected = series.expectedGameCount ?: return notReady("expected game count is not configured")
        val selectorChecksum = selectorFingerprintService.fingerprint(seriesId)
        if (series.lastSyncedSelectorChecksum != selectorChecksum || series.lastScoredSelectorChecksum != selectorChecksum) {
            return notReady("game selector changed since successful sync and scoring")
        }
        val economy = economyFingerprintService.snapshot(seriesId)
        if (!economy.ready) return notReady(economy.reason ?: "series economy inputs are not ready")

        val resultItems = leagueImportRepository.findCurrentResultItemsForSeries(seriesId, lock = lockEvidence)
        if (resultItems.size != 1) return notReady("exactly one current RESULT source is required")
        if (resultItems.single().policyGeneration != properties.policyGeneration || properties.policyGeneration == "disabled") {
            return notReady("RESULT policy generation is stale")
        }
        val resultDraft = resultItems.single().draftJson?.let {
            runCatching { objectMapper.treeToValue(it, LeagueResultDraft::class.java) }.getOrNull()
        } ?: return notReady("RESULT source draft is invalid")
        if (resultDraft.tournamentId != series.tournament?.id || resultDraft.seriesNumber != series.publicNumber) {
            return notReady("RESULT source does not match series identity")
        }
        if (resultDraft.expectedGameCount != expected || resultDraft.games.size != expected) {
            return notReady("RESULT game count differs from series expectation")
        }

        val games = seriesGameRepository.findAllBySeries_Id(seriesId).sortedWith(
            compareBy({ it.playedAt ?: java.time.Instant.MAX }, { it.polemicaGameId }, { it.id ?: Long.MAX_VALUE }),
        )
        if (games.size != expected) return notReady("expected $expected synced games, found ${games.size}")
        val scoringContextChecksum = scoringContextFingerprintService.fingerprint(seriesId)
        val evaluated = games.mapIndexed { index, row ->
            val cache = row.gameDataCache ?: return notReady("game ${row.polemicaGameId} cache is missing")
            val game = runCatching { objectMapper.treeToValue(cache, PolemicaGame::class.java) }.getOrNull()
                ?: return notReady("game ${row.polemicaGameId} cache is invalid")
            val number = index + 1
            val result = game.result ?: return notReady("game $number is unfinished")
            if (!row.scored || row.pointsStatus != "COMPLETE" || row.scoringInputChecksum?.matches(SHA256) != true ||
                row.scoringContextChecksum != scoringContextChecksum || row.scoredAt == null) {
                return notReady("game $number scoring/public points are incomplete or stale")
            }
            EvaluatedGame(
                number, row.id!!, row.polemicaGameId, result, sha256(cache.toString()), row.scored,
                row.pointsStatus, row.scoringInputChecksum!!, row.scoringContextChecksum!!, game,
            )
        }

        val announced = resultDraft.games.associateBy { it.number }
        for (game in evaluated) {
            val announcedGame = announced[game.number] ?: return notReady("RESULT is missing game ${game.number}")
            val expectedWinner = announcedGame.winner
            val actualWinner = when (game.result) {
                PolemicaGameResult.RED_WIN -> AnnouncedGameWinner.CIVILIANS
                PolemicaGameResult.BLACK_WIN -> AnnouncedGameWinner.MAFIA
            }
            if (expectedWinner != actualWinner) return notReady("winner mismatch for game ${game.number}")
            val blackNames = game.parsedGame.players.orEmpty()
                .filter { it.role == Role.DON || it.role == Role.MAFIA }
                .mapNotNull { it.player?.username?.takeIf(String::isNotBlank) ?: it.username.takeIf(String::isNotBlank) }
            if (blackNames.size != 3 || !matchesMafiaLine(announcedGame.mafiaLine, blackNames)) {
                return notReady("mafia set mismatch or unresolved alias for game ${game.number}")
            }
            val sheriffNames = game.parsedGame.players.orEmpty()
                .filter { it.role == Role.SHERIFF }
                .mapNotNull { it.player?.username?.takeIf(String::isNotBlank) ?: it.username.takeIf(String::isNotBlank) }
            if (sheriffNames.size != 1 || normalizeIdentity(sheriffNames.single()) != normalizeIdentity(announcedGame.sheriff)) {
                return notReady("sheriff mismatch or unresolved alias for game ${game.number}")
            }
        }

        val fingerprint = sha256(
            buildList {
                add(seriesId.toString())
                add(series.status.name)
                add(series.finalized.toString())
                add(expected.toString())
                add(series.tournament!!.id!!.toString())
                add(series.publicNumber.toString())
                add(series.namePrefix.orEmpty())
                add(series.gameStartedOn?.toString().orEmpty())
                add(series.startsAt.toString())
                add(series.teamDeadline.toString())
                add(scoringContextChecksum)
                add(selectorChecksum)
                add(economy.checksum!!)
                add(properties.policyGeneration)
                add(resultItems.single().version.toString())
                add(resultItems.single().currentRevision.toString())
                add(resultItems.single().draftChecksum.orEmpty())
                evaluated.forEach {
                    add("${it.number}:${it.rowId}:${it.polemicaGameId}:${it.cacheChecksum}:${it.result.value}:" +
                        "${it.scored}:${it.pointsStatus}:${it.scoringChecksum}:${it.scoringContextChecksum}")
                }
            }.joinToString("|"),
        )
        return SeriesCompletion(SeriesCompletionStatus.READY, checksum = fingerprint)
    }

    fun requireReady(seriesId: Long, expectedChecksum: String? = null): SeriesCompletion {
        val completion = evaluate(seriesId)
        check(completion.ready) { "Series $seriesId is not ready to finalize: ${completion.reason}" }
        if (expectedChecksum != null) check(expectedChecksum == completion.checksum) { "Series readiness changed before finalization" }
        return completion
    }

    private fun notReady(reason: String) = SeriesCompletion(SeriesCompletionStatus.NOT_READY, reason = reason)

    private fun matchesMafiaLine(line: String, actualNames: List<String>): Boolean {
        val expected = normalizeIdentity(line)
        return permutations(actualNames).any { normalizeIdentity(it.joinToString(", ")) == expected }
    }

    private fun permutations(values: List<String>): Sequence<List<String>> = sequence {
        if (values.size <= 1) {
            yield(values)
        } else {
            values.indices.forEach { index ->
                val head = values[index]
                permutations(values.filterIndexed { i, _ -> i != index }).forEach { yield(listOf(head) + it) }
            }
        }
    }

    private fun normalizeIdentity(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .lowercase()
        .replace('ё', 'е')
        // Polemica usernames can mix the visually identical Latin C and Cyrillic С.
        // Canonicalize only this observed confusable; punctuation/emoji are removed below.
        .replace('с', 'c')
        .filter { it.isLetterOrDigit() }
    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    private data class EvaluatedGame(
        val number: Int,
        val rowId: Long,
        val polemicaGameId: Long,
        val result: PolemicaGameResult,
        val cacheChecksum: String,
        val scored: Boolean,
        val pointsStatus: String,
        val scoringChecksum: String,
        val scoringContextChecksum: String,
        val parsedGame: PolemicaGame,
    )

    private companion object {
        val SHA256 = Regex("[0-9a-f]{64}")
    }
}
