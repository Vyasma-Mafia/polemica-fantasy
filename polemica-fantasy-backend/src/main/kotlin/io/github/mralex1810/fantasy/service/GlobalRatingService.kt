package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.config.AppRatingProperties
import io.github.mralex1810.fantasy.dto.user.response.GlobalRatingDto
import io.github.mralex1810.fantasy.entity.TelegramUser
import org.springframework.cache.CacheManager
import org.springframework.stereotype.Service

@Service
class GlobalRatingService(
    private val globalRatingDataCache: GlobalRatingDataCache,
    private val cacheManager: CacheManager,
    private val appRatingProperties: AppRatingProperties,
) {
    /**
     * If the cached snapshot predates the current user row (e.g. new registration), the leaderboard is
     * refreshed so [TelegramUser] is included and [GlobalRatingDto.currentUser] is populated.
     */
    fun getRating(telegramUser: TelegramUser): GlobalRatingDto {
        var entries = globalRatingDataCache.loadSnapshot()
        val missingByDesign = appRatingProperties.isExcludedFromRating(telegramUser.telegramId)
        if (!missingByDesign && entries.none { it.userId == telegramUser.id }) {
            cacheManager.getCache("globalRating")?.evict("all")
            entries = globalRatingDataCache.loadSnapshot()
        }
        val current = entries.find { it.userId == telegramUser.id }
        return GlobalRatingDto(
            entries = entries.map { it.toDto() },
            currentUser = current?.toDto(),
        )
    }
}
