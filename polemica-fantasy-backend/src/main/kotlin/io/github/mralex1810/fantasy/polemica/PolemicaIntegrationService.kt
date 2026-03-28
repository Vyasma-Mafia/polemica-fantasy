package io.github.mralex1810.fantasy.polemica

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.mafia.vyasma.polemica.library.client.PolemicaClient
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
     * All profile game rows for a Polemica user (paginated public API).
     */
    fun fetchAllProfileRows(userId: Long): List<PolemicaClient.ProfileGameRow> {
        val out = mutableListOf<PolemicaClient.ProfileGameRow>()
        var page = 1L
        val pageSize = 100L
        while (true) {
            val chunk = polemicaClient.getProfileGames(userId, page, pageSize)
            out.addAll(chunk.rows)
            if (chunk.rows.isEmpty() || out.size >= chunk.totalCount) break
            page++
        }
        return out
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
