package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.dto.user.request.CreateMarketplaceWatchRequest
import io.github.mralex1810.fantasy.dto.user.response.FantasyPlayerBriefDto
import io.github.mralex1810.fantasy.dto.user.response.MarketplaceWatchPerkDto
import io.github.mralex1810.fantasy.dto.user.response.MarketplaceWatchDto
import io.github.mralex1810.fantasy.dto.user.response.MarketplaceWatchesResponse
import io.github.mralex1810.fantasy.dto.user.response.TournamentBriefDto
import io.github.mralex1810.fantasy.entity.MarketplaceWatchFilter
import io.github.mralex1810.fantasy.event.AchievementProgressEvent
import io.github.mralex1810.fantasy.event.AchievementProgressEventType
import io.github.mralex1810.fantasy.repository.PerkRepository
import io.github.mralex1810.fantasy.repository.FantasyPlayerRepository
import io.github.mralex1810.fantasy.repository.MarketplaceWatchFilterRepository
import io.github.mralex1810.fantasy.repository.TelegramUserRepository
import io.github.mralex1810.fantasy.repository.TournamentRepository
import org.springframework.context.ApplicationEventPublisher
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
    private val perkRepository: PerkRepository,
    private val applicationEventPublisher: ApplicationEventPublisher,
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
        val normalizedPerkIds = normalizePerkIds(request.perkIds)
        if (
            request.fantasyPlayerId == null &&
            request.tournamentId == null &&
            request.rarity == null &&
            request.minTimesRenewed == null &&
            request.maxTimesRenewed == null &&
            normalizedPerkIds.isEmpty()
        ) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "At least one filter criterion required",
            )
        }
        if (request.maxPrice != null && request.maxPrice <= 0) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "maxPrice must be positive")
        }
        if (request.minTimesRenewed != null && request.minTimesRenewed < 0) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "minTimesRenewed must be non-negative")
        }
        if (request.maxTimesRenewed != null && request.maxTimesRenewed < 0) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "maxTimesRenewed must be non-negative")
        }
        if (
            request.minTimesRenewed != null &&
            request.maxTimesRenewed != null &&
            request.minTimesRenewed > request.maxTimesRenewed
        ) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "minTimesRenewed must be <= maxTimesRenewed")
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
        val perks = if (normalizedPerkIds.isEmpty()) {
            emptyList()
        } else {
            val found = perkRepository.findAllByIdIn(normalizedPerkIds)
            val foundIds = found.map { it.id }.toSet()
            val missing = normalizedPerkIds.firstOrNull { it !in foundIds }
            if (missing != null) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Perk $missing not found")
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
                    minTimesRenewed = request.minTimesRenewed,
                    maxTimesRenewed = request.maxTimesRenewed,
                    perkIdsKey = normalizedPerkIds.joinToString(","),
                    perks = perks.toMutableSet(),
                ),
            )
        } catch (_: DataIntegrityViolationException) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Watch filter already exists")
        }
        applicationEventPublisher.publishEvent(
            AchievementProgressEvent(AchievementProgressEventType.MARKETPLACE_WATCH_CREATED, setOf(internalUserId)),
        )
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
            minTimesRenewed = minTimesRenewed,
            maxTimesRenewed = maxTimesRenewed,
            perks = perks.sortedBy { it.id }.map { perk ->
                MarketplaceWatchPerkDto(
                    id = perk.id,
                    name = perk.name,
                )
            },
            createdAt = createdAt,
        )

    private fun normalizePerkIds(ids: Collection<String>?): List<String> =
        ids.orEmpty()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
}
