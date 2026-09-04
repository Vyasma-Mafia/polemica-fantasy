package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.entity.FantasyPlayer
import io.github.mralex1810.fantasy.repository.FantasyPlayerAliasRepository
import io.github.mralex1810.fantasy.repository.FantasyPlayerImportBlockRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class FantasyPlayerImportPolicyService(
    private val importBlockRepository: FantasyPlayerImportBlockRepository,
    private val fantasyPlayerAliasRepository: FantasyPlayerAliasRepository,
) {
    fun requireImportAllowed(polemicaUserId: Long) {
        rejectIfBlocked(listOf(polemicaUserId))
    }

    fun requireImportAllowed(player: FantasyPlayer) {
        val playerId = requireNotNull(player.id) { "Fantasy player must be persisted before import policy validation" }
        val polemicaUserIds = buildSet {
            add(player.polemicaUserId)
            addAll(fantasyPlayerAliasRepository.findPolemicaUserIdsByFantasyPlayerId(playerId))
        }
        rejectIfBlocked(polemicaUserIds)
    }

    private fun rejectIfBlocked(polemicaUserIds: Collection<Long>) {
        val blocked = importBlockRepository.findFirstByPolemicaUserIdIn(polemicaUserIds) ?: return
        throw ResponseStatusException(
            HttpStatus.CONFLICT,
            "Polemica user ${blocked.polemicaUserId} is blocked from Fantasy imports",
        )
    }
}
