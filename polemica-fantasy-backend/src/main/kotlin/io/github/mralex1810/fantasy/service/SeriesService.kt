package io.github.mralex1810.fantasy.service

import com.fasterxml.jackson.databind.JsonNode
import com.github.mafia.vyasma.polemica.library.model.game.PolemicaGame
import io.github.mralex1810.fantasy.config.PolemicaProperties
import io.github.mralex1810.fantasy.dto.admin.request.AddSeriesGameRequest
import io.github.mralex1810.fantasy.dto.admin.request.AssignSeriesPlayersRequest
import io.github.mralex1810.fantasy.dto.admin.request.CreateSeriesRequest
import io.github.mralex1810.fantasy.dto.admin.request.UpdateSeriesRequest
import io.github.mralex1810.fantasy.dto.admin.response.AdminSeriesGameDto
import io.github.mralex1810.fantasy.dto.admin.response.BatchStartSeriesResponse
import io.github.mralex1810.fantasy.dto.admin.response.SeriesDto
import io.github.mralex1810.fantasy.dto.admin.response.SeriesPlayerMarketplaceUnlistResultDto
import io.github.mralex1810.fantasy.dto.admin.response.SkippedSeriesEntry
import io.github.mralex1810.fantasy.dto.admin.response.StartedSeriesEntry
import io.github.mralex1810.fantasy.entity.DeadlineReminder
import io.github.mralex1810.fantasy.entity.LeagueType
import io.github.mralex1810.fantasy.entity.MarketplaceListingStatus
import io.github.mralex1810.fantasy.entity.Series
import io.github.mralex1810.fantasy.entity.SeriesGame
import io.github.mralex1810.fantasy.entity.SeriesLeague
import io.github.mralex1810.fantasy.entity.SeriesPlayer
import io.github.mralex1810.fantasy.entity.SeriesStatus
import io.github.mralex1810.fantasy.entity.TournamentKind
import io.github.mralex1810.fantasy.entity.TournamentPlayer
import io.github.mralex1810.fantasy.event.SeriesBatchStartedEvent
import io.github.mralex1810.fantasy.event.SeriesRosterReplacementNotificationEvent
import io.github.mralex1810.fantasy.event.SeriesRosterReplacementRecipient
import io.github.mralex1810.fantasy.event.StartedSeriesInfo
import io.github.mralex1810.fantasy.event.buildSeriesRosterReplacementTelegramMessage
import io.github.mralex1810.fantasy.polemica.GameSyncService
import io.github.mralex1810.fantasy.polemica.PolemicaIntegrationService
import io.github.mralex1810.fantasy.repository.DeadlineReminderRepository
import io.github.mralex1810.fantasy.repository.FantasyPlayerAliasRepository
import io.github.mralex1810.fantasy.repository.FantasyTeamCardGameScoreRepository
import io.github.mralex1810.fantasy.repository.FantasyTeamRepository
import io.github.mralex1810.fantasy.repository.LeagueRepository
import io.github.mralex1810.fantasy.repository.MarketplaceListingRepository
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
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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
    private val marketplaceListingRepository: MarketplaceListingRepository,
    private val seriesLeagueRepository: SeriesLeagueRepository,
    private val fantasyTeamRepository: FantasyTeamRepository,
    private val fantasyTeamCardGameScoreRepository: FantasyTeamCardGameScoreRepository,
    private val polemicaProperties: PolemicaProperties,
    private val polemicaIntegrationService: PolemicaIntegrationService,
    private val gameSyncService: GameSyncService,
    private val scoringService: ScoringService,
    private val seriesFinalizationService: SeriesFinalizationService,
    private val fantasyTeamRosterPruningService: FantasyTeamRosterPruningService,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val fantasyPlayerAliasRepository: FantasyPlayerAliasRepository,
    platformTransactionManager: PlatformTransactionManager,
) {

    private val transactionTemplate = TransactionTemplate(platformTransactionManager)

    @Transactional
    fun createSeries(tournamentId: Long, request: CreateSeriesRequest): SeriesDto {
        val tournament = tournamentRepository.findById(tournamentId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Tournament $tournamentId not found")
        }
        val validated = validatedSeriesFields(tournament.kind, request)
        val name = request.name.trim()
        val s = seriesRepository.save(
            Series(
                tournament = tournament,
                name = name,
                publicNumber = derivePublicNumber(name),
                namePrefix = validated.namePrefix,
                gameNumFrom = validated.gameNumFrom,
                gameNumTo = validated.gameNumTo,
                gamePhase = validated.gamePhase,
                gameStartedOn = validated.gameStartedOn,
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
        return seriesRepository.findById(s.id!!).get().toDto(emptyList(), emptyMap(), 0L, 0L)
    }

    @Transactional
    fun updateSeries(id: Long, request: UpdateSeriesRequest): SeriesDto {
        val s = seriesRepository.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Series $id not found")
        }
        val kind = s.tournament!!.kind
        val previousStatus = s.status
        request.name?.let {
            val name = it.trim()
            s.name = name
            s.publicNumber = derivePublicNumber(name)
        }
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
                if (request.gameStartedOnSpecified) {
                    s.gameStartedOn = request.gameStartedOn
                }
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
                if (request.gameStartedOnSpecified && request.gameStartedOn != null) {
                    throw ResponseStatusException(HttpStatus.BAD_REQUEST, "gameStartedOn is only used for STANDALONE tournaments")
                }
                s.gameStartedOn = null
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
        val assignment = playerAssignmentForSeries(id)
        return result.toDto(assignment.tournamentPlayerIds, assignment.replacementPolemicaUserIds, counts.first, counts.second)
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
                    gameStartedOn = request.gameStartedOn,
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
                if (request.gameStartedOn != null) {
                    throw ResponseStatusException(HttpStatus.BAD_REQUEST, "gameStartedOn is only used for STANDALONE tournaments")
                }
                val prefix = request.namePrefix?.trim()?.takeIf { it.isNotEmpty() }
                ValidatedSeriesFields(
                    namePrefix = prefix,
                    gameNumFrom = from,
                    gameNumTo = to,
                    gamePhase = request.gamePhase,
                    gameStartedOn = null,
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
        val selectedIds = ids.toSet()
        val tournamentPlayersById = ids.associateWith { tpId ->
            tournamentPlayerRepository.findByIdAndTournament_Id(tpId, tournamentId)
                ?: throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Tournament player $tpId is not part of tournament $tournamentId",
                )
        }
        val unknownReplacementKeys = request.replacementPolemicaUserIds.keys - selectedIds
        if (unknownReplacementKeys.isNotEmpty()) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Replacement specified for unselected tournament player ${unknownReplacementKeys.sorted().first()}",
            )
        }
        val replacementByTournamentPlayerId = validateSeriesPlayerReplacements(
            tournamentPlayersById = tournamentPlayersById,
            rawReplacementByTournamentPlayerId = request.replacementPolemicaUserIds,
        )
        val previousTpIds = seriesPlayerRepository.findAllBySeries_IdWithTournamentPlayers(seriesId)
            .map { it.tournamentPlayer!!.id!! }
            .toSet()
        seriesPlayerRepository.deleteAllBySeries_Id(seriesId)
        seriesPlayerRepository.flush()
        ids.forEach { tpId ->
            val tp = tournamentPlayersById.getValue(tpId)
            seriesPlayerRepository.save(
                SeriesPlayer(
                    series = series,
                    tournamentPlayer = tp,
                    replacementPolemicaUserId = replacementByTournamentPlayerId[tpId],
                ),
            )
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
        val assignment = playerAssignmentForSeries(seriesId)
        return seriesRepository.findById(seriesId).get()
            .toDto(assignment.tournamentPlayerIds, assignment.replacementPolemicaUserIds, counts.first, counts.second)
    }

    private fun validateSeriesPlayerReplacements(
        tournamentPlayersById: Map<Long, TournamentPlayer>,
        rawReplacementByTournamentPlayerId: Map<Long, Long?>,
    ): Map<Long, Long> {
        val aliasesByTournamentPlayerId = tournamentPlayersById.mapValues { (_, tp) ->
            aliasesForFantasyPlayer(tp.fantasyPlayer!!)
        }
        val mainPolemicaUserIds = aliasesByTournamentPlayerId.values.flatten().toSet()
        val replacements = rawReplacementByTournamentPlayerId
            .mapNotNull { (tournamentPlayerId, replacementPolemicaUserId) ->
                replacementPolemicaUserId?.let { tournamentPlayerId to it }
            }
            .toMap()

        replacements.forEach { (tournamentPlayerId, replacementPolemicaUserId) ->
            if (replacementPolemicaUserId <= 0) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Replacement Polemica user id must be positive")
            }
            val mainAliases = aliasesByTournamentPlayerId.getValue(tournamentPlayerId)
            if (replacementPolemicaUserId in mainAliases) {
                throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Replacement Polemica user id cannot equal any alias of the main player",
                )
            }
            if (replacementPolemicaUserId in mainPolemicaUserIds) {
                throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Replacement Polemica user id cannot match another selected main player id",
                )
            }
        }

        val duplicateReplacement = replacements.values
            .groupingBy { it }
            .eachCount()
            .filterValues { it > 1 }
            .keys
            .firstOrNull()
        if (duplicateReplacement != null) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Replacement Polemica user id $duplicateReplacement is used more than once",
            )
        }
        return replacements
    }

    private fun aliasesForFantasyPlayer(fantasyPlayer: io.github.mralex1810.fantasy.entity.FantasyPlayer): List<Long> {
        val fantasyPlayerId = fantasyPlayer.id ?: return listOf(fantasyPlayer.polemicaUserId)
        return fantasyPlayerAliasRepository.findPolemicaUserIdsByFantasyPlayerId(fantasyPlayerId)
            .ifEmpty { listOf(fantasyPlayer.polemicaUserId) }
    }

    @Transactional
    fun unlistMarketplaceListingsForTournamentPlayer(
        seriesId: Long,
        tournamentPlayerId: Long,
    ): SeriesPlayerMarketplaceUnlistResultDto {
        val series = seriesRepository.findById(seriesId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Series $seriesId not found")
        }
        val tournament = series.tournament ?: throw ResponseStatusException(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Series $seriesId has no tournament",
        )
        val tp = tournamentPlayerRepository.findByIdAndTournament_Id(tournamentPlayerId, tournament.id!!)
            ?: throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Tournament player $tournamentPlayerId is not part of tournament ${tournament.id}",
            )
        val fantasyPlayer = tp.fantasyPlayer ?: throw ResponseStatusException(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Tournament player $tournamentPlayerId has no fantasy player",
        )
        val cancelled = marketplaceListingRepository.cancelAllActiveByFantasyPlayerId(
            fantasyPlayerId = fantasyPlayer.id!!,
            active = MarketplaceListingStatus.ACTIVE,
            cancelled = MarketplaceListingStatus.CANCELLED,
        )
        return SeriesPlayerMarketplaceUnlistResultDto(
            tournamentPlayerId = tournamentPlayerId,
            fantasyPlayerId = fantasyPlayer.id!!,
            playerNickname = fantasyPlayer.nickname,
            cancelledListings = cancelled,
        )
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
            val replacementByTpId = bySeriesId[s.id]
                ?.mapNotNull { sp ->
                    val tpId = sp.tournamentPlayer!!.id!!
                    sp.replacementPolemicaUserId?.let { tpId to it }
                }
                ?.toMap()
                ?: emptyMap()
            val counts = countMap[s.id!!] ?: (0L to 0L)
            s.toDto(tpIds, replacementByTpId, counts.first, counts.second)
        }
    }

    @Transactional(readOnly = true)
    fun getSeries(id: Long): SeriesDto {
        val s = seriesRepository.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Series $id not found")
        }
        val counts = gameCountsForSeriesIds(listOf(id))[id] ?: (0L to 0L)
        val assignment = playerAssignmentForSeries(id)
        return s.toDto(assignment.tournamentPlayerIds, assignment.replacementPolemicaUserIds, counts.first, counts.second)
    }

    fun syncGames(seriesId: Long) {
        gameSyncService.syncGames(seriesId)
    }

    fun calculateScores(seriesId: Long) {
        scoringService.calculateScores(seriesId)
    }

    @Transactional(readOnly = true)
    fun listSeriesGames(seriesId: Long): List<AdminSeriesGameDto> {
        if (!seriesRepository.existsById(seriesId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Series $seriesId not found")
        }
        return seriesGameRepository.findAllBySeries_Id(seriesId)
            .sortedWith(
                compareBy<SeriesGame>(
                    { it.playedAt ?: Instant.MAX },
                    { it.polemicaGameId },
                    { it.id ?: Long.MAX_VALUE },
                ),
            )
            .map { it.toAdminSeriesGameDto() }
    }

    fun addSeriesGame(seriesId: Long, request: AddSeriesGameRequest): AdminSeriesGameDto {
        val polemicaGameId = request.polemicaGameId
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "polemicaGameId is required")
        val context = seriesGameAdminContext(seriesId)
        ensureSeriesGamesMutable(context.finalized)
        ensurePolemicaCredentialsConfigured()
        val game = fetchManualSeriesGame(context, polemicaGameId)
        val storedPolemicaGameId = game.id ?: polemicaGameId
        val prepared = PreparedSeriesGame(
            polemicaGameId = storedPolemicaGameId,
            resolvedName = resolvedStoredGameName(game, storedPolemicaGameId),
            gameDataJson = polemicaIntegrationService.toJsonNode(game),
            playedAt = game.started.atZone(ZoneId.systemDefault()).toInstant(),
        )
        val saved = transactionTemplate.execute {
            val series = seriesRepository.findById(seriesId).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Series $seriesId not found")
            }
            ensureSeriesGamesMutable(series.finalized)
            val row = seriesGameRepository.findBySeries_IdAndPolemicaGameId(seriesId, prepared.polemicaGameId)
                ?: SeriesGame(
                    series = series,
                    polemicaGameId = prepared.polemicaGameId,
                )
            row.gameName = prepared.resolvedName
            row.gameDataCache = prepared.gameDataJson
            row.playedAt = prepared.playedAt
            row.scored = false
            seriesGameRepository.save(row)
        } ?: throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to save series game")
        return saved.toAdminSeriesGameDto()
    }

    @Transactional
    fun deleteSeriesGame(seriesId: Long, gameId: Long) {
        val series = seriesRepository.findById(seriesId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Series $seriesId not found")
        }
        ensureSeriesGamesMutable(series.finalized)
        val game = seriesGameRepository.findByIdAndSeries_Id(gameId, seriesId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Series game $gameId not found")
        fantasyTeamCardGameScoreRepository.deleteAllBySeriesGameId(game.id!!)
        fantasyTeamCardGameScoreRepository.flush()
        seriesGameRepository.delete(game)
        seriesGameRepository.flush()
        recomputeStoredScoresForSeries(seriesId)
    }

    private fun seriesGameAdminContext(seriesId: Long): SeriesGameAdminContext {
        val series = seriesRepository.findByIdWithTournament(seriesId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Series $seriesId not found")
        val tournament = series.tournament ?: throw ResponseStatusException(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Series $seriesId has no tournament",
        )
        return SeriesGameAdminContext(
            finalized = series.finalized,
            tournamentKind = tournament.kind,
            polemicaCompetitionId = tournament.polemicaCompetitionId,
        )
    }

    private fun fetchManualSeriesGame(context: SeriesGameAdminContext, polemicaGameId: Long): PolemicaGame =
        when (context.tournamentKind) {
            TournamentKind.STANDALONE -> polemicaIntegrationService.loadMatch(polemicaGameId)
            TournamentKind.POLEMICA_COMPETITION -> {
                val competitionId = context.polemicaCompetitionId
                    ?: throw ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Tournament polemica_competition_id is missing for POLEMICA_COMPETITION",
                    )
                val ref = polemicaIntegrationService.listCompetitionGameReferences(competitionId)
                    .firstOrNull { it.id == polemicaGameId }
                    ?: throw ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Polemica game $polemicaGameId is not part of competition $competitionId",
                    )
                polemicaIntegrationService.loadGameFromCompetition(competitionId, ref.id, ref.version)
            }
        }

    private fun ensurePolemicaCredentialsConfigured() {
        if (polemicaProperties.username.isBlank() || polemicaProperties.password.isBlank()) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Polemica API credentials are not configured (POLEMICA_USERNAME / POLEMICA_PASSWORD)",
            )
        }
    }

    private fun ensureSeriesGamesMutable(finalized: Boolean) {
        if (finalized) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Cannot change games for finalized series")
        }
    }

    private fun recomputeStoredScoresForSeries(seriesId: Long) {
        val teams = fantasyTeamRepository.findAllBySeries_IdWithCards(seriesId)
        val cards = teams.flatMap { it.cards }
        val cardIds = cards.mapNotNull { it.id }
        val scoreByCardId = if (cardIds.isEmpty()) {
            emptyMap()
        } else {
            fantasyTeamCardGameScoreRepository.sumTotalScoreByFantasyTeamCardIdIn(cardIds).associate { row ->
                val cardId = (row[0] as Number).toLong()
                val totalScore = (row[1] as Number?)?.toDouble() ?: 0.0
                cardId to totalScore
            }
        }
        for (team in teams) {
            var teamTotal = 0.0
            for (card in team.cards) {
                val cardScore = card.id?.let { scoreByCardId[it] } ?: 0.0
                card.score = cardScore
                teamTotal += cardScore
            }
            team.totalScore = teamTotal
        }
    }

    private fun SeriesGame.toAdminSeriesGameDto(): AdminSeriesGameDto {
        val node = gameDataCache
        return AdminSeriesGameDto(
            id = id!!,
            polemicaGameId = polemicaGameId,
            displayName = formatSeriesGameDisplayName(this),
            gameName = gameName,
            gameNum = node?.intField("num"),
            table = node?.intField("table"),
            phase = node?.intField("phase"),
            playedAt = playedAt,
            finished = node?.hasNonNullField("result") ?: false,
            scored = scored,
        )
    }

    private fun JsonNode.intField(name: String): Int? {
        val node = path(name)
        return if (node.isMissingNode || node.isNull) null else node.asInt()
    }

    private fun JsonNode.hasNonNullField(name: String): Boolean {
        val node = path(name)
        return !node.isMissingNode && !node.isNull
    }

    private fun resolvedStoredGameName(game: PolemicaGame, fallbackGameId: Long): String {
        val trimmed = game.name?.trim().orEmpty()
        if (trimmed.isNotEmpty()) return trimmed
        val num = game.num
        return if (num != null) "Игра $num" else "Игра #$fallbackGameId"
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

    private fun playerAssignmentForSeries(seriesId: Long): SeriesPlayerAssignment {
        val rows = seriesPlayerRepository.findAllBySeries_IdWithTournamentPlayers(seriesId)
        return SeriesPlayerAssignment(
            tournamentPlayerIds = rows.map { it.tournamentPlayer!!.id!! },
            replacementPolemicaUserIds = rows.mapNotNull { sp ->
                val tpId = sp.tournamentPlayer!!.id!!
                sp.replacementPolemicaUserId?.let { tpId to it }
            }.toMap(),
        )
    }

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
        replacementPolemicaUserIds: Map<Long, Long>,
        syncedGamesCount: Long,
        scoredGamesCount: Long,
    ) = SeriesDto(
        id = id!!,
        tournamentId = tournament!!.id!!,
        name = name,
        publicNumber = publicNumber,
        namePrefix = namePrefix,
        gameNumFrom = gameNumFrom,
        gameNumTo = gameNumTo,
        gamePhase = gamePhase,
        gameStartedOn = gameStartedOn,
        status = status,
        startsAt = startsAt,
        teamDeadline = teamDeadline,
        finalized = finalized,
        syncedGamesCount = syncedGamesCount,
        scoredGamesCount = scoredGamesCount,
        tournamentPlayerIds = tournamentPlayerIds,
        replacementPolemicaUserIds = replacementPolemicaUserIds,
    )

    private fun derivePublicNumber(name: String): Long =
        Regex("""\d+""").findAll(name).lastOrNull()?.value?.toLongOrNull() ?: 1

    private data class SeriesPlayerAssignment(
        val tournamentPlayerIds: List<Long>,
        val replacementPolemicaUserIds: Map<Long, Long>,
    )

    private data class SeriesGameAdminContext(
        val finalized: Boolean,
        val tournamentKind: TournamentKind,
        val polemicaCompetitionId: Long?,
    )

    private data class PreparedSeriesGame(
        val polemicaGameId: Long,
        val resolvedName: String,
        val gameDataJson: JsonNode,
        val playedAt: Instant,
    )

    private data class ValidatedSeriesFields(
        val namePrefix: String?,
        val gameNumFrom: Long?,
        val gameNumTo: Long?,
        val gamePhase: Int?,
        val gameStartedOn: LocalDate?,
    )
}
