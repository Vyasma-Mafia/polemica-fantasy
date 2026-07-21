package io.github.mralex1810.fantasy.service

import com.fasterxml.jackson.databind.JsonNode
import io.github.mralex1810.fantasy.dto.admin.response.SeriesResultsCellDto
import io.github.mralex1810.fantasy.dto.admin.response.SeriesResultsGameDto
import io.github.mralex1810.fantasy.dto.admin.response.SeriesResultsPlayerDto
import io.github.mralex1810.fantasy.dto.admin.response.SeriesResultsPointsStatus
import io.github.mralex1810.fantasy.dto.admin.response.SeriesResultsResponseDto
import io.github.mralex1810.fantasy.entity.SeriesGame
import io.github.mralex1810.fantasy.entity.TournamentKind
import io.github.mralex1810.fantasy.polemica.PolemicaPublicPointsLoader
import io.github.mralex1810.fantasy.repository.SeriesGameRepository
import io.github.mralex1810.fantasy.repository.SeriesRepository
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.Locale

@Service
class SeriesResultsService(
    private val seriesRepository: SeriesRepository,
    private val seriesGameRepository: SeriesGameRepository,
    private val publicPointsLoader: PolemicaPublicPointsLoader,
) {
    fun getResults(seriesId: Long): SeriesResultsResponseDto {
        val series = seriesRepository.findByIdWithTournament(seriesId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Series $seriesId not found")
        val tournamentKind = series.tournament?.kind
            ?: throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Series $seriesId has no tournament")
        val rows = seriesGameRepository.findAllBySeries_Id(seriesId).sortedWith(gameComparator(tournamentKind))
        val warnings = mutableListOf<String>()
        val processed = rows.map { processGame(it, warnings) }
        val players = linkedMapOf<String, PlayerAccumulator>()

        processed.forEach { game ->
            game.participants.forEach { participant ->
                val accumulator = players.getOrPut(participant.playerKey) {
                    PlayerAccumulator(participant.playerKey, participant.polemicaUserId, participant.nickname)
                }
                // Iteration follows the displayed game order, so the latest cached nickname wins.
                accumulator.nickname = participant.nickname
                accumulator.participationByGameId[game.row.id!!] = participant.position
            }
        }

        val globallyIncomplete = processed.any {
            it.status == SeriesResultsPointsStatus.CACHE_MISSING ||
                it.status == SeriesResultsPointsStatus.CACHE_INVALID
        }
        val playerDtos = players.values.map { player ->
            var total = 0.0
            var complete = !globallyIncomplete
            val cells = processed.map { game ->
                val gameId = game.row.id!!
                val position = player.participationByGameId[gameId]
                val points = position?.let { game.pointsByPosition[it] }
                if (position != null && points == null) complete = false
                if (points != null) total += points
                SeriesResultsCellDto(
                    seriesGameId = gameId,
                    participated = position != null,
                    points = points,
                )
            }
            SeriesResultsPlayerDto(
                playerKey = player.playerKey,
                polemicaUserId = player.polemicaUserId,
                nickname = player.nickname,
                cells = cells,
                totalPoints = total,
                gamesPlayed = player.participationByGameId.size,
                complete = complete,
            )
        }.sortedWith(
            compareByDescending<SeriesResultsPlayerDto> { it.totalPoints }
                .thenBy { it.nickname.lowercase(Locale.ROOT) }
                .thenBy { it.polemicaUserId ?: Long.MAX_VALUE }
                .thenBy { it.playerKey },
        )

        return SeriesResultsResponseDto(
            seriesId = seriesId,
            tournamentKind = tournamentKind,
            games = processed.mapIndexed { index, game ->
                SeriesResultsGameDto(
                    seriesGameId = game.row.id!!,
                    polemicaGameId = game.row.polemicaGameId,
                    columnLabel = if (tournamentKind == TournamentKind.STANDALONE) {
                        (index + 1).toString()
                    } else {
                        game.num?.toString() ?: "#${game.row.polemicaGameId}"
                    },
                    gameNum = game.num,
                    table = game.table,
                    phase = game.phase,
                    playedAt = game.row.playedAt,
                    finished = game.finished,
                    pointsStatus = game.status,
                )
            },
            players = playerDtos,
            warnings = warnings,
        )
    }

    private fun processGame(row: SeriesGame, warnings: MutableList<String>): ProcessedGame {
        val node = row.gameDataCache
        if (node == null) {
            warnings += "Game ${row.polemicaGameId} (seriesGameId=${row.id}) has no cached game data"
            return ProcessedGame(row, null, null, null, false, SeriesResultsPointsStatus.CACHE_MISSING)
        }
        val num = node.optionalInt("num")
        val table = node.optionalInt("table")
        val phase = node.optionalInt("phase")
        val finished = node.get("result")?.let { !it.isNull } ?: false
        val parsedParticipants = try {
            parseParticipants(row, node, warnings)
        } catch (e: IllegalArgumentException) {
            warnings += "Game ${row.polemicaGameId} (seriesGameId=${row.id}) has invalid cached data: ${e.message}"
            return ProcessedGame(row, num, table, phase, finished, SeriesResultsPointsStatus.CACHE_INVALID)
        }
        val duplicatePlayerKeys = parsedParticipants.groupBy { it.playerKey }.filterValues { it.size > 1 }.keys
        if (duplicatePlayerKeys.isNotEmpty()) {
            warnings += "Game ${row.polemicaGameId} has duplicate cached Polemica player ids: " +
                duplicatePlayerKeys.sorted().joinToString()
        }
        val participants = parsedParticipants.map { participant ->
            if (participant.playerKey in duplicatePlayerKeys) {
                participant.copy(
                    playerKey = "${participant.playerKey}:seriesGame:${row.id}:position:${participant.position}",
                )
            } else {
                participant
            }
        }
        if (!finished) {
            return ProcessedGame(row, num, table, phase, false, SeriesResultsPointsStatus.UNFINISHED, participants)
        }

        val loaded = try {
            publicPointsLoader.load(row.polemicaGameId)
        } catch (e: Exception) {
            logger.warn(
                "Failed to load public points for Polemica game {} (seriesGameId={})",
                row.polemicaGameId,
                row.id,
                e,
            )
            warnings += "Failed to load public points for game ${row.polemicaGameId} (seriesGameId=${row.id})"
            return ProcessedGame(row, num, table, phase, true, SeriesResultsPointsStatus.LOAD_FAILED, participants)
        }
        warnings += loaded.warnings
        val duplicateCached = participants.groupBy { it.position }.filterValues { it.size > 1 }.keys
        val duplicatePublic = loaded.rows.groupBy { it.tablePosition }.filterValues { it.size > 1 }.keys
        if (duplicateCached.isNotEmpty()) {
            warnings += "Game ${row.polemicaGameId} has duplicate cached table positions: ${duplicateCached.sorted().joinToString()}"
        }
        if (duplicatePublic.isNotEmpty()) {
            warnings += "Game ${row.polemicaGameId} has duplicate public points positions: ${duplicatePublic.sorted().joinToString()}"
        }
        val cachedPositions = participants.map { it.position }.toSet()
        val publicPositions = loaded.rows.map { it.tablePosition }.toSet()
        val mismatch = cachedPositions != publicPositions
        if (mismatch) {
            warnings += "Game ${row.polemicaGameId} cached/public position sets differ: cached=${cachedPositions.sorted()}, public=${publicPositions.sorted()}"
        }
        val points = loaded.rows
            .filterNot { it.tablePosition in duplicatePublic || it.tablePosition in duplicateCached }
            .associate { it.tablePosition to it.points }
        val status = when {
            loaded.rows.isEmpty() && loaded.warnings.isEmpty() -> SeriesResultsPointsStatus.EMPTY
            loaded.warnings.isNotEmpty() || duplicatePlayerKeys.isNotEmpty() || duplicateCached.isNotEmpty() ||
                duplicatePublic.isNotEmpty() || mismatch ->
                SeriesResultsPointsStatus.PARTIAL
            else -> SeriesResultsPointsStatus.AVAILABLE
        }
        return ProcessedGame(row, num, table, phase, true, status, participants, points)
    }

    private fun parseParticipants(row: SeriesGame, root: JsonNode, warnings: MutableList<String>): List<CachedParticipant> {
        val players = root.get("players")
        require(players != null && players.isArray) { "players must be an array" }
        return players.mapIndexed { index, player ->
            val positionNode = player.get("position")
            val position = positionNode
                ?.takeIf { it.isIntegralNumber }
                ?.longValue()
                ?.takeIf { it in 1L..10L }
                ?.toInt()
                ?: throw IllegalArgumentException("players[$index].position must be an integer from 1 to 10")
            val identity = player.get("player")
            val polemicaUserId = when {
                identity == null || identity.isNull -> null
                identity.isIntegralNumber -> identity.longValue()
                identity.isObject && identity.get("id")?.isIntegralNumber == true -> identity.get("id").longValue()
                else -> throw IllegalArgumentException("players[$index].player has no valid id")
            }
            val nickname = identity?.takeIf { it.isObject }?.get("username")
                ?.takeIf { it.isTextual }?.asText()?.trim().orEmpty()
                .ifEmpty { player.get("username")?.takeIf { it.isTextual }?.asText()?.trim().orEmpty() }
                .ifEmpty { polemicaUserId?.let { "#$it" } ?: "Anonymous ${position}" }
            val key = polemicaUserId?.let { "polemica:$it" } ?: "anonymous:${row.id}:$position"
            if (polemicaUserId == null) {
                warnings += "Game ${row.polemicaGameId} position $position has no Polemica user id; using deterministic key $key"
            }
            CachedParticipant(key, polemicaUserId, nickname, position)
        }
    }

    private fun gameComparator(kind: TournamentKind): Comparator<SeriesGame> = when (kind) {
        TournamentKind.STANDALONE -> compareBy<SeriesGame>(
            { it.playedAt ?: Instant.MAX },
            { it.polemicaGameId },
            { it.id ?: Long.MAX_VALUE },
        )
        TournamentKind.POLEMICA_COMPETITION -> compareBy<SeriesGame>(
            { it.gameDataCache.optionalInt("num") ?: Int.MAX_VALUE },
            { it.gameDataCache.optionalInt("phase") ?: Int.MAX_VALUE },
            { it.gameDataCache.optionalInt("table") ?: Int.MAX_VALUE },
            { it.playedAt ?: Instant.MAX },
            { it.polemicaGameId },
            { it.id ?: Long.MAX_VALUE },
        )
    }

    private fun JsonNode?.optionalInt(field: String): Int? {
        val value = this?.get(field) ?: return null
        return if (value.isIntegralNumber && value.canConvertToInt()) value.intValue() else null
    }

    private data class CachedParticipant(
        val playerKey: String,
        val polemicaUserId: Long?,
        val nickname: String,
        val position: Int,
    )

    private data class ProcessedGame(
        val row: SeriesGame,
        val num: Int?,
        val table: Int?,
        val phase: Int?,
        val finished: Boolean,
        val status: SeriesResultsPointsStatus,
        val participants: List<CachedParticipant> = emptyList(),
        val pointsByPosition: Map<Int, Double> = emptyMap(),
    )

    private data class PlayerAccumulator(
        val playerKey: String,
        val polemicaUserId: Long?,
        var nickname: String,
        val participationByGameId: MutableMap<Long, Int> = linkedMapOf(),
    )

    private companion object {
        val logger = LoggerFactory.getLogger(SeriesResultsService::class.java)
    }
}
