package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.repository.FantasyTeamRepository
import io.github.mralex1810.fantasy.repository.SeriesPlayerRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest

/** Fingerprints every roster/team identity that can change which player/card is scored. */
@Service
class SeriesScoringContextFingerprintService(
    private val seriesPlayerRepository: SeriesPlayerRepository,
    private val fantasyTeamRepository: FantasyTeamRepository,
) {
    @Transactional(readOnly = true)
    fun fingerprint(seriesId: Long): String {
        val roster = seriesPlayerRepository.findAllBySeries_IdWithTournamentPlayers(seriesId)
            .map { row ->
                val tp = row.tournamentPlayer!!
                "R:${tp.id}:${tp.fantasyPlayer?.id}:${row.replacementPolemicaUserId ?: "-"}"
            }
            .sorted()
        val teams = fantasyTeamRepository.findAllWithCardsForScoring(seriesId)
            .flatMap { team ->
                team.cards.map { card ->
                    val userCard = card.userCard!!
                    val template = userCard.cardTemplate!!
                    val perks = template.perks.map { configured ->
                        val perk = configured.perk!!
                        val roles = perk.applicableRoles.map { it.role }.sorted().joinToString(",")
                        "${configured.id}:${perk.id}:${configured.bonusPoints ?: "-"}:${perk.bonusPoints}:" +
                            "${perk.occurrenceType}:$roles"
                    }.sorted().joinToString(";")
                    "T:${team.id}:${team.telegramUser?.id}:${team.seriesLeague?.id}:${card.slot}:${card.id}:${userCard.id}:" +
                        "${template.id}:${template.fantasyPlayer?.id}:${template.rarity}:$perks"
                }
            }
            .sorted()
        return sha256((roster + teams).joinToString("|"))
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
