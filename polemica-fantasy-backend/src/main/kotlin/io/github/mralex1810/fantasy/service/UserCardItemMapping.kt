package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.dto.user.response.ActiveMarketplaceListingBriefDto
import io.github.mralex1810.fantasy.dto.user.response.CardPerkBriefDto
import io.github.mralex1810.fantasy.dto.user.response.UserCardItemDto
import io.github.mralex1810.fantasy.dto.user.response.TrophyCardProvenanceDto
import io.github.mralex1810.fantasy.entity.CardTemplate
import io.github.mralex1810.fantasy.entity.UserCard

/**
 * @param templateOverride optional template loaded in the same transaction with perks FETCH
 * (see [io.github.mralex1810.fantasy.repository.CardTemplateRepository.findAllByIdWithPerksLoaded]).
 */
fun UserCard.toUserCardItemDto(
    templateOverride: CardTemplate? = null,
    imageStorage: ImageStorageService,
    cardValueService: CardValueService,
    leaguesInSeries: List<String>? = null,
    canJoinMoreLeagues: Boolean? = null,
    activeMarketplaceListing: ActiveMarketplaceListingBriefDto? = null,
    minListingPrice: Long? = null,
): UserCardItemDto {
    val ct = templateOverride ?: cardTemplate!!
    val fp = ct.fantasyPlayer!!
    return UserCardItemDto(
        id = id!!,
        acquiredAt = acquiredAt,
        cardTemplateId = ct.id!!,
        fantasyPlayerId = fp.id!!,
        rarity = ct.rarity,
        imageUrl = imageStorage.publicObjectUrl(ct.imageUrl),
        description = ct.description,
        playerNickname = fp.nickname,
        playerPhotoUrl = imageStorage.publicObjectUrl(fp.photoUrl),
        perks = ct.perks
            .distinctBy { it.perk!!.id }
            .map { a ->
                val def = a.perk!!
                CardPerkBriefDto(
                    perkId = def.id,
                    perkName = def.name,
                    bonusPoints = a.bonusPoints ?: def.bonusPoints,
                )
            },
        usesRemaining = usesRemaining,
        timesRenewed = timesRenewed,
        minListingPrice = minListingPrice,
        sourceCardPackId = sourceCardPack?.id,
        craftedByTelegramUserId = craftedBy?.telegramId,
        value = cardValueService.calculateValue(ct),
        leaguesInSeries = leaguesInSeries,
        canJoinMoreLeagues = canJoinMoreLeagues,
        activeMarketplaceListing = activeMarketplaceListing,
        skinCode = cardSkin?.code,
        trophyProvenance = trophyRewardId?.let { rewardId ->
            TrophyCardProvenanceDto(
                rewardId = rewardId,
                periodId = trophyPeriodId!!,
                periodTitle = trophyPeriodTitle!!,
                rank = trophyRank!!,
                serial = trophySerial!!,
                originalOwnerTelegramId = trophyOriginalOwnerTelegramId!!,
            )
        },
    )
}
