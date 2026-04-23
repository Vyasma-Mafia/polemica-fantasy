package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.dto.user.response.UserCardItemDto
import io.github.mralex1810.fantasy.entity.Rarity
import io.github.mralex1810.fantasy.entity.TelegramUser
import io.github.mralex1810.fantasy.repository.SeriesRepository
import io.github.mralex1810.fantasy.repository.UserCardRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class UserCardCollectionService(
    private val userCardRepository: UserCardRepository,
    private val seriesRepository: SeriesRepository,
    private val imageStorageService: ImageStorageService,
) {

    @Transactional(readOnly = true)
    fun listCards(
        user: TelegramUser,
        tournamentId: Long?,
        seriesId: Long?,
        rarity: Rarity?,
    ): List<UserCardItemDto> {
        if (seriesId != null && !seriesRepository.existsById(seriesId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Series $seriesId not found")
        }
        val rows = userCardRepository.findAllForUserFiltered(
            telegramUserId = user.id!!,
            tournamentId = tournamentId,
            seriesId = seriesId,
            rarity = rarity,
        )
        return rows.map { it.toUserCardItemDto(imageStorage = imageStorageService) }
    }
}
