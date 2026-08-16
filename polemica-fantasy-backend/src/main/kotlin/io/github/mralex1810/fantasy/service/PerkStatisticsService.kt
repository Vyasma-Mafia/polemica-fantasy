package io.github.mralex1810.fantasy.service

import com.github.mafia.vyasma.polemica.library.client.GamePointsService
import com.github.mafia.vyasma.polemica.library.model.game.PolemicaGame
import io.github.mralex1810.fantasy.dto.admin.request.PerkStatisticsRequest
import io.github.mralex1810.fantasy.dto.admin.response.PerkAnomalySettingsDto
import io.github.mralex1810.fantasy.dto.admin.response.PerkFrequencyRowDto
import io.github.mralex1810.fantasy.dto.admin.response.PerkPlayerAnomalyDto
import io.github.mralex1810.fantasy.dto.admin.response.PerkStatisticsReportDto
import io.github.mralex1810.fantasy.dto.admin.response.MatchLoadFailureDto
import io.github.mralex1810.fantasy.entity.Perk
import io.github.mralex1810.fantasy.entity.FantasyPlayer
import io.github.mralex1810.fantasy.polemica.PolemicaIntegrationService
import io.github.mralex1810.fantasy.repository.PerkRepository
import io.github.mralex1810.fantasy.repository.FantasyPlayerAliasRepository
import io.github.mralex1810.fantasy.repository.FantasyPlayerRepository
import io.github.mralex1810.fantasy.scoring.perk.PerkDetectorRegistry
import io.github.mralex1810.fantasy.scoring.perk.ScoringContext
import io.github.mralex1810.fantasy.scoring.appliedOccurrences
import io.github.mralex1810.fantasy.scoring.isFinishedForScoring
import io.github.mralex1810.fantasy.scoring.isRoleApplicable
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class PerkStatisticsService(
    private val fantasyPlayerRepository: FantasyPlayerRepository,
    private val fantasyPlayerAliasRepository: FantasyPlayerAliasRepository,
    private val perkRepository: PerkRepository,
    private val perkRegistry: PerkDetectorRegistry,
    private val polemicaIntegrationService: PolemicaIntegrationService,
    private val gamePointsService: GamePointsService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Collects per-perk frequencies by loading each fantasy player's first profile page (100 games),
     * deduplicating match ids, then running the same [PerkDetector] + role checks as scoring.
     */
    fun collectReport(request: PerkStatisticsRequest? = null): PerkStatisticsReportDto {
        val anomalySettings = request.toAnomalySettings()
        val generatedAt = Instant.now()
        val fantasyPlayers = fantasyPlayerRepository.findAll()
        val polemicaUserIdsByFantasyPlayerId = fantasyPlayers.associate { fp ->
            val fantasyPlayerId = fp.id!!
            fantasyPlayerId to fantasyPlayerAliasRepository.findPolemicaUserIdsByFantasyPlayerId(fantasyPlayerId)
                .ifEmpty { listOf(fp.polemicaUserId) }
        }
        val uniqueMatchIds = LinkedHashSet<Long>()
        var profileFetchFailures = 0

        for (userId in polemicaUserIdsByFantasyPlayerId.values.flatten().distinct()) {
            try {
                val rows = polemicaIntegrationService.fetchProfileGamesFirstPageForStatistics(userId)
                for (row in rows) {
                    uniqueMatchIds.add(row.id)
                }
            } catch (e: Exception) {
                profileFetchFailures++
                log.warn("getProfileGames failed for one player: {}", e.message)
            }
        }

        val perks = perkRepository.findAllWithApplicableRoles()
        val aggregates = perks.associate { it.id to MutablePerkAggregate() }
        val playerAggregatesByFantasyPlayerId = fantasyPlayers
            .mapNotNull { fp -> fp.id?.let { it to MutablePlayerAggregate(fp, perks) } }
            .toMap()
        val playerAggregatesByPolemicaUserId = polemicaUserIdsByFantasyPlayerId
            .flatMap { (fantasyPlayerId, polemicaUserIds) ->
                val aggregate = playerAggregatesByFantasyPlayerId.getValue(fantasyPlayerId)
                polemicaUserIds.map { it to aggregate }
            }
            .toMap()
        val loadFailures = mutableListOf<MatchLoadFailureDto>()
        var gamesLoaded = 0
        var totalPlayerSlots = 0L

        for (matchId in uniqueMatchIds) {
            val game = try {
                polemicaIntegrationService.loadMatch(matchId)
            } catch (e: Exception) {
                loadFailures.add(MatchLoadFailureDto(matchId, e.message))
                log.warn("getMatch failed for matchId={}: {}", matchId, e.message)
                continue
            }
            gamesLoaded++
            if (!game.isFinishedForScoring()) {
                continue
            }
            val pointsByPosition =
                try {
                    gamePointsService.fetchPlayerStats(matchId).associate { it.position to it.points }
                } catch (e: Exception) {
                    log.warn("fetchPlayerStats failed for matchId={}: {}", matchId, e.message)
                    null
                }
            totalPlayerSlots += accumulateForGame(
                game,
                perks,
                aggregates,
                playerAggregatesByPolemicaUserId,
                pointsByPosition,
            )
        }

        val catalogIds = perks.map { it.id }.sorted()
        val byPerk = catalogIds.map { id ->
            val a = aggregates[id] ?: MutablePerkAggregate()
            PerkFrequencyRowDto(
                perkId = id,
                applicableSlots = a.applicableSlots,
                sumRawMatchCount = a.sumRawMatchCount,
                slotsWithPositiveRaw = a.slotsWithPositiveRaw,
                sumAppliedOccurrences = a.sumAppliedOccurrences,
            )
        }
        val anomalies = buildAnomalies(
            anomalySettings,
            perks,
            aggregates,
            totalPlayerSlots,
            playerAggregatesByFantasyPlayerId.values,
        )

        return PerkStatisticsReportDto(
            generatedAt = generatedAt,
            fantasyPlayerCount = fantasyPlayers.size,
            profileFetchFailures = profileFetchFailures,
            uniqueMatchIdsFromProfiles = uniqueMatchIds.size,
            uniqueGamesLoaded = gamesLoaded,
            totalPlayerSlots = totalPlayerSlots,
            gamesFailedToLoad = loadFailures.size,
            loadFailures = loadFailures.take(MAX_FAILURES_IN_RESPONSE),
            perkIdsInCatalog = catalogIds,
            byPerk = byPerk,
            anomalySettings = anomalySettings,
            anomalies = anomalies,
        )
    }

    private fun accumulateForGame(
        game: PolemicaGame,
        perks: List<Perk>,
        aggregates: Map<String, MutablePerkAggregate>,
        playerAggregatesByPolemicaUserId: Map<Long, MutablePlayerAggregate>,
        pointsByPosition: Map<Int, Double>?,
    ): Long {
        val players = game.players.orEmpty()
        for (player in players) {
            val playerAggregate = player.player?.id?.let { playerAggregatesByPolemicaUserId[it] }
            if (playerAggregate != null) {
                playerAggregate.games++
            }
            val basePoints =
                when (pointsByPosition) {
                    null -> Double.NaN
                    else -> pointsByPosition[player.position.value] ?: 0.0
                }
            val scoringContext = ScoringContext(basePoints = basePoints)
            for (perk in perks) {
                val det = perkRegistry.detector(perk.id) ?: continue
                if (!isRoleApplicable(perk, player)) continue
                val agg = aggregates.getValue(perk.id)
                val raw = det.matchCount(game, player, scoringContext)
                val applied = appliedOccurrences(raw, perk.occurrenceType)
                agg.add(raw, applied)
                playerAggregate?.byPerk?.getValue(perk.id)?.add(raw, applied)
            }
        }
        return players.size.toLong()
    }

    private fun buildAnomalies(
        settings: PerkAnomalySettingsDto,
        perks: List<Perk>,
        globalAggregates: Map<String, MutablePerkAggregate>,
        totalPlayerSlots: Long,
        playerAggregates: Collection<MutablePlayerAggregate>,
    ): List<PerkPlayerAnomalyDto> {
        val perksById = perks.associateBy { it.id }
        val rows = mutableListOf<PerkPlayerAnomalyDto>()

        for (playerAggregate in playerAggregates) {
            if (playerAggregate.games < settings.minPlayerGames) continue
            val fantasyPlayer = playerAggregate.fantasyPlayer
            val fantasyPlayerId = fantasyPlayer.id ?: continue

            for ((perkId, playerPerkAggregate) in playerAggregate.byPerk) {
                if (playerPerkAggregate.applicableSlots < settings.minApplicableSlots) continue

                val perk = perksById[perkId] ?: continue
                val globalAggregate = globalAggregates[perkId] ?: continue
                val globalOccurrencesPerGame = rate(globalAggregate.sumAppliedOccurrences, totalPlayerSlots)
                val playerOccurrencesPerGame = rate(
                    playerPerkAggregate.sumAppliedOccurrences,
                    playerAggregate.games,
                )
                val smoothedPlayerOccurrencesPerGame =
                    smoothedRate(
                        playerPerkAggregate.sumAppliedOccurrences,
                        playerAggregate.games,
                        globalOccurrencesPerGame,
                        settings.priorGames,
                    )
                val excessOccurrencesPerGame = smoothedPlayerOccurrencesPerGame - globalOccurrencesPerGame
                if (excessOccurrencesPerGame <= 0.0) continue

                val baselineBonusPerGame = globalOccurrencesPerGame * perk.bonusPoints
                val expectedBonusPerGame = smoothedPlayerOccurrencesPerGame * perk.bonusPoints
                rows.add(
                    PerkPlayerAnomalyDto(
                        fantasyPlayerId = fantasyPlayerId,
                        polemicaUserId = fantasyPlayer.polemicaUserId,
                        nickname = fantasyPlayer.nickname,
                        perkId = perk.id,
                        perkName = perk.name,
                        playerGames = playerAggregate.games,
                        applicableSlots = playerPerkAggregate.applicableSlots,
                        sumAppliedOccurrences = playerPerkAggregate.sumAppliedOccurrences,
                        globalOccurrencesPerGame = globalOccurrencesPerGame,
                        playerOccurrencesPerGame = playerOccurrencesPerGame,
                        smoothedPlayerOccurrencesPerGame = smoothedPlayerOccurrencesPerGame,
                        playerOccurrencesPerApplicableSlot = nullableRate(
                            playerPerkAggregate.sumAppliedOccurrences,
                            playerPerkAggregate.applicableSlots,
                        ),
                        globalOccurrencesPerApplicableSlot = nullableRate(
                            globalAggregate.sumAppliedOccurrences,
                            globalAggregate.applicableSlots,
                        ),
                        lift = when {
                            globalOccurrencesPerGame > 0.0 -> smoothedPlayerOccurrencesPerGame /
                                globalOccurrencesPerGame
                            else -> null
                        },
                        baselineBonusPerGame = baselineBonusPerGame,
                        expectedBonusPerGame = expectedBonusPerGame,
                        excessBonusPerGame = expectedBonusPerGame - baselineBonusPerGame,
                    ),
                )
            }
        }

        return rows
            .sortedWith(
                compareByDescending<PerkPlayerAnomalyDto> { it.excessBonusPerGame }
                    .thenByDescending { it.lift ?: 0.0 }
                    .thenBy { it.perkId }
                    .thenBy { it.nickname },
            )
            .take(settings.maxAnomalies)
    }

    private fun PerkStatisticsRequest?.toAnomalySettings(): PerkAnomalySettingsDto =
        PerkAnomalySettingsDto(
            minPlayerGames = this?.minPlayerGames?.coerceAtLeast(1) ?: DEFAULT_MIN_PLAYER_GAMES,
            minApplicableSlots = this?.minApplicableSlots?.coerceAtLeast(0) ?: DEFAULT_MIN_APPLICABLE_SLOTS,
            priorGames = this?.priorGames?.takeIf { it.isFiniteNumber() && it >= 0.0 } ?: DEFAULT_PRIOR_GAMES,
            maxAnomalies = this?.maxAnomalies?.coerceIn(1, MAX_ANOMALIES_IN_RESPONSE)
                ?: DEFAULT_MAX_ANOMALIES,
        )

    private fun Double.isFiniteNumber(): Boolean =
        !isNaN() && this != Double.POSITIVE_INFINITY && this != Double.NEGATIVE_INFINITY

    private fun rate(numerator: Long, denominator: Long): Double =
        when {
            denominator > 0 -> numerator.toDouble() / denominator.toDouble()
            else -> 0.0
        }

    private fun nullableRate(numerator: Long, denominator: Long): Double? =
        when {
            denominator > 0 -> numerator.toDouble() / denominator.toDouble()
            else -> null
        }

    private fun smoothedRate(
        numerator: Long,
        denominator: Long,
        baselineRate: Double,
        priorDenominator: Double,
    ): Double =
        when {
            denominator <= 0 && priorDenominator <= 0.0 -> 0.0
            else -> (numerator.toDouble() + baselineRate * priorDenominator) /
                (denominator.toDouble() + priorDenominator)
        }

    private class MutablePerkAggregate {
        var applicableSlots: Long = 0
        var sumRawMatchCount: Long = 0
        var slotsWithPositiveRaw: Long = 0
        var sumAppliedOccurrences: Long = 0

        fun add(raw: Int, applied: Int) {
            applicableSlots++
            sumRawMatchCount += raw.toLong()
            if (raw > 0) slotsWithPositiveRaw++
            sumAppliedOccurrences += applied.toLong()
        }
    }

    private class MutablePlayerAggregate(
        val fantasyPlayer: FantasyPlayer,
        perks: List<Perk>,
    ) {
        var games: Long = 0
        val byPerk: Map<String, MutablePerkAggregate> =
            perks.associate { it.id to MutablePerkAggregate() }
    }

    private companion object {
        private const val MAX_FAILURES_IN_RESPONSE = 100
        private const val DEFAULT_MIN_PLAYER_GAMES = 20
        private const val DEFAULT_MIN_APPLICABLE_SLOTS = 5
        private const val DEFAULT_PRIOR_GAMES = 30.0
        private const val DEFAULT_MAX_ANOMALIES = 100
        private const val MAX_ANOMALIES_IN_RESPONSE = 500
    }
}
