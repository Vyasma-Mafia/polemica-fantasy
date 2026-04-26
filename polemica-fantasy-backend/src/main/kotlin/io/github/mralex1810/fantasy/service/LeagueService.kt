package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.entity.SeriesLeague
import org.springframework.stereotype.Service

@Service
class LeagueService(
    private val economyConfigService: EconomyConfigService,
) {
    fun getEffectiveValueCap(seriesLeague: SeriesLeague): Long? {
        seriesLeague.valueCapOverride?.let { return it }
        val league = seriesLeague.league!!
        if (league.code == BUDGET_CODE) {
            return economyConfigService.getLong("league.budget.value_cap")
        }
        return league.valueCap
    }

    fun getEffectiveRewardScale(seriesLeague: SeriesLeague): Int {
        seriesLeague.rewardScaleOverride?.let { return it }
        return economyConfigService.getInt("league.reward_scale.${seriesLeague.league!!.code}")
    }

    fun getEffectiveMaxLegendary(seriesLeague: SeriesLeague): Int? =
        seriesLeague.league!!.maxLegendaryCount

    companion object {
        const val MAIN_CODE = "MAIN"
        const val BUDGET_CODE = "BUDGET"
    }
}
