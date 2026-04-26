package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.dto.admin.request.AssignSeriesPlayersRequest
import io.github.mralex1810.fantasy.dto.admin.request.CreateSeriesRequest
import io.github.mralex1810.fantasy.dto.admin.request.UpdateSeriesRequest
import io.github.mralex1810.fantasy.dto.admin.response.BatchStartSeriesResponse
import io.github.mralex1810.fantasy.dto.admin.response.SeriesDto
import io.github.mralex1810.fantasy.dto.admin.response.SkippedSeriesEntry
import io.github.mralex1810.fantasy.dto.admin.response.StartedSeriesEntry
import io.github.mralex1810.fantasy.entity.DeadlineReminder
import io.github.mralex1810.fantasy.entity.LeagueType
import io.github.mralex1810.fantasy.entity.Series
import io.github.mralex1810.fantasy.entity.SeriesLeague
import io.github.mralex1810.fantasy.entity.SeriesPlayer
import io.github.mralex1810.fantasy.entity.SeriesStatus
import io.github.mralex1810.fantasy.entity.TournamentKind
import io.github.mralex1810.fantasy.event.SeriesBatchStartedEvent
import io.github.mralex1810.fantasy.event.SeriesRosterReplacementNotificationEvent
import io.github.mralex1810.fantasy.event.SeriesRosterReplacementRecipient
import io.github.mralex1810.fantasy.event.StartedSeriesInfo
import io.github.mralex1810.fantasy.event.buildSeriesRosterReplacementTelegramMessage
import io.github.mralex1810.fantasy.polemica.GameSyncService
import io.github.mralex1810.fantasy.repository.DeadlineReminderRepository
import io.github.mralex1810.fantasy.repository.LeagueRepository
import io.github.mralex1810.fantasy.repository.SeriesLeagueRepository
import io.github.mralex1810.fantasy.repository.SeriesGameRepository
import io.github.mralex1810.fantasy.repository.SeriesPlayerRepository
import io.github.mralex1810.fantasy.repository.SeriesRepository
import io.github.mralex1810.fantasy.repository.TournamentPlayerRepository
import io.github.mralex1810.fantasy.repository.TournamentRepository
import io.github.mralex1810.fantasy.scoring.ScoringService
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
class SeriesService(
    private val tournamentRepository: TournamentRepository,
    private val seriesRepository: SeriesRepository,
    private val seriesGameRepository: SeriesGameRepository,
    private val tournamentPlayerRepository: TournamentPlayerRepository,
    private val seriesPlayerRepository: SeriesPlayerRepository,
    private val deadlineReminderRepository: DeadlineReminderRepository,
    private val leagueRepository: LeagueRepository,
    private val seriesLeagueRepository: SeriesLeagueRepository,
    private val gameSyncService: GameSyncService,
    private val scoringService: ScoringService,
    private val seriesFinalizationService: SeriesFinalizationService,
    private val fantasyTeamRosterPruningService: FantasyTeamRosterPruningService,
    private val applicationEventPublisher: ApplicationEventPublisher,
) {

    @Transactional
    fun createSeries(tournamentId: Long, request: CreateSeriesRequest): SeriesDto {
        val tournament = tournamentRepository.findById(tournamentId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Tournament $tournamentId not found")
        }
        val validated = validatedSeriesFields(tournament.kind, request)
        val s = seriesRepository.save(
            Series(
                tournament = tournament,
                name = request.name.trim(),
                namePrefix = validated.namePrefix,
                gameNumFrom = validated.gameNumFrom,
                gameNumTo = validated.gameNumTo,
                gamePhase = validated.gamePhase,
                status = request.status,
                startsAt = request.startsAt,
                teamDeadline = request.teamDeadline,
            ),
        )
        bootstrapSystemLeagues(s)
        upsertDeadlineReminder(s)
        if (s.status == SeriesStatus.FINISHED && !s.finalized) {
            seriesFinalizationService.finalizeSeries(s.id!!)
        }
        return seriesRepository.findById(s.id!!).get().toDto(emptyList(), 0L, 0L)
    }

    @Transactional
    fun updateSeries(id: Long, request: UpdateSeriesRequest): SeriesDto {
        val s = seriesRepository.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Series $id not found")
        }
        val kind = s.tournament!!.kind
        val previousStatus = s.status
        request.name?.let { s.name = it.trim() }
        request.status?.let { s.status = it }
        request.startsAt?.let { s.startsAt = it }
        request.teamDeadline?.let { s.teamDeadline = it }
        when (kind) {
            TournamentKind.STANDALONE -> {
                request.namePrefix?.let { p ->
                    val t = p.trim()
                    if (t.isEmpty()) {
                        throw ResponseStatusException(HttpStatus.BAD_REQUEST, "namePrefix cannot be empty for STANDALONE")
                    }
                    s.namePrefix = t
                }
                if (request.gameNumFrom != null || request.gameNumTo != null) {
                    throw ResponseStatusException(HttpStatus.BAD_REQUEST, "game numbers are not used for STANDALONE tournaments")
                }
                if (request.gamePhaseSpecified && request.gamePhase != null) {
                    throw ResponseStatusException(HttpStatus.BAD_REQUEST, "gamePhase is not used for STANDALONE tournaments")
                }
                s.gamePhase = null
            }
            TournamentKind.POLEMICA_COMPETITION -> {
                val from = request.gameNumFrom ?: s.gameNumFrom
                val to = request.gameNumTo ?: s.gameNumTo
                if (request.gameNumFrom != null || request.gameNumTo != null) {
                    if (from == null || to == null) {
                        throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Both gameNumFrom and gameNumTo must be set")
                    }
                    if (from > to) {
                        throw ResponseStatusException(HttpStatus.BAD_REQUEST, "gameNumFrom must be <= gameNumTo")
                    }
                    s.gameNumFrom = from
                    s.gameNumTo = to
                }
                request.namePrefix?.let { s.namePrefix = it.trim().takeIf { x -> x.isNotEmpty() } }
                if (request.gamePhaseSpecified) {
                    s.gamePhase = request.gamePhase
                }
            }
        }
        val saved = seriesRepository.save(s)
        upsertDeadlineReminder(saved)
        if (saved.status == SeriesStatus.FINISHED && !saved.finalized) {
            seriesFinalizationService.finalizeSeries(saved.id!!)
        }
        if (previousStatus == SeriesStatus.UPCOMING && saved.status == SeriesStatus.ACTIVE) {
            applicationEventPublisher.publishEvent(
                SeriesBatchStartedEvent(startedSeries = listOf(saved.toStartedSeriesInfo())),
            )
        }
        val result = seriesRepository.findById(id).get()
        val counts = gameCountsForSeriesIds(listOf(id))[id] ?: (0L to 0L)
        return result.toDto(tournamentPlayerIdsForSeries(id), counts.first, counts.second)
    }

    @Transactional
    fun batchStartSeries(seriesIds: List<Long>): BatchStartSeriesResponse {
        val started = mutableListOf<StartedSeriesEntry>()
        val skipped = mutableListOf<SkippedSeriesEntry>()
        val startedInfos = mutableListOf<StartedSeriesInfo>()

        for (seriesId in seriesIds.distinct()) {
            val series = seriesRepository.findById(seriesId).orElse(null)
            if (series == null) {
                skipped.add(SkippedSeriesEntry(seriesId = seriesId, reason = "Not found"))
                continue
            }
            if (series.status != SeriesStatus.UPCOMING) {
                skipped.add(SkippedSeriesEntry(seriesId = seriesId, reason = "Already ${series.status.name}"))
                continue
            }
            val previousStatus = series.status
            series.status = SeriesStatus.ACTIVE
            val saved = seriesRepository.save(series)
            started.add(
                StartedSeriesEntry(
                    seriesId = saved.id!!,
                    name = saved.name,
                    tournamentName = saved.tournament!!.name,
                    previousStatus = previousStatus.name,
                ),
            )
            startedInfos.add(saved.toStartedSeriesInfo())
        }

        if (startedInfos.isNotEmpty()) {
            applicationEventPublisher.publishEvent(SeriesBatchStartedEvent(startedSeries = startedInfos))
        }
        return BatchStartSeriesResponse(
            startedSeries = started,
            skipped = skipped,
            notificationRecipientCount = 0,
        )
    }

    private fun validatedSeriesFields(
        kind: TournamentKind,
        request: CreateSeriesRequest,
    ): ValidatedSeriesFields =
        when (kind) {
            TournamentKind.STANDALONE -> {
                val p = request.namePrefix?.trim()?.takeIf { it.isNotEmpty() }
                    ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "namePrefix is required for STANDALONE tournaments")
                if (request.gameNumFrom != null || request.gameNumTo != null) {
                    throw ResponseStatusException(HttpStatus.BAD_REQUEST, "game numbers are not used for STANDALONE tournaments")
                }
                ValidatedSeriesFields(
                    namePrefix = p,
                    gameNumFrom = null,
                    gameNumTo = null,
                    gamePhase = null,
                )
            }
            TournamentKind.POLEMICA_COMPETITION -> {
                val from = request.gameNumFrom
                    ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "gameNumFrom is required for POLEMICA_COMPETITION tournaments")
                val to = request.gameNumTo
                    ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "gameNumTo is required for POLEMICA_COMPETITION tournaments")
                if (from > to) {
                    throw ResponseStatusException(HttpStatus.BAD_REQUEST, "gameNumFrom must be <= gameNumTo")
                }
                val prefix = request.namePrefix?.trim()?.takeIf { it.isNotEmpty() }
                ValidatedSeriesFields(
                    namePrefix = prefix,
                    gameNumFrom = from,
                    gameNumTo = to,
                    gamePhase = request.gamePhase,
                )
            }
        }

    @Transactional
    fun assignPlayers(seriesId: Long, request: AssignSeriesPlayersRequest): SeriesDto {
        val series = seriesRepository.findById(seriesId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Series $seriesId not found")
        }
        val tournamentId = series.tournament!!.id!!
        val ids = request.tournamentPlayerIds.distinct()
        ids.forEach { tpId ->
            val tp = tournamentPlayerRepository.findByIdAndTournament_Id(tpId, tournamentId)
                ?: throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Tournament player $tpId is not part of tournament $tournamentId",
                )
        }
        val previousTpIds = seriesPlayerRepository.findAllBySeries_IdWithTournamentPlayers(seriesId)
            .map { it.tournamentPlayer!!.id!! }
            .toSet()
        seriesPlayerRepository.deleteAllBySeries_Id(seriesId)
        seriesPlayerRepository.flush()
        ids.forEach { tpId ->
            val tp = tournamentPlayerRepository.findById(tpId).get()
            seriesPlayerRepository.save(SeriesPlayer(series = series, tournamentPlayer = tp))
        }
        val newTpIds = ids.toSet()
        val removedTpIds = previousTpIds - newTpIds
        val addedTpIds = newTpIds - previousTpIds
        val replacementByRemovedFantasyPlayerId =
            replacementNicknameByRemovedFantasyPlayerId(removedTpIds, addedTpIds)
        val pruneResult = fantasyTeamRosterPruningService.pruneInvalidCardsForSeries(seriesId)
        if (pruneResult.prunedCards.isNotEmpty()) {
            val tournamentName = series.tournament!!.name
            val seriesName = series.name
            val recipients = pruneResult.prunedCards
                .groupBy { it.telegramChatId }
                .map { (chatId, cards) ->
                    SeriesRosterReplacementRecipient(
                        telegramChatId = chatId,
                        messageText = buildSeriesRosterReplacementTelegramMessage(
                            tournamentName = tournamentName,
                            seriesName = seriesName,
                            prunedCardsForUser = cards,
                            replacementNicknameByRemovedFantasyPlayerId = replacementByRemovedFantasyPlayerId,
                        ),
                    )
                }
            applicationEventPublisher.publishEvent(SeriesRosterReplacementNotificationEvent(recipients))
        }
        val counts = gameCountsForSeriesIds(listOf(seriesId))[seriesId] ?: (0L to 0L)
        return seriesRepository.findById(seriesId).get()
            .toDto(tournamentPlayerIdsForSeries(seriesId), counts.first, counts.second)
    }

    /**
     * When the same number of players were removed and added, pairs them by sorted [tournament_player] id (zip).
     */
    private fun replacementNicknameByRemovedFantasyPlayerId(
        removedTpIds: Set<Long>,
        addedTpIds: Set<Long>,
    ): Map<Long, String> {
        if (removedTpIds.size != addedTpIds.size || removedTpIds.isEmpty()) {
            return emptyMap()
        }
        val removedSorted = removedTpIds.sorted()
        val addedSorted = addedTpIds.sorted()
        val out = LinkedHashMap<Long, String>()
        for (i in removedSorted.indices) {
            val r = tournamentPlayerRepository.findById(removedSorted[i]).orElse(null) ?: continue
            val a = tournamentPlayerRepository.findById(addedSorted[i]).orElse(null) ?: continue
            val rFpId = r.fantasyPlayer?.id ?: continue
            val nick = a.fantasyPlayer?.nickname?.trim()?.takeIf { it.isNotEmpty() } ?: continue
            out[rFpId] = nick
        }
        return out
    }

    @Transactional(readOnly = true)
    fun listSeriesByTournament(tournamentId: Long): List<SeriesDto> {
        if (!tournamentRepository.existsById(tournamentId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Tournament $tournamentId not found")
        }
        val seriesList = seriesRepository.findAllByTournament_IdOrderByIdDesc(tournamentId)
        val sidList = seriesList.map { it.id!! }
        if (sidList.isEmpty()) return emptyList()
        val bySeriesId = seriesPlayerRepository.findAllBySeries_IdIn(sidList).groupBy { sp ->
            sp.series!!.id!!
        }
        val countMap = gameCountsForSeriesIds(sidList)
        return seriesList.map { s ->
            val tpIds = bySeriesId[s.id]?.map { it.tournamentPlayer!!.id!! }?.sorted() ?: emptyList()
            val counts = countMap[s.id!!] ?: (0L to 0L)
            s.toDto(tpIds, counts.first, counts.second)
        }
    }

    @Transactional(readOnly = true)
    fun getSeries(id: Long): SeriesDto {
        val s = seriesRepository.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Series $id not found")
        }
        val counts = gameCountsForSeriesIds(listOf(id))[id] ?: (0L to 0L)
        return s.toDto(tournamentPlayerIdsForSeries(id), counts.first, counts.second)
    }

    fun syncGames(seriesId: Long) {
        gameSyncService.syncGames(seriesId)
    }

    fun calculateScores(seriesId: Long) {
        scoringService.calculateScores(seriesId)
    }

    private fun upsertDeadlineReminder(series: Series) {
        val seriesId = series.id ?: return
        val remindAt = series.teamDeadline.minus(1, ChronoUnit.HOURS)
        val existing = deadlineReminderRepository.findBySeries_Id(seriesId)
        if (existing != null) {
            existing.remindAt = remindAt
            if (remindAt.isAfter(Instant.now()) && existing.sent) {
                existing.sent = false
                existing.sentAt = null
                existing.recipientCount = null
            }
            deadlineReminderRepository.save(existing)
            return
        }
        deadlineReminderRepository.save(
            DeadlineReminder(
                series = series,
                remindAt = remindAt,
            ),
        )
    }

    private fun tournamentPlayerIdsForSeries(seriesId: Long): List<Long> =
        seriesPlayerRepository.findAllBySeries_IdWithTournamentPlayers(seriesId).map { it.tournamentPlayer!!.id!! }

    private fun gameCountsForSeriesIds(seriesIds: List<Long>): Map<Long, Pair<Long, Long>> {
        if (seriesIds.isEmpty()) return emptyMap()
        val totalRows = seriesGameRepository.countTotalBySeriesIdIn(seriesIds)
        val scoredRows = seriesGameRepository.countScoredBySeriesIdIn(seriesIds)
        val totals = totalRows.associate { row ->
            val sid = (row[0] as Number).toLong()
            val c = (row[1] as Number).toLong()
            sid to c
        }
        val scored = scoredRows.associate { row ->
            val sid = (row[0] as Number).toLong()
            val c = (row[1] as Number).toLong()
            sid to c
        }
        return seriesIds.associateWith { id ->
            Pair(totals[id] ?: 0L, scored[id] ?: 0L)
        }
    }

    private fun bootstrapSystemLeagues(series: Series) {
        val seriesId = series.id ?: return
        val systemLeagues = leagueRepository.findAllByLeagueType(LeagueType.SYSTEM)
        for (league in systemLeagues) {
            if (seriesLeagueRepository.findBySeries_IdAndLeague_Id(seriesId, league.id!!) != null) {
                continue
            }
            seriesLeagueRepository.save(
                SeriesLeague(
                    series = series,
                    league = league,
                    enabled = true,
                ),
            )
        }
    }

    private fun Series.toStartedSeriesInfo(): StartedSeriesInfo = StartedSeriesInfo(
        seriesId = id!!,
        seriesName = name,
        tournamentId = tournament!!.id!!,
        tournamentName = tournament!!.name,
        teamDeadline = teamDeadline,
    )

    private fun Series.toDto(
        tournamentPlayerIds: List<Long>,
        syncedGamesCount: Long,
        scoredGamesCount: Long,
    ) = SeriesDto(
        id = id!!,
        tournamentId = tournament!!.id!!,
        name = name,
        namePrefix = namePrefix,
        gameNumFrom = gameNumFrom,
        gameNumTo = gameNumTo,
        gamePhase = gamePhase,
        status = status,
        startsAt = startsAt,
        teamDeadline = teamDeadline,
        finalized = finalized,
        syncedGamesCount = syncedGamesCount,
        scoredGamesCount = scoredGamesCount,
        tournamentPlayerIds = tournamentPlayerIds,
    )

    private data class ValidatedSeriesFields(
        val namePrefix: String?,
        val gameNumFrom: Long?,
        val gameNumTo: Long?,
        val gamePhase: Int?,
    )
}
