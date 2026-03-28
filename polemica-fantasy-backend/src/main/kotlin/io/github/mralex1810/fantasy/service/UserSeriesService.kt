package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.dto.user.response.LeaderboardEntryDto
import io.github.mralex1810.fantasy.dto.user.response.SeriesGameEntryDto
import io.github.mralex1810.fantasy.dto.user.response.SeriesPlayerEntryDto
import io.github.mralex1810.fantasy.dto.user.response.UserPublicDto
import io.github.mralex1810.fantasy.dto.user.response.UserSeriesDetailDto
import io.github.mralex1810.fantasy.repository.FantasyTeamRepository
import io.github.mralex1810.fantasy.repository.SeriesGameRepository
import io.github.mralex1810.fantasy.repository.SeriesPlayerRepository
import io.github.mralex1810.fantasy.entity.SeriesGame
import io.github.mralex1810.fantasy.repository.SeriesRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class UserSeriesService(
    private val seriesRepository: SeriesRepository,
    private val seriesPlayerRepository: SeriesPlayerRepository,
    private val seriesGameRepository: SeriesGameRepository,
    private val fantasyTeamRepository: FantasyTeamRepository,
) {

    @Transactional(readOnly = true)
    fun getSeriesDetail(seriesId: Long): UserSeriesDetailDto {
        val s = seriesRepository.findById(seriesId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Series $seriesId not found")
        }
        val players = seriesPlayerRepository.findAllBySeries_IdWithTournamentPlayers(seriesId).map { sp ->
            val tp = sp.tournamentPlayer!!
            val fp = tp.fantasyPlayer!!
            SeriesPlayerEntryDto(
                tournamentPlayerId = tp.id!!,
                nickname = fp.nickname,
                photoUrl = fp.photoUrl,
            )
        }
        val games = seriesGameRepository.findAllBySeries_Id(seriesId).map { g ->
            SeriesGameEntryDto(
                polemicaGameId = g.polemicaGameId,
                gameName = formatGameDisplayName(g),
                scored = g.scored,
            )
        }
        return UserSeriesDetailDto(
            id = s.id!!,
            tournamentId = s.tournament!!.id!!,
            name = s.name,
            namePrefix = s.namePrefix,
            gameNumFrom = s.gameNumFrom,
            gameNumTo = s.gameNumTo,
            status = s.status,
            startsAt = s.startsAt,
            teamDeadline = s.teamDeadline,
            players = players,
            games = games,
        )
    }

    @Transactional(readOnly = true)
    fun getLeaderboard(seriesId: Long): List<LeaderboardEntryDto> {
        if (!seriesRepository.existsById(seriesId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Series $seriesId not found")
        }
        val teams = fantasyTeamRepository.findLeaderboardForSeries(seriesId)
        return teams.mapIndexed { index, ft ->
            val u = ft.telegramUser!!
            LeaderboardEntryDto(
                rank = index + 1,
                totalScore = ft.totalScore,
                user = UserPublicDto(
                    telegramId = u.telegramId,
                    username = u.username,
                    firstName = u.firstName,
                ),
            )
        }
    }

    /**
     * If sync stored an empty name as "(no name)", show "стол N, игра K" from cached Polemica JSON (`table`, `num`).
     */
    private fun formatGameDisplayName(g: SeriesGame): String {
        val stored = g.gameName.trim()
        if (stored.isNotEmpty() && stored != "(no name)") return stored
        val node = g.gameDataCache
        if (node != null && !node.isNull) {
            val numNode = node.path("num")
            val tableNode = node.path("table")
            val num = if (numNode.isMissingNode || numNode.isNull) null else numNode.asInt()
            val table = if (tableNode.isMissingNode || tableNode.isNull) null else tableNode.asInt()
            if (num != null || table != null) {
                val parts = mutableListOf<String>()
                if (table != null) parts.add("стол $table")
                if (num != null) parts.add("игра $num")
                return parts.joinToString(", ")
            }
        }
        return if (stored == "(no name)") "Игра #${g.polemicaGameId}" else stored.ifEmpty { "Игра #${g.polemicaGameId}" }
    }
}
