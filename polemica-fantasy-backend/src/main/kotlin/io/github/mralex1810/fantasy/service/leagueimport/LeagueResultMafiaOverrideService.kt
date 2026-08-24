package io.github.mralex1810.fantasy.service.leagueimport

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.mafia.vyasma.polemica.library.model.game.PolemicaGame
import com.github.mafia.vyasma.polemica.library.model.game.Role
import io.github.mralex1810.fantasy.dto.admin.response.ResultMafiaOverrideDto
import io.github.mralex1810.fantasy.entity.SeriesStatus
import io.github.mralex1810.fantasy.repository.LeagueImportRepository
import io.github.mralex1810.fantasy.repository.SeriesGameRepository
import io.github.mralex1810.fantasy.repository.SeriesRepository
import io.github.mralex1810.fantasy.service.SeriesResultIdentityMatcher
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

/** Audited correction layered over immutable Telegram RESULT evidence. */
@Service
class LeagueResultMafiaOverrideService(
    private val seriesRepository: SeriesRepository,
    private val seriesGameRepository: SeriesGameRepository,
    private val leagueImportRepository: LeagueImportRepository,
    private val objectMapper: ObjectMapper,
) {
    @Transactional
    fun create(
        seriesId: Long,
        gameNumber: Int,
        correctedMafiaLine: String,
        reason: String,
        adminActor: String,
    ): ResultMafiaOverrideDto {
        val series = seriesRepository.findByIdForUpdate(seriesId) ?: notFound("Series $seriesId not found")
        if (series.finalized || series.status == SeriesStatus.FINISHED) conflict("series is already finalized")

        val resultItems = leagueImportRepository.findCurrentResultItemsForSeries(seriesId, lock = true)
        if (resultItems.size != 1) conflict("exactly one current RESULT source is required")
        val resultItem = resultItems.single()
        val draft = resultItem.draftJson?.let {
            runCatching { objectMapper.treeToValue(it, LeagueResultDraft::class.java) }.getOrNull()
        } ?: conflict("RESULT source draft is invalid")
        val announcedGame = draft.games.singleOrNull { it.number == gameNumber }
            ?: conflict("RESULT does not contain game $gameNumber")

        val games = seriesGameRepository.findAllBySeries_Id(seriesId).sortedWith(
            compareBy({ it.playedAt ?: Instant.MAX }, { it.polemicaGameId }, { it.id ?: Long.MAX_VALUE }),
        )
        val gameRow = games.getOrNull(gameNumber - 1) ?: conflict("synced game $gameNumber does not exist")
        val game = gameRow.gameDataCache?.let {
            runCatching { objectMapper.treeToValue(it, PolemicaGame::class.java) }.getOrNull()
        } ?: conflict("game $gameNumber cache is missing or invalid")
        val actualMafia = game.players.orEmpty()
            .filter { it.role == Role.DON || it.role == Role.MAFIA }
            .mapNotNull { it.player?.username?.takeIf(String::isNotBlank) ?: it.username.takeIf(String::isNotBlank) }
        if (actualMafia.size != 3 || !SeriesResultIdentityMatcher.matchesMafiaLine(correctedMafiaLine, actualMafia)) {
            conflict("corrected mafia line does not match current Polemica roles for game $gameNumber")
        }
        if (SeriesResultIdentityMatcher.matchesMafiaLine(announcedGame.mafiaLine, actualMafia)) {
            conflict("original RESULT mafia line already matches game $gameNumber")
        }

        val normalizedReason = reason.trim()
        val normalizedActor = adminActor.trim().ifBlank { "unknown-admin" }.take(128)
        val override = try {
            leagueImportRepository.insertResultMafiaOverride(
                seriesId = seriesId,
                importItemId = resultItem.id,
                gameNumber = gameNumber,
                originalMafiaLine = announcedGame.mafiaLine,
                correctedMafiaLine = correctedMafiaLine.trim(),
                reason = normalizedReason,
                adminActor = normalizedActor,
            )
        } catch (_: DataIntegrityViolationException) {
            conflict("mafia override already exists for game $gameNumber")
        }
        leagueImportRepository.audit(
            resultItem.id,
            actionId = null,
            actorId = null,
            eventType = "RESULT_MAFIA_OVERRIDDEN",
            outcome = "COMMITTED",
            details = mapOf(
                "seriesId" to seriesId,
                "gameNumber" to gameNumber,
                "originalMafiaLine" to override.originalMafiaLine,
                "correctedMafiaLine" to override.correctedMafiaLine,
                "reason" to override.reason,
                "adminActor" to override.adminActor,
            ),
        )
        return override.toDto()
    }

    private fun io.github.mralex1810.fantasy.repository.LeagueImportResultMafiaOverrideRow.toDto() =
        ResultMafiaOverrideDto(
            id = id,
            seriesId = seriesId,
            importItemId = importItemId,
            gameNumber = gameNumber,
            originalMafiaLine = originalMafiaLine,
            correctedMafiaLine = correctedMafiaLine,
            reason = reason,
            adminActor = adminActor,
            createdAt = createdAt,
        )

    private fun notFound(message: String): Nothing = throw ResponseStatusException(HttpStatus.NOT_FOUND, message)
    private fun conflict(message: String): Nothing = throw ResponseStatusException(HttpStatus.CONFLICT, message)
}
