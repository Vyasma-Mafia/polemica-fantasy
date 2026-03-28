package io.github.mralex1810.fantasy.dto.user.response

import io.github.mralex1810.fantasy.entity.AchievementType
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
    val createdAt: Instant,
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

data class SeriesPlayerEntryDto(
    val tournamentPlayerId: Long,
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
)

data class UserPublicDto(
    val telegramId: Long,
    val username: String?,
    val firstName: String?,
)

data class LeaderboardEntryDto(
    val rank: Int,
    val totalScore: Double?,
    val user: UserPublicDto,
)

data class CardAchievementBriefDto(
    val achievementType: AchievementType,
    val bonusPoints: Double,
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
)

data class FantasyTeamSlotDto(
    val slot: Int,
    val userCardId: Long,
    val score: Double?,
)

data class FantasyTeamDto(
    val seriesId: Long,
    val tournamentId: Long,
    val totalScore: Double?,
    val submittedAt: Instant,
    val slots: List<FantasyTeamSlotDto>,
)
