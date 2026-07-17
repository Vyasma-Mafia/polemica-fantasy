package io.github.mralex1810.fantasy.dto.periodicrating

import java.math.BigDecimal
import java.time.Instant
import com.fasterxml.jackson.databind.JsonNode

data class PeriodicRatingPeriodDto(
    val id: Long,
    val code: String,
    val title: String,
    val startsAt: Instant,
    val endsAt: Instant,
    val timezone: String,
    val league: String,
    val status: String,
    val provisional: Boolean,
    val seriesCount: Int,
    val participantCount: Int,
    val finalizedAt: Instant?,
)

data class PeriodicRatingUserDto(
    val telegramId: Long,
    val username: String?,
    val firstName: String?,
    val displayName: String?,
    val profileFrameCode: String? = null,
)

data class PeriodicRatingEntryDto(
    val user: PeriodicRatingUserDto,
    val rank: Int,
    val totalScore: BigDecimal,
    val seriesCount: Int,
    val averageScore: BigDecimal,
    val bestSeriesScore: BigDecimal,
)

data class PeriodicRatingContributionDto(
    val seriesId: Long,
    val seriesName: String,
    val tournamentId: Long,
    val tournamentName: String,
    val score: BigDecimal,
    val seriesRank: Int,
    val participantsCount: Int,
)

data class PeriodicRatingLeaderboardDto(
    val period: PeriodicRatingPeriodDto,
    val provisional: Boolean,
    val entries: List<PeriodicRatingEntryDto>,
    val currentUser: PeriodicRatingEntryDto?,
    val blockersCount: Int,
)

data class PeriodicRatingMeDto(
    val period: PeriodicRatingPeriodDto,
    val entry: PeriodicRatingEntryDto?,
    val contributions: List<PeriodicRatingContributionDto>,
)

data class PeriodicRatingSeriesPreviewDto(
    val seriesId: Long,
    val seriesName: String,
    val tournamentId: Long,
    val tournamentName: String,
    val effectiveAt: Instant?,
    val included: Boolean,
    val reason: String?,
    val finalized: Boolean,
    val blocker: Boolean,
)

data class PeriodicRatingPreviewDto(
    val period: PeriodicRatingPeriodDto,
    val sourceChecksum: String,
    val series: List<PeriodicRatingSeriesPreviewDto>,
    val blockers: List<PeriodicRatingSeriesPreviewDto>,
    val leaderboard: List<PeriodicRatingEntryDto>,
    val rewardLiability: Map<String, Int>,
)

data class CreatePeriodicRatingPeriodRequest(
    val code: String,
    val title: String,
    val startsAt: Instant,
    val endsAt: Instant,
)

data class UpdatePeriodicRatingSeriesRequest(
    val included: Boolean,
    val reason: String? = null,
)

data class FinalizePeriodicRatingRequest(
    val sourceChecksum: String,
    val reason: String,
)

data class PeriodicRatingRewardDto(
    val id: Long,
    val periodId: Long,
    val periodCode: String,
    val periodTitle: String,
    val user: PeriodicRatingUserDto,
    val rank: Int,
    val status: String,
    val serial: String,
    val policy: JsonNode,
    val selection: JsonNode,
    val selectedPlayer: PeriodicRatingRewardPlayerDto?,
    val version: Int,
    val claimDeadline: Instant?,
    val changesRequestedReason: String?,
    val fantikiAmount: Long,
    val fantikiGrantedAt: Instant?,
    val issuedCardTemplateId: Long?,
    val issuedUserCardId: Long?,
    val issuedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class PeriodicRatingRewardDraftRequest(
    val playerId: Long,
    val perkIds: List<String> = emptyList(),
    val skinCode: String,
    val version: Int,
)

data class PeriodicRatingRewardVersionRequest(val version: Int)

data class RequestPeriodicRatingRewardChangesRequest(
    val reason: String,
    val version: Int,
)

data class PeriodicRatingRewardPlayerDto(
    val id: Long,
    val polemicaUserId: Long,
    val nickname: String,
    val photoUrl: String?,
)

data class PeriodicRatingRewardPlayerPageDto(
    val content: List<PeriodicRatingRewardPlayerDto>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
)
