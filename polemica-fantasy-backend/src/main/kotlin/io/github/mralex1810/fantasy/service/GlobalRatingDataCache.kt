package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.dto.user.response.RatingEntryDto
import io.github.mralex1810.fantasy.dto.user.response.UserPublicDto
import io.github.mralex1810.fantasy.entity.TelegramUser
import io.github.mralex1810.fantasy.config.AppRatingProperties
import io.github.mralex1810.fantasy.repository.TelegramUserRepository
import io.github.mralex1810.fantasy.repository.UserCardRepository
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class GlobalRatingEntry(
    val userId: Long,
    val rank: Int,
    val telegramId: Long,
    val username: String?,
    val firstName: String?,
    val displayName: String?,
    val fantikiBalance: Long,
    val cardsValue: Long,
    val totalValue: Long,
    val cardsCount: Int,
) {
    fun toDto(): RatingEntryDto = RatingEntryDto(
        rank = rank,
        user = UserPublicDto(telegramId, username, firstName, displayName),
        fantikiBalance = fantikiBalance,
        cardsValue = cardsValue,
        totalValue = totalValue,
        cardsCount = cardsCount,
    )
}

/**
 * Full leaderboard snapshot; cached (see [io.github.mralex1810.fantasy.FantasyApplication] and application.yml).
 */
@Service
class GlobalRatingDataCache(
    private val telegramUserRepository: TelegramUserRepository,
    private val userCardRepository: UserCardRepository,
    private val cardValueService: CardValueService,
    private val appRatingProperties: AppRatingProperties,
) {
    @Cacheable(value = ["globalRating"], key = "'all'")
    @Transactional(readOnly = true)
    fun loadSnapshot(): List<GlobalRatingEntry> {
        val excluded = appRatingProperties.excludedTelegramIds
        val users = telegramUserRepository.findAll()
            .filter { it.telegramId !in excluded }
        val cards = userCardRepository.findAllForGlobalRating()
        val byUserId = cards.groupBy { it.telegramUser!!.id!! }
        data class Scored(
            val user: TelegramUser,
            val cardsValue: Long,
            val cardsCount: Int,
            val total: Long,
        )
        val rows = users.map { u ->
            val uCards = byUserId[u.id] ?: emptyList()
            val cardsValue = uCards.sumOf { cardValueService.calculateValue(it) }
            val cardsCount = uCards.size
            val total = u.fantiki + cardsValue
            Scored(u, cardsValue, cardsCount, total)
        }
        val sorted = rows.sortedWith(
            compareByDescending<Scored> { it.total }
                .thenBy { it.user.id!! },
        )
        return sorted.mapIndexed { index, s ->
            val u = s.user
            GlobalRatingEntry(
                userId = u.id!!,
                rank = index + 1,
                telegramId = u.telegramId,
                username = u.username,
                firstName = u.firstName,
                displayName = u.displayName,
                fantikiBalance = u.fantiki,
                cardsValue = s.cardsValue,
                totalValue = s.total,
                cardsCount = s.cardsCount,
            )
        }
    }
}
