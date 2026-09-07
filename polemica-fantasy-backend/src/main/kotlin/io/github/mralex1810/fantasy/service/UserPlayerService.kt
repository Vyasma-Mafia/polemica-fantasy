package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.dto.user.response.UserPlayerDto
import io.github.mralex1810.fantasy.repository.FantasyPlayerRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class UserPlayerService(private val fantasyPlayerRepository: FantasyPlayerRepository) {
    @Transactional(readOnly = true)
    fun getPlayer(fantasyPlayerId: Long): UserPlayerDto {
        if (fantasyPlayerId <= 0) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "fantasyPlayerId must be positive")
        }
        val player = fantasyPlayerRepository.findById(fantasyPlayerId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Player not found")
        }
        return UserPlayerDto(player.id!!, player.polemicaUserId, player.nickname, player.photoUrl)
    }
}
