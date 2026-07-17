package io.github.mralex1810.fantasy.service

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

object PeriodicRatingRules {
    fun isRewarded(rank: Int): Boolean = rank in 1..10

    fun rewardTier(rank: Int): String = when (rank) {
        1 -> "champion"
        2 -> "silver"
        3 -> "bronze"
        in 4..10 -> "finalist"
        else -> throw IllegalArgumentException("Rank $rank is outside the reward ladder")
    }

    fun rewardRarity(rank: Int): String = when (rank) {
        1 -> "EPIC"
        in 2..5 -> "RARE"
        in 6..10 -> "COMMON"
        else -> throw IllegalArgumentException("Rank $rank is outside the reward ladder")
    }

    fun rewardFantiki(rank: Int): Long = if (rank in 6..10) 50L else 0L

    fun rewardSkinCodes(rank: Int): List<String> = listOf("aurora", "crimson", "nocturne")
        .map { "rating_${rewardTier(rank)}_edition_$it" }

    fun contains(startsAt: Instant, endsAt: Instant, value: Instant): Boolean =
        !value.isBefore(startsAt) && value.isBefore(endsAt)

    fun overlaps(firstStart: Instant, firstEnd: Instant, secondStart: Instant, secondEnd: Instant): Boolean =
        firstStart.isBefore(secondEnd) && firstEnd.isAfter(secondStart)

    fun roundedSum(values: Collection<Double>): BigDecimal =
        values.fold(BigDecimal.ZERO) { total, value -> total + BigDecimal.valueOf(value) }
            .setScale(2, RoundingMode.HALF_UP)

    fun competitionRanks(valuesDescending: List<BigDecimal>): List<Int> {
        var previous: BigDecimal? = null
        var rank = 0
        return valuesDescending.mapIndexed { index, value ->
            if (previous == null || value.compareTo(previous) != 0) rank = index + 1
            previous = value
            rank
        }
    }

    fun isBlocker(finalized: Boolean, effectiveAt: Instant?, explicitlyIncluded: Boolean): Boolean =
        explicitlyIncluded && (!finalized || effectiveAt == null)
}
