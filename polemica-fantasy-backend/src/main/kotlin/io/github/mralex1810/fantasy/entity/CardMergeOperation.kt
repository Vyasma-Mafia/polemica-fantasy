package io.github.mralex1810.fantasy.entity

enum class CardMergeOperation(
    val sourceRarity: Rarity,
    val resultRarity: Rarity,
) {
    COMMON_TO_RARE(Rarity.COMMON, Rarity.RARE),
    RARE_TO_EPIC(Rarity.RARE, Rarity.EPIC),
}
