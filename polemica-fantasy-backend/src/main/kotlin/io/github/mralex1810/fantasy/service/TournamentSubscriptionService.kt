package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.dto.user.request.UpdateTournamentSubscriptionsRequest
import io.github.mralex1810.fantasy.dto.user.response.TournamentSubscriptionEntry
import io.github.mralex1810.fantasy.dto.user.response.TournamentSubscriptionsResponse
import io.github.mralex1810.fantasy.entity.TournamentStatus
import io.github.mralex1810.fantasy.entity.TournamentSubscription
import io.github.mralex1810.fantasy.repository.TelegramUserRepository
import io.github.mralex1810.fantasy.repository.TournamentRepository
import io.github.mralex1810.fantasy.repository.TournamentSubscriptionRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class TournamentSubscriptionService(
    private val tournamentSubscriptionRepository: TournamentSubscriptionRepository,
    private val tournamentRepository: TournamentRepository,
    private val telegramUserRepository: TelegramUserRepository,
) {
    @Transactional(readOnly = true)
    fun getSubscriptions(internalUserId: Long): TournamentSubscriptionsResponse {
        val subscribedTournamentIds = tournamentSubscriptionRepository
            .findTournamentIdsByTelegramUser_Id(internalUserId)
            .toSet()
        val available = tournamentRepository.findAllByStatusOrderByIdAsc(TournamentStatus.ACTIVE)
            .map { tournament ->
                TournamentSubscriptionEntry(
                    tournamentId = tournament.id!!,
                    tournamentName = tournament.name,
                    subscribed = tournament.id in subscribedTournamentIds,
                )
            }
        return TournamentSubscriptionsResponse(
            subscriptions = available.filter { it.subscribed },
            availableTournaments = available,
        )
    }

    @Transactional
    fun updateSubscriptions(
        internalUserId: Long,
        request: UpdateTournamentSubscriptionsRequest,
    ): TournamentSubscriptionsResponse {
        val requestedTournamentIds = request.tournamentIds.distinct()
        val tournamentsById = if (requestedTournamentIds.isEmpty()) {
            emptyMap()
        } else {
            tournamentRepository.findAllById(requestedTournamentIds).associateBy { it.id!! }
        }
        val missingTournamentIds = requestedTournamentIds.filterNot { tournamentsById.containsKey(it) }
        if (missingTournamentIds.isNotEmpty()) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Tournament(s) not found: ${missingTournamentIds.joinToString(", ")}",
            )
        }
        val inactiveTournament = tournamentsById.values.firstOrNull { it.status != TournamentStatus.ACTIVE }
        if (inactiveTournament != null) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Tournament ${inactiveTournament.id} is not ACTIVE",
            )
        }

        tournamentSubscriptionRepository.deleteAllByTelegramUser_Id(internalUserId)
        if (requestedTournamentIds.isNotEmpty()) {
            val user = telegramUserRepository.findById(internalUserId).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "User $internalUserId not found")
            }
            for (tournamentId in requestedTournamentIds) {
                tournamentSubscriptionRepository.save(
                    TournamentSubscription(
                        telegramUser = user,
                        tournament = tournamentsById.getValue(tournamentId),
                    ),
                )
            }
        }
        return getSubscriptions(internalUserId)
    }
}
