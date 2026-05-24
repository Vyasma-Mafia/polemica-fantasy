package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.dto.user.request.CreateMarketplaceWatchRequest
import io.github.mralex1810.fantasy.dto.user.response.FantasyPlayerBriefDto
import io.github.mralex1810.fantasy.dto.user.response.MarketplaceWatchAchievementDto
import io.github.mralex1810.fantasy.dto.user.response.MarketplaceWatchDto
import io.github.mralex1810.fantasy.dto.user.response.MarketplaceWatchesResponse
import io.github.mralex1810.fantasy.dto.user.response.TournamentBriefDto
import io.github.mralex1810.fantasy.entity.MarketplaceWatchFilter
import io.github.mralex1810.fantasy.repository.AchievementRepository
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
    private val achievementRepository: AchievementRepository,
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
        val normalizedAchievementIds = normalizeAchievementIds(request.achievementIds)
        if (
            request.fantasyPlayerId == null &&
            request.tournamentId == null &&
            request.rarity == null &&
            normalizedAchievementIds.isEmpty()
        ) {
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
        val achievements = if (normalizedAchievementIds.isEmpty()) {
            emptyList()
        } else {
            val found = achievementRepository.findAllByIdIn(normalizedAchievementIds)
            val foundIds = found.map { it.id }.toSet()
            val missing = normalizedAchievementIds.firstOrNull { it !in foundIds }
            if (missing != null) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Achievement $missing not found")
            }
            found.sortedBy { it.id }
        }

        val saved = try {
            marketplaceWatchFilterRepository.save(
                MarketplaceWatchFilter(
                    telegramUser = user,
                    fantasyPlayer = player,
                    tournament = tournament,
                    rarity = request.rarity,
                    maxPrice = request.maxPrice,
                    achievementIdsKey = normalizedAchievementIds.joinToString(","),
                    achievements = achievements.toMutableSet(),
                ),
            )
        } catch (_: DataIntegrityViolationException) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Watch filter already exists")
        }
        return saved.toDto()
    }

    @Transactional
    fun deleteWatch(internalUserId: Long, watchId: Long) {
        val filter = marketplaceWatchFilterRepository.findByIdAndTelegramUser_Id(watchId, internalUserId)
        if (filter == null) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Watch filter not found")
        }
        marketplaceWatchFilterRepository.delete(filter)
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
            achievements = achievements.sortedBy { it.id }.map { achievement ->
                MarketplaceWatchAchievementDto(
                    id = achievement.id,
                    name = achievement.name,
                )
            },
            createdAt = createdAt,
        )

    private fun normalizeAchievementIds(ids: Collection<String>?): List<String> =
        ids.orEmpty()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
}
