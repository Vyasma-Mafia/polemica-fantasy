package io.github.mralex1810.fantasy.dto.user.response

import io.github.mralex1810.fantasy.entity.OccurrenceType
import io.github.mralex1810.fantasy.entity.Rarity
import io.github.mralex1810.fantasy.entity.SeriesStatus
import io.github.mralex1810.fantasy.entity.TournamentKind
import io.github.mralex1810.fantasy.entity.TournamentStatus
import java.time.Instant

data class UserProfileDto(
    val id: Long,
    val telegramId: Long,
    val username: String?,
    val firstName: String?,
    val displayName: String?,
    val createdAt: Instant,
    val fantiki: Long,
    /** Успешные открытия паков (магазин). */
    val packOpensCount: Int,
)

data class UserTournamentDto(
    val id: Long,
    val name: String,
    val description: String?,
    val status: TournamentStatus,
    val kind: TournamentKind,
    val polemicaCompetitionId: Long?,
    val createdAt: Instant,
)

data class UserSeriesSummaryDto(
    val id: Long,
    val tournamentId: Long,
    val name: String,
    val namePrefix: String?,
    val gameNumFrom: Long?,
    val gameNumTo: Long?,
    val status: SeriesStatus,
    val startsAt: Instant,
    val teamDeadline: Instant,
    val leagues: List<SeriesLeagueBriefDto> = emptyList(),
)

data class UserTournamentDetailDto(
    val id: Long,
    val name: String,
    val description: String?,
    val status: TournamentStatus,
    val kind: TournamentKind,
    val polemicaCompetitionId: Long?,
    val createdAt: Instant,
    val series: List<UserSeriesSummaryDto>,
)

/** Series in an ACTIVE tournament where team submission deadline has not passed (home / quick lineup). */
data class SeriesOpenForTeamDto(
    val seriesId: Long,
    val tournamentId: Long,
    val tournamentName: String,
    val seriesName: String,
    val gameNumFrom: Long?,
    val gameNumTo: Long?,
    val teamDeadline: Instant,
)

data class SeriesPlayerEntryDto(
    val tournamentPlayerId: Long,
    val fantasyPlayerId: Long,
    val nickname: String,
    val photoUrl: String?,
)

/** Справочник игроков (например, фильтр маркетплейса). */
data class FantasyPlayerBriefDto(
    val id: Long,
    val nickname: String,
    val photoUrl: String?,
)

data class SeriesGameEntryDto(
    val polemicaGameId: Long,
    val gameName: String,
    val scored: Boolean,
)

data class UserSeriesDetailDto(
    val id: Long,
    val tournamentId: Long,
    val name: String,
    val namePrefix: String?,
    val gameNumFrom: Long?,
    val gameNumTo: Long?,
    val status: SeriesStatus,
    val startsAt: Instant,
    val teamDeadline: Instant,
    val players: List<SeriesPlayerEntryDto>,
    val games: List<SeriesGameEntryDto>,
    val leagues: List<SeriesLeagueBriefDto> = emptyList(),
)

data class SeriesLeagueBriefDto(
    val code: String,
    val name: String,
    val hasTeam: Boolean,
    val valueCap: Long?,
)

data class UserPublicDto(
    val telegramId: Long,
    val username: String?,
    val firstName: String?,
    val displayName: String?,
)

data class LeaderboardEntryDto(
    val rank: Int,
    val totalScore: Double?,
    val user: UserPublicDto,
    val fantasyPlayerIds: List<Long> = emptyList(),
)

data class CardAchievementBriefDto(
    val achievementId: String,
    val achievementName: String,
    val bonusPoints: Double,
)

data class ActiveMarketplaceListingBriefDto(
    val listingId: Long,
    val price: Long,
)

data class UserCardItemDto(
    val id: Long,
    val acquiredAt: Instant,
    val cardTemplateId: Long,
    val fantasyPlayerId: Long,
    val rarity: Rarity,
    val imageUrl: String?,
    val description: String?,
    val playerNickname: String,
    val playerPhotoUrl: String?,
    val achievements: List<CardAchievementBriefDto>,
    val usesRemaining: Int,
    val timesRenewed: Int,
    val sourceCardPackId: Long?,
    /** Telegram user id of the crafter (platform id); null for admin-issued cards. */
    val craftedByTelegramUserId: Long?,
    /** Вычисляемая ценность карты (см. economy card.value.*). */
    val value: Long,
    /** Коды лиг этой серии, где карта уже в составе (только при запросе с seriesId). */
    val leaguesInSeries: List<String>? = null,
    /** Можно ли поставить карту ещё хотя бы в одну лигу этой серии (только при seriesId). */
    val canJoinMoreLeagues: Boolean? = null,
    /** Активный лот на маркетплейсе для этой user_card; null если не выставлена. */
    val activeMarketplaceListing: ActiveMarketplaceListingBriefDto? = null,
)

