package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.entity.EconomyConfig
import io.github.mralex1810.fantasy.repository.EconomyConfigRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class EconomyConfigServiceTest {

    @Mock
    private lateinit var economyConfigRepository: EconomyConfigRepository

    @Test
    fun `getSeriesReward uses top100 tier before participation`() {
        val service = EconomyConfigService(economyConfigRepository)
        whenever(economyConfigRepository.findAll()).thenReturn(
            listOf(
                EconomyConfig("series.reward.1", "250", "Награда за 1 место"),
                EconomyConfig("series.reward.2", "200", "Награда за 2 место"),
                EconomyConfig("series.reward.3", "150", "Награда за 3 место"),
                EconomyConfig("series.reward.top10", "100", "Награда за 4-10 место"),
                EconomyConfig("series.reward.top25", "75", "Награда за 11–25 место"),
                EconomyConfig("series.reward.top50", "50", "Награда за 26–50 место"),
                EconomyConfig("series.reward.top100", "40", "Награда за 51–100 место"),
                EconomyConfig("series.reward.participation", "30", "Награда за участие (101+ место)"),
            ),
        )

        assertEquals(50L, service.getSeriesReward(50, 120))
        assertEquals(40L, service.getSeriesReward(51, 120))
        assertEquals(40L, service.getSeriesReward(100, 120))
        assertEquals(30L, service.getSeriesReward(101, 120))
    }

    @Test
    fun `buildEconomyInfo returns series reward tiers in scoring order`() {
        val service = EconomyConfigService(economyConfigRepository)
        val rewardRows = listOf(
            EconomyConfig("series.reward.1", "250", "Награда за 1 место"),
            EconomyConfig("series.reward.2", "200", "Награда за 2 место"),
            EconomyConfig("series.reward.3", "150", "Награда за 3 место"),
            EconomyConfig("series.reward.top10", "100", "Награда за 4-10 место"),
            EconomyConfig("series.reward.top25", "75", "Награда за 11–25 место"),
            EconomyConfig("series.reward.top50", "50", "Награда за 26–50 место"),
            EconomyConfig("series.reward.top100", "40", "Награда за 51–100 место"),
            EconomyConfig("series.reward.participation", "30", "Награда за участие (101+ место)"),
        )
        val rows = rewardRows + listOf(
            EconomyConfig("card.uses.COMMON", "2", null),
            EconomyConfig("card.uses.RARE", "3", null),
            EconomyConfig("card.uses.EPIC", "4", null),
            EconomyConfig("card.uses.LEGENDARY", "5", null),
            EconomyConfig("recycle.value.COMMON", "10", null),
            EconomyConfig("recycle.value.RARE", "25", null),
            EconomyConfig("recycle.value.EPIC", "60", null),
            EconomyConfig("recycle.value.LEGENDARY", "200", null),
            EconomyConfig("renewal.cost.COMMON", "15", null),
            EconomyConfig("renewal.cost.RARE", "30", null),
            EconomyConfig("renewal.cost.EPIC", "90", null),
            EconomyConfig("renewal.cost.LEGENDARY", "250", null),
            EconomyConfig("renewal.max_times", "2", null),
            EconomyConfig("marketplace.commission_percent", "15", null),
            EconomyConfig("marketplace.min_price.COMMON", "20", null),
            EconomyConfig("marketplace.min_price.RARE", "40", null),
            EconomyConfig("marketplace.min_price.EPIC", "120", null),
            EconomyConfig("marketplace.min_price.LEGENDARY", "250", null),
            EconomyConfig("marketplace.max_price.COMMON", "150", null),
            EconomyConfig("marketplace.max_price.RARE", "300", null),
            EconomyConfig("marketplace.max_price.EPIC", "600", null),
            EconomyConfig("marketplace.max_price.LEGENDARY", "1500", null),
            EconomyConfig("marketplace.min_pack_opens_before_purchase", "3", null),
            EconomyConfig("card.value.COMMON", "25", null),
            EconomyConfig("card.value.RARE", "40", null),
            EconomyConfig("card.value.EPIC", "80", null),
            EconomyConfig("card.value.LEGENDARY", "370", null),
            EconomyConfig("card.value.perk_bonus", "10", null),
            EconomyConfig("league.reward_scale.MAIN", "100", null),
            EconomyConfig("league.reward_scale.BUDGET", "75", null),
            EconomyConfig("league.budget.value_cap", "175", null),
        )
        whenever(economyConfigRepository.findAll()).thenReturn(rows)
        rewardRows.forEach { row ->
            whenever(economyConfigRepository.findById(row.key)).thenReturn(Optional.of(row))
        }

        val tiers = service.buildEconomyInfo().seriesRewards

        assertEquals(
            listOf(
                "Награда за 1 место",
                "Награда за 2 место",
                "Награда за 3 место",
                "Награда за 4-10 место",
                "Награда за 11–25 место",
                "Награда за 26–50 место",
                "Награда за 51–100 место",
                "Награда за участие (101+ место)",
            ),
            tiers.map { it.label },
        )
        assertEquals(listOf(250L, 200L, 150L, 100L, 75L, 50L, 40L, 30L), tiers.map { it.fantiki })
    }
}
