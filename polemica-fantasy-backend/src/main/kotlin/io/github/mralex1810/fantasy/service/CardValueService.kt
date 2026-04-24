package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.entity.CardTemplate
import io.github.mralex1810.fantasy.entity.Rarity
import io.github.mralex1810.fantasy.entity.UserCard
import org.springframework.stereotype.Service

@Service
class CardValueService(
    private val economyConfigService: EconomyConfigService,
) {
    fun calculateValue(rarity: Rarity, achievementCount: Int): Long {
        val base = economyConfigService.getCardBaseValue(rarity)
        val bonus = economyConfigService.getCardAchievementBonus()
        return base + achievementCount * bonus
    }

    fun calculateValue(cardTemplate: CardTemplate): Long {
        return calculateValue(
            cardTemplate.rarity,
            cardTemplate.achievements.distinctBy { it.achievement!!.id }.size,
        )
    }

    fun calculateValue(userCard: UserCard): Long {
        return calculateValue(userCard.cardTemplate!!)
    }
}
