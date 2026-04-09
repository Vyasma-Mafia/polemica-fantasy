package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.dto.user.response.EconomyInfoDto
import io.github.mralex1810.fantasy.dto.user.response.RewardTierDto
import io.github.mralex1810.fantasy.entity.Rarity
import io.github.mralex1810.fantasy.repository.EconomyConfigRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.util.concurrent.ConcurrentHashMap

@Service
class EconomyConfigService(
    private val economyConfigRepository: EconomyConfigRepository,
) {
    private val cache = ConcurrentHashMap<String, String>()

    /** Order of tiers in UI / finalize logic must stay in sync with [getSeriesReward]. */
    private val seriesRewardKeysInOrder: List<String> = listOf(
        "series.reward.1",
        "series.reward.2",
        "series.reward.3",
        "series.reward.top10",
        "series.reward.top25",
        "series.reward.top50",
        "series.reward.participation",
    )

    fun invalidateCache() {
        cache.clear()
    }

    private fun ensureLoaded() {
        if (cache.isNotEmpty()) return
        synchronized(this) {
            if (cache.isNotEmpty()) return
            val rows = economyConfigRepository.findAll()
            if (rows.isEmpty()) {
                throw IllegalStateException("economy_config table is empty")
            }
            rows.forEach { cache[it.key] = it.value }
        }
    }

    private fun raw(key: String): String {
        ensureLoaded()
        return cache[key]
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Economy config key not found: $key")
    }

    fun getInt(key: String): Int = raw(key).toInt()

    fun getLong(key: String): Long = raw(key).toLong()

    fun getUsesForRarity(rarity: Rarity): Int = getInt("card.uses.$rarity")

    fun getRecycleValue(rarity: Rarity): Long = getLong("recycle.value.$rarity")

    fun getRenewalCost(rarity: Rarity): Long = getLong("renewal.cost.$rarity")

    fun getMaxRenewals(): Int = getInt("renewal.max_times")

    fun getLegendaryUpgradeCost(): Long = getLong("legendary.upgrade.cost")

    fun getLegendaryTeamMaxPerSeries(): Int = getInt("legendary.team.max_per_series")

    fun getMarketplaceCommissionPercent(): Int = getInt("marketplace.commission_percent")

    fun getMaxListingPrice(rarity: Rarity): Long = getLong("marketplace.max_price.$rarity")

    fun getMinPackOpensBeforeMarketplacePurchase(): Int = getInt("marketplace.min_pack_opens_before_purchase")

    /**
     * @param position 1-based rank on the series leaderboard (same order as [io.github.mralex1810.fantasy.repository.FantasyTeamRepository.findLeaderboardForSeries]).
     */
    @Suppress("UNUSED_PARAMETER")
    fun getSeriesReward(position: Int, totalParticipants: Int): Long {
        return when {
            position <= 1 -> getLong("series.reward.1")
            position == 2 -> getLong("series.reward.2")
            position == 3 -> getLong("series.reward.3")
            position in 4..10 -> getLong("series.reward.top10")
            position in 11..25 -> getLong("series.reward.top25")
            position in 26..50 -> getLong("series.reward.top50")
            else -> getLong("series.reward.participation")
        }
    }

    fun buildEconomyInfo(): EconomyInfoDto {
        val uses = Rarity.entries.associateWith { getUsesForRarity(it) }
        val recycle = Rarity.entries.associateWith { getRecycleValue(it) }
        val renewal = Rarity.entries.associateWith { getRenewalCost(it) }
        val marketplaceMax = Rarity.entries.associateWith { getMaxListingPrice(it) }
        val tiers = seriesRewardKeysInOrder.map { key ->
            val row = economyConfigRepository.findById(key).orElseThrow {
                ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Economy config row missing: $key")
            }
            val label = row.description?.trim()?.takeIf { it.isNotEmpty() } ?: key
            RewardTierDto(label = label, fantiki = getLong(key))
        }
        return EconomyInfoDto(
            usesPerRarity = uses,
            recycleValues = recycle,
            renewalCosts = renewal,
            maxRenewals = getMaxRenewals(),
            seriesRewards = tiers,
            marketplaceCommissionPercent = getMarketplaceCommissionPercent(),
            marketplaceMaxPrices = marketplaceMax,
            minPackOpensBeforeMarketplacePurchase = getMinPackOpensBeforeMarketplacePurchase(),
        )
    }
}
