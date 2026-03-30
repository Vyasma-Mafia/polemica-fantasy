package io.github.mralex1810.fantasy.dto.admin.response

import java.time.Instant

data class AchievementStatisticsReportDto(
    val generatedAt: Instant,
    val fantasyPlayerCount: Int,
    val profileFetchFailures: Int,
    val uniqueMatchIdsFromProfiles: Int,
    val uniqueGamesLoaded: Int,
    val gamesFailedToLoad: Int,
    val loadFailures: List<MatchLoadFailureDto>,
    val achievementIdsInCatalog: List<String>,
    val byAchievement: List<AchievementFrequencyRowDto>,
)

data class AchievementFrequencyRowDto(
    val achievementId: String,
    val applicableSlots: Long,
    val sumRawMatchCount: Long,
    val slotsWithPositiveRaw: Long,
    val sumAppliedOccurrences: Long,
)

data class MatchLoadFailureDto(
    val matchId: Long,
    val error: String?,
)
