package io.github.mralex1810.fantasy.dto.user.response

import io.github.mralex1810.fantasy.entity.Rarity

data class RecycleResultDto(
    val fantikiEarned: Long,
    val newBalance: Long,
)

data class RenewResultDto(
    val cost: Long,
    val newBalance: Long,
    val newUsesRemaining: Int,
)

data class SeriesFinalizationResultDto(
    val rewardsDistributed: Int,
    val cardsDecremented: Int,
)

data class RewardTierDto(
    val label: String,
    val fantiki: Long,
)

data class EconomyInfoDto(
    val usesPerRarity: Map<Rarity, Int>,
    val recycleValues: Map<Rarity, Long>,
    val renewalCosts: Map<Rarity, Long>,
    val maxRenewals: Int,
    val seriesRewards: List<RewardTierDto>,
)
