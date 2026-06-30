package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.dto.StreamLinkDto
import io.github.mralex1810.fantasy.dto.StreamLinkRequest
import io.github.mralex1810.fantasy.entity.Series
import io.github.mralex1810.fantasy.entity.SeriesStreamLink
import io.github.mralex1810.fantasy.entity.Tournament
import io.github.mralex1810.fantasy.entity.TournamentStreamLink
import io.github.mralex1810.fantasy.repository.SeriesStreamLinkRepository
import io.github.mralex1810.fantasy.repository.TournamentStreamLinkRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.net.URI

@Service
class StreamLinkService(
    private val tournamentStreamLinkRepository: TournamentStreamLinkRepository,
    private val seriesStreamLinkRepository: SeriesStreamLinkRepository,
) {
    fun replaceTournamentLinks(tournament: Tournament, requests: List<StreamLinkRequest>) {
        val tournamentId = tournament.id ?: return
        tournamentStreamLinkRepository.deleteAllByTournament_Id(tournamentId)
        val links = normalize(requests).mapIndexed { index, link ->
            TournamentStreamLink(
                tournament = tournament,
                label = link.label,
                url = link.url,
                displayOrder = index,
            )
        }
        tournamentStreamLinkRepository.saveAll(links)
    }

    fun replaceSeriesLinks(series: Series, requests: List<StreamLinkRequest>) {
        val seriesId = series.id ?: return
        seriesStreamLinkRepository.deleteAllBySeries_Id(seriesId)
        val links = normalize(requests).mapIndexed { index, link ->
            SeriesStreamLink(
                series = series,
                label = link.label,
                url = link.url,
                displayOrder = index,
            )
        }
        seriesStreamLinkRepository.saveAll(links)
    }

    fun linksForTournament(tournamentId: Long): List<StreamLinkDto> =
        tournamentStreamLinkRepository.findAllByTournament_IdOrderByDisplayOrderAscIdAsc(tournamentId)
            .map { it.toDto() }

    fun linksForSeries(seriesId: Long): List<StreamLinkDto> =
        seriesStreamLinkRepository.findAllBySeries_IdOrderByDisplayOrderAscIdAsc(seriesId)
            .map { it.toDto() }

    fun linksByTournamentIds(tournamentIds: Collection<Long>): Map<Long, List<StreamLinkDto>> {
        if (tournamentIds.isEmpty()) return emptyMap()
        return tournamentStreamLinkRepository
            .findAllByTournament_IdInOrderByTournament_IdAscDisplayOrderAscIdAsc(tournamentIds)
            .groupBy { it.tournament!!.id!! }
            .mapValues { (_, links) -> links.map { it.toDto() } }
    }

    fun linksBySeriesIds(seriesIds: Collection<Long>): Map<Long, List<StreamLinkDto>> {
        if (seriesIds.isEmpty()) return emptyMap()
        return seriesStreamLinkRepository
            .findAllBySeries_IdInOrderBySeries_IdAscDisplayOrderAscIdAsc(seriesIds)
            .groupBy { it.series!!.id!! }
            .mapValues { (_, links) -> links.map { it.toDto() } }
    }

    fun effectiveLinksBySeries(series: Collection<Series>): Map<Long, List<StreamLinkDto>> {
        if (series.isEmpty()) return emptyMap()
        val tournamentLinksById = linksByTournamentIds(series.map { it.tournament!!.id!! }.toSet())
        val seriesLinksById = linksBySeriesIds(series.map { it.id!! })
        return series.associate { s ->
            val sid = s.id!!
            sid to ((tournamentLinksById[s.tournament!!.id!!] ?: emptyList()) + (seriesLinksById[sid] ?: emptyList()))
        }
    }

    fun effectiveLinksForSeries(series: Series): List<StreamLinkDto> {
        val tournamentId = series.tournament!!.id!!
        val seriesId = series.id!!
        return linksForTournament(tournamentId) + linksForSeries(seriesId)
    }

    private fun normalize(requests: List<StreamLinkRequest>): List<StreamLinkDto> =
        requests.map { request ->
            val url = request.url.trim()
            if (url.isEmpty()) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "stream link url cannot be blank")
            }
            val uri = runCatching { URI(url) }.getOrElse {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "stream link url is invalid")
            }
            if (uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank()) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "stream link url must be http(s)")
            }
            StreamLinkDto(
                label = request.label?.trim()?.takeIf { it.isNotEmpty() },
                url = url,
            )
        }

    private fun TournamentStreamLink.toDto() = StreamLinkDto(label = label, url = url)

    private fun SeriesStreamLink.toDto() = StreamLinkDto(label = label, url = url)
}