data class LegendaryUpgradeInfoDto(
    val cost: Long,
    val balance: Long,
    val canAfford: Boolean,
)

data class LegendaryEasterEggDto(
    val message: String,
    val bonusFantiki: Long,
    val companionCardName: String,
    val companionCardImageUrl: String?,
)

data class LegendaryUpgradeResponseDto(
    val card: UserCardItemDto,
    val easterEgg: LegendaryEasterEggDto? = null,
)

data class FantasyTeamSlotDto(
    val slot: Int,
    val userCardId: Long,
    val score: Double?,
)

data class FantasyTeamDto(
    val seriesId: Long,
    val tournamentId: Long,
    val leagueCode: String,
    val totalScore: Double?,
    val submittedAt: Instant,
    val slots: List<FantasyTeamSlotDto>,
)

data class PublicFantasyTeamSlotDto(
    val slot: Int,
    val score: Double?,
    val card: UserCardItemDto,
)

data class PublicFantasyTeamDto(
    val owner: UserPublicDto,
    val seriesId: Long,
    val tournamentId: Long,
    val leagueCode: String,
    val totalScore: Double?,
    val submittedAt: Instant,
    val slots: List<PublicFantasyTeamSlotDto>,
)

data class StorePackRaritySlotDto(
    val rarity: Rarity,
    val cardsCount: Int,
)

data class StorePackItemDto(
    val id: Long,
    val name: String,
    val priceFantiki: Long,
    /** Remaining free opens for the current user; 0 when not applicable. */
    val freeOpensRemaining: Int,
    /** Max total opens per user for this pack; 0 = unlimited. */
    val maxOpensPerUser: Int,
    /** How many cards the user already got from this pack (opens used). */
    val packOpensUsed: Int,
    val rarityLayout: List<StorePackRaritySlotDto>,
)

data class BuyPackResponseDto(
    val fantiki: Long,
    val cards: List<UserCardItemDto>,
    val openingCards: List<PackOpeningCardDto>,
)

enum class PackOpeningCardKind {
    USER_CARD,
    COMPANION,
}

data class PackOpeningCardDto(
    val kind: PackOpeningCardKind,
    /** Present for [PackOpeningCardKind.USER_CARD]. */
    val card: UserCardItemDto? = null,
    /** Present for [PackOpeningCardKind.COMPANION]. */
    val companionCardName: String? = null,
    /** Present for [PackOpeningCardKind.COMPANION]. */
    val companionCardImageUrl: String? = null,
    val rarity: Rarity,
    val value: Long,
    /** Developer user card that spawned this visual companion. */
    val relatedUserCardId: Long? = null,
) {
    companion object {
        fun userCard(card: UserCardItemDto): PackOpeningCardDto = PackOpeningCardDto(
            kind = PackOpeningCardKind.USER_CARD,
            card = card,
            rarity = card.rarity,
            value = card.value,
        )

        fun companion(
            relatedUserCardId: Long,
            rarity: Rarity,
            value: Long,
            companionCardName: String,
            companionCardImageUrl: String?,
        ): PackOpeningCardDto = PackOpeningCardDto(
            kind = PackOpeningCardKind.COMPANION,
            companionCardName = companionCardName,
            companionCardImageUrl = companionCardImageUrl,
            rarity = rarity,
            value = value,
            relatedUserCardId = relatedUserCardId,
        )
    }
}

data class FantasyTeamSeriesGameInfoDto(
    val seriesGameId: Long,
    val polemicaGameId: Long,
    val gameName: String,
    val scored: Boolean,
)

data class AchievementInGameDto(
    val achievementId: String,
    val achievementName: String,
    val bonusPoints: Double,
)

data class CardGameBreakdownDto(
    val basePoints: Double?,
    val achievementBonus: Double?,
    val rarityModifier: Double?,
    val totalScore: Double?,
    val achievements: List<AchievementInGameDto>,
)

data class FantasyTeamDetailSlotDto(
    val slot: Int,
    val userCardId: Long,
    /** Same order as [FantasyTeamSeriesDetailsDto.games]. */
    val cells: List<CardGameBreakdownDto?>,
)

data class FantasyTeamSeriesDetailsDto(
    val seriesId: Long,
    val games: List<FantasyTeamSeriesGameInfoDto>,
    val columns: List<FantasyTeamDetailSlotDto>,
)

data class AchievementCatalogItemDto(
    val id: String,
    val name: String,
    val description: String?,
    val bonusPoints: Double,
    val occurrenceType: OccurrenceType,
    val applicableRoles: List<String>,
    val canAppearOnRandomCards: Boolean,
)
