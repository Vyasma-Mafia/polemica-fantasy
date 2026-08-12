package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.entity.FantasyTeam
import io.github.mralex1810.fantasy.entity.FantasyTeamCard
import io.github.mralex1810.fantasy.repository.FantasyTeamCardRepository
import io.github.mralex1810.fantasy.repository.FantasyTeamRepository
import io.github.mralex1810.fantasy.repository.SeriesPlayerRepository
import io.github.mralex1810.fantasy.repository.SeriesRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class FantasyTeamRosterPruningService(
    private val seriesRepository: SeriesRepository,
    private val seriesPlayerRepository: SeriesPlayerRepository,
    private val fantasyTeamRepository: FantasyTeamRepository,
    private val fantasyTeamCardRepository: FantasyTeamCardRepository,
) {

    /**
     * Removes fantasy team slots whose card's fantasy player is no longer in [series_player],
     * renumbers remaining slots to 1..n, deletes [FantasyTeam] if no cards left.
     * No-op if the series team deadline has passed (locked teams).
     *
     * @return each removed card per user (same chat may appear multiple times if several slots pruned).
     */
    @Transactional
    fun pruneInvalidCardsForSeries(seriesId: Long): FantasyTeamRosterPruneResult {
        val series = seriesRepository.findByIdForUpdate(seriesId)
            ?: return FantasyTeamRosterPruneResult(emptyList())
        if (series.finalized || Instant.now().isAfter(series.teamDeadline)) {
            return FantasyTeamRosterPruneResult(emptyList())
        }

        val pruned = mutableListOf<FantasyRosterPrunedCard>()
        val teams = fantasyTeamRepository.findAllBySeries_IdWithCards(seriesId)
        for (team in teams) {
            pruneTeam(team, seriesId, pruned)
        }
        return FantasyTeamRosterPruneResult(pruned)
    }

    private fun pruneTeam(team: FantasyTeam, seriesId: Long, pruned: MutableList<FantasyRosterPrunedCard>) {
        val cards = team.cards.sortedBy { it.slot }
        val toRemove = cards.filter { ftc ->
            val fpId = ftc.userCard!!.cardTemplate!!.fantasyPlayer!!.id!!
            !seriesPlayerRepository.existsBySeries_IdAndTournamentPlayer_FantasyPlayer_Id(seriesId, fpId)
        }
        if (toRemove.isEmpty()) return

        val chatId = team.telegramUser!!.telegramId
        for (ftc in toRemove) {
            val fp = ftc.userCard!!.cardTemplate!!.fantasyPlayer!!
            pruned.add(
                FantasyRosterPrunedCard(
                    telegramChatId = chatId,
                    fantasyPlayerId = fp.id!!,
                    playerNickname = fp.nickname,
                ),
            )
            fantasyTeamCardRepository.delete(ftc)
            team.cards.remove(ftc)
        }
        fantasyTeamCardRepository.flush()

        // Flush each slot change separately: UNIQUE (fantasy_team_id, slot) is checked per UPDATE;
        // a single Hibernate flush can reorder SQL so a row moves into slot N before the row at N moves away.
        val remaining = team.cards.sortedBy { it.slot }.toMutableList()
        remaining.forEachIndexed { index, ftc ->
            val newSlot = index + 1
            if (ftc.slot != newSlot) {
                ftc.slot = newSlot
                fantasyTeamCardRepository.saveAndFlush(ftc)
            }
        }

        if (team.cards.isEmpty()) {
            fantasyTeamRepository.delete(team)
        }
    }
}
