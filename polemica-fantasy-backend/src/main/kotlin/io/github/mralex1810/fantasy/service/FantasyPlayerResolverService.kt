package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.entity.FantasyPlayer
import io.github.mralex1810.fantasy.entity.FantasyPlayerAlias
import io.github.mralex1810.fantasy.repository.FantasyPlayerAliasRepository
import io.github.mralex1810.fantasy.repository.FantasyPlayerRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class FantasyPlayerResolverService(
    private val fantasyPlayerRepository: FantasyPlayerRepository,
    private val fantasyPlayerAliasRepository: FantasyPlayerAliasRepository,
) {
    fun resolveByPolemicaUserId(polemicaUserId: Long): FantasyPlayer? {
        if (polemicaUserId <= 0) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "polemicaUserId must be positive")
        }
        val aliasPlayer = fantasyPlayerAliasRepository.findByPolemicaUserIdWithFantasyPlayer(polemicaUserId)?.fantasyPlayer
        if (aliasPlayer != null) return aliasPlayer
        return fantasyPlayerRepository.findByPolemicaUserId(polemicaUserId)
    }

    fun requireByPolemicaUserId(polemicaUserId: Long): FantasyPlayer =
        resolveByPolemicaUserId(polemicaUserId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "FantasyPlayer alias $polemicaUserId not found")

    fun createWithPrimaryAlias(polemicaUserId: Long, nickname: String): FantasyPlayer {
        if (polemicaUserId <= 0) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "polemicaUserId must be positive")
        }
        if (resolveByPolemicaUserId(polemicaUserId) != null) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Player with this polemicaUserId already exists")
        }
        val player = fantasyPlayerRepository.save(
            FantasyPlayer(
                polemicaUserId = polemicaUserId,
                nickname = nickname.trim(),
            ),
        )
        fantasyPlayerAliasRepository.save(
            FantasyPlayerAlias(
                fantasyPlayer = player,
                polemicaUserId = polemicaUserId,
                primaryAlias = true,
            ),
        )
        return player
    }
}
