package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.repository.FantasyTeamRepository
import io.github.mralex1810.fantasy.repository.SeriesLeagueRepository
import io.github.mralex1810.fantasy.repository.SeriesRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.Instant

data class SeriesEconomySnapshot(val checksum: String?, val reason: String? = null) {
    val ready: Boolean get() = checksum != null
}

/** Fingerprints the exact leaderboard, reward and card-use inputs consumed by finalization. */
@Service
class SeriesEconomyFingerprintService(
    private val seriesRepository: SeriesRepository,
    private val fantasyTeamRepository: FantasyTeamRepository,
    private val seriesLeagueRepository: SeriesLeagueRepository,
    private val economyConfigService: EconomyConfigService,
    private val leagueService: LeagueService,
) {
    @Transactional(readOnly = true)
    fun snapshot(seriesId: Long): SeriesEconomySnapshot {
        val series = seriesRepository.findById(seriesId).orElse(null)
            ?: return SeriesEconomySnapshot(null, "series does not exist")
        if (Instant.now().isBefore(series.teamDeadline)) {
            return SeriesEconomySnapshot(null, "team deadline has not passed")
        }
        val teams = fantasyTeamRepository.findAllWithCardsForScoring(seriesId)
        val incompleteTeam = teams.firstOrNull { team ->
            team.totalScore == null || team.cards.any { it.score == null }
        }
        if (incompleteTeam != null) return SeriesEconomySnapshot(null, "fantasy team scoring is incomplete")

        val cardLeagueIds = linkedMapOf<Long, MutableSet<Long>>()
        val cardUses = linkedMapOf<Long, Int>()
        teams.forEach { team ->
            val leagueId = team.seriesLeague?.id
                ?: return SeriesEconomySnapshot(null, "fantasy team has no series league")
            team.cards.forEach { card ->
                val userCard = card.userCard ?: return SeriesEconomySnapshot(null, "fantasy team card is invalid")
                val cardId = userCard.id ?: return SeriesEconomySnapshot(null, "user card has no id")
                cardLeagueIds.computeIfAbsent(cardId) { linkedSetOf() }.add(leagueId)
                cardUses[cardId] = userCard.usesRemaining
            }
        }
        val overcommitted = cardLeagueIds.any { (cardId, leagues) -> (cardUses[cardId] ?: 0) < leagues.size }
        if (overcommitted) return SeriesEconomySnapshot(null, "card uses are overcommitted")

        val material = mutableListOf(
            "series:$seriesId",
            "deadline:${series.teamDeadline}",
        )
        val leagueIds = teams.mapNotNull { it.seriesLeague?.id }.distinct().sorted()
        leagueIds.forEach { leagueId ->
            val seriesLeague = seriesLeagueRepository.findById(leagueId).orElse(null)
                ?: return SeriesEconomySnapshot(null, "series league $leagueId does not exist")
            val leaderboard = fantasyTeamRepository.findLeaderboardForSeriesLeague(leagueId)
            val scale = leagueService.getEffectiveRewardScale(seriesLeague)
            material += "league:$leagueId:${seriesLeague.league?.id}:$scale:${leaderboard.size}"
            leaderboard.forEachIndexed { index, team ->
                val position = index + 1
                val baseReward = economyConfigService.getSeriesReward(position, leaderboard.size)
                val cardCount = team.cards.size.coerceIn(0, 3)
                val rosterScaled = when (cardCount) {
                    3 -> baseReward
                    2 -> (2L * baseReward + 2L) / 3L
                    1 -> (baseReward + 2L) / 3L
                    else -> 0L
                }
                val reward = rosterScaled * scale / 100L
                material += "team:${team.id}:${team.telegramUser?.id}:${team.totalScore}:$position:$reward"
                team.cards.sortedBy { it.slot }.forEach { card ->
                    val uc = card.userCard!!
                    material += "card:${card.id}:${card.slot}:${card.score}:${uc.id}:${uc.usesRemaining}:${uc.cardTemplate?.id}"
                }
            }
        }
        cardLeagueIds.toSortedMap().forEach { (cardId, leagues) ->
            material += "uses:$cardId:${cardUses[cardId]}:${leagues.sorted().joinToString(",")}"
        }
        return SeriesEconomySnapshot(sha256(material.joinToString("|")))
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
