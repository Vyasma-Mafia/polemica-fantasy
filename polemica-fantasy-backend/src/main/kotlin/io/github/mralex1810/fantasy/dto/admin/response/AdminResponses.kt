package io.github.mralex1810.fantasy.dto.admin.response

import io.github.mralex1810.fantasy.entity.AchievementType
import io.github.mralex1810.fantasy.entity.Rarity
import io.github.mralex1810.fantasy.entity.SeriesStatus
import io.github.mralex1810.fantasy.entity.TournamentKind
import io.github.mralex1810.fantasy.entity.TournamentStatus
import java.time.Instant

data class TournamentDto(
    val id: Long,
    val name: String,
    val description: String?,
    val status: TournamentStatus,
    val kind: TournamentKind,
    val polemicaCompetitionId: Long?,
    val createdAt: Instant,
)

data class TournamentDetailDto(
    val id: Long,
    val name: String,
    val description: String?,
    val status: TournamentStatus,
    val kind: TournamentKind,
    val polemicaCompetitionId: Long?,
    val createdAt: Instant,
    val players: List<TournamentPlayerDto>,
)

data class TournamentPlayerDto(
    val id: Long,
    val tournamentId: Long,
    val fantasyPlayerId: Long,
    val polemicaUserId: Long,
    val nickname: String,
    val photoUrl: String?,
)

data class SeriesDto(
    val id: Long,
    val tournamentId: Long,
    val name: String,
    val namePrefix: String?,
    val gameNumFrom: Long?,
    val gameNumTo: Long?,
    val status: SeriesStatus,
    val startsAt: Instant,
    val teamDeadline: Instant,
    /** `tournament_player.id` for players assigned to this series */
    val tournamentPlayerIds: List<Long>,
)

data class CardTemplateAchievementDto(
    val id: Long,
    val achievementType: AchievementType,
    val bonusPoints: Double,
)

data class CardTemplateDto(
    val id: Long,
    val fantasyPlayerId: Long,
    val rarity: Rarity,
    val imageUrl: String?,
    val description: String?,
    val achievements: List<CardTemplateAchievementDto>,
)

data class CardPackRarityConfigResponseDto(
    val id: Long,
    val rarity: Rarity,
    val probability: Double,
    val cardsCount: Int,
)

data class CardPackDto(
    val id: Long,
    val name: String,
    val tournamentId: Long,
    val active: Boolean,
    val rarityConfigs: List<CardPackRarityConfigResponseDto>,
)

data class UserCardDto(
    val id: Long,
    val telegramUserId: Long,
    val cardTemplateId: Long,
    val acquiredAt: Instant,
)

data class OpenPackResultDto(
    val userCards: List<UserCardDto>,
)

data class ApiErrorBody(
    val message: String,
    val fieldErrors: Map<String, String>? = null,
)
