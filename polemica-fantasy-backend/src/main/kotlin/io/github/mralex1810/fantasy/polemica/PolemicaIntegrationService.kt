package io.github.mralex1810.fantasy.polemica

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.mafia.vyasma.polemica.library.client.PolemicaClient
import com.github.mafia.vyasma.polemica.library.client.PolemicaClient.ProfileGameRow
import com.github.mafia.vyasma.polemica.library.model.game.PolemicaGame
import io.github.mralex1810.fantasy.dto.admin.response.PolemicaCompetitionDetailDto
import io.github.mralex1810.fantasy.dto.admin.response.PolemicaCompetitionSummaryDto
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class PolemicaIntegrationService(
    private val polemicaClient: PolemicaClient,
    private val objectMapper: ObjectMapper,
) {

    /**
     * Up to [PROFILE_SYNC_PAGE_SIZE] most recent profile games (page 1 of the public profile API, single request).
     * Used for STANDALONE sync to avoid timeouts when a player has a very long history.
     */
    fun fetchRecentProfileRowsForSync(userId: Long): List<PolemicaClient.ProfileGameRow> {
        val chunk = polemicaClient.getProfileGames(userId, 1, PROFILE_SYNC_PAGE_SIZE)
        return chunk.rows
    }

    /** First page of public profile games (newest first). Used for achievement frequency statistics. */
    fun fetchProfileGamesFirstPageForStatistics(userId: Long): List<ProfileGameRow> {
        val chunk = polemicaClient.getProfileGames(userId, PROFILE_STATS_PAGE, PROFILE_STATS_PAGE_SIZE)
        return chunk.rows
    }

    private companion object {
        private const val PROFILE_SYNC_PAGE_SIZE = 500L
        private const val PROFILE_STATS_PAGE = 1L
        private const val PROFILE_STATS_PAGE_SIZE = 100L
    }

    fun loadMatch(matchId: Long): PolemicaGame =
        polemicaClient.getMatch(PolemicaClient.PolemicaMatchId(matchId))

    fun listCompetitionGameReferences(competitionId: Long): List<PolemicaClient.PolemicaTournamentGameReference> =
        polemicaClient.getGamesFromCompetition(competitionId)

    fun loadGameFromCompetition(competitionId: Long, gameId: Long, version: Long?): PolemicaGame =
        polemicaClient.getGameFromCompetition(
            PolemicaClient.PolemicaCompetitionGameId(competitionId, gameId, version),
        )

    fun listCompetitionsSummary(): List<PolemicaCompetitionSummaryDto> =
        polemicaClient.getCompetitions().map { c ->
            PolemicaCompetitionSummaryDto(
                id = c.id,
                name = c.name,
                startDate = c.startDate,
                endDate = c.endDate,
            )
        }

    fun getCompetitionDetail(id: Long): PolemicaCompetitionDetailDto {
        val c = polemicaClient.getCompetition(id)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Polemica competition $id not found")
        return PolemicaCompetitionDetailDto(
            id = c.id,
            name = c.name,
            startDate = c.startDate,
            endDate = c.endDate,
            region = c.region,
            city = c.city,
            description = c.description,
            link = c.link,
            memberCount = c.memberCount,
            rating = c.rating,
            hasScores = c.hasScores,
        )
    }

    fun toJsonNode(game: PolemicaGame): JsonNode = objectMapper.valueToTree(game)
}
