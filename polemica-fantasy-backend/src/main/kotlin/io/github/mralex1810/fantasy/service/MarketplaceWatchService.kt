package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.dto.user.request.CreateMarketplaceWatchRequest
import io.github.mralex1810.fantasy.dto.user.response.FantasyPlayerBriefDto
import io.github.mralex1810.fantasy.dto.user.response.MarketplaceWatchDto
import io.github.mralex1810.fantasy.dto.user.response.MarketplaceWatchesResponse
import io.github.mralex1810.fantasy.dto.user.response.TournamentBriefDto
import io.github.mralex1810.fantasy.entity.MarketplaceWatchFilter
import io.github.mralex1810.fantasy.repository.FantasyPlayerRepository
import io.github.mralex1810.fantasy.repository.MarketplaceWatchFilterRepository
import io.github.mralex1810.fantasy.repository.TelegramUserRepository
import io.github.mralex1810.fantasy.repository.TournamentRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class MarketplaceWatchService(
    private val marketplaceWatchFilterRepository: MarketplaceWatchFilterRepository,
    private val telegramUserRepository: TelegramUserRepository,
    private val fantasyPlayerRepository: FantasyPlayerRepository,
    private val tournamentRepository: TournamentRepository,
) {
    companion object {
        const val MAX_WATCHES_PER_USER = 10
    }

    @Transactional(readOnly = true)
    fun getWatches(internalUserId: Long): MarketplaceWatchesResponse {
        val filters = marketplaceWatchFilterRepository.findAllByTelegramUser_IdOrderByCreatedAtDesc(internalUserId)
        return MarketplaceWatchesResponse(
            watches = filters.map { it.toDto() },
            maxWatches = MAX_WATCHES_PER_USER,
        )
    }

    @Transactional
    fun createWatch(
        internalUserId: Long,
        request: CreateMarketplaceWatchRequest,
    ): MarketplaceWatchDto {
        val count = marketplaceWatchFilterRepository.countByTelegramUser_Id(internalUserId)
        if (count >= MAX_WATCHES_PER_USER) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Maximum $MAX_WATCHES_PER_USER watches reached",
            )
        }
        if (request.fantasyPlayerId == null && request.tournamentId == null && request.rarity == null) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "At least one filter criterion required",
            )
        }
        if (request.maxPrice != null && request.maxPrice <= 0) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "maxPrice must be positive")
        }

        val user = telegramUserRepository.findById(internalUserId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "User $internalUserId not found")
        }
        val player = request.fantasyPlayerId?.let { fantasyPlayerId ->
            fantasyPlayerRepository.findById(fantasyPlayerId).orElseThrow {
                ResponseStatusException(HttpStatus.BAD_REQUEST, "Fantasy player $fantasyPlayerId not found")
            }
        }
        val tournament = request.tournamentId?.let { tournamentId ->
            tournamentRepository.findById(tournamentId).orElseThrow {
                ResponseStatusException(HttpStatus.BAD_REQUEST, "Tournament $tournamentId not found")
            }
        }

        val saved = try {
            marketplaceWatchFilterRepository.save(
                MarketplaceWatchFilter(
                    telegramUser = user,
                    fantasyPlayer = player,
                    tournament = tournament,
                    rarity = request.rarity,
                    maxPrice = request.maxPrice,
                ),
            )
        } catch (_: DataIntegrityViolationException) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Watch filter already exists")
        }
        return saved.toDto()
    }

    @Transactional
    fun deleteWatch(internalUserId: Long, watchId: Long) {
        val deleted = marketplaceWatchFilterRepository.deleteByIdAndTelegramUser_Id(watchId, internalUserId)
        if (deleted == 0) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Watch filter not found")
        }
    }

    private fun MarketplaceWatchFilter.toDto(): MarketplaceWatchDto =
        MarketplaceWatchDto(
            id = id!!,
            fantasyPlayer = fantasyPlayer?.let { fp ->
                FantasyPlayerBriefDto(
                    id = fp.id!!,
                    nickname = fp.nickname,
                    photoUrl = fp.photoUrl,
                )
            },
            tournament = tournament?.let { t ->
                TournamentBriefDto(
                    id = t.id!!,
                    name = t.name,
                )
            },
            rarity = rarity?.name,
            maxPrice = maxPrice,
            createdAt = createdAt,
        )
}
