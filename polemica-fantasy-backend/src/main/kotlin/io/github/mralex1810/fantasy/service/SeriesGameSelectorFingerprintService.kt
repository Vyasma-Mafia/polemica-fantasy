package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.repository.FantasyPlayerAliasRepository
import io.github.mralex1810.fantasy.repository.SeriesPlayerRepository
import io.github.mralex1810.fantasy.repository.SeriesRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest

/** Fingerprints every persisted selector input that determines which Polemica games belong to a series. */
@Service
class SeriesGameSelectorFingerprintService(
    private val seriesRepository: SeriesRepository,
    private val seriesPlayerRepository: SeriesPlayerRepository,
    private val fantasyPlayerAliasRepository: FantasyPlayerAliasRepository,
) {
    @Transactional(readOnly = true)
    fun fingerprint(seriesId: Long): String {
        val series = requireNotNull(seriesRepository.findByIdWithTournament(seriesId)) { "Series $seriesId not found" }
        val tournament = requireNotNull(series.tournament) { "Series $seriesId has no tournament" }
        val roster = seriesPlayerRepository.findAllBySeries_IdWithTournamentPlayers(seriesId).map { row ->
            val fantasyPlayer = requireNotNull(row.tournamentPlayer?.fantasyPlayer) { "Series player has no fantasy player" }
            val aliases = fantasyPlayerAliasRepository.findPolemicaUserIdsByFantasyPlayerId(fantasyPlayer.id!!)
                .ifEmpty { listOf(fantasyPlayer.polemicaUserId) }
                .distinct().sorted().joinToString(",")
            "${row.tournamentPlayer!!.id}:${fantasyPlayer.id}:$aliases"
        }.sorted()
        return sha256(
            buildList {
                add("series=$seriesId")
                add("tournament=${tournament.id}:${tournament.kind}:${tournament.polemicaCompetitionId ?: "-"}")
                add("prefix=${series.namePrefix.orEmpty()}")
                add("startedOn=${series.gameStartedOn ?: "-"}")
                add("range=${series.gameNumFrom ?: "-"}:${series.gameNumTo ?: "-"}")
                add("phase=${series.gamePhase ?: "-"}")
                addAll(roster.map { "roster=$it" })
            }.joinToString("|"),
        )
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
