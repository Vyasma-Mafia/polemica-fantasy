package io.github.mralex1810.fantasy.dto.admin.response

import java.time.Instant

data class AchievementStatisticsReportDto(
    val generatedAt: Instant,
    val fantasyPlayerCount: Int,
    val profileFetchFailures: Int,
    val uniqueMatchIdsFromProfiles: Int,
    val uniqueGamesLoaded: Int,
    val totalPlayerSlots: Long,
    val gamesFailedToLoad: Int,
    val loadFailures: List<MatchLoadFailureDto>,
    val achievementIdsInCatalog: List<String>,
    val byAchievement: List<AchievementFrequencyRowDto>,
    val anomalySettings: AchievementAnomalySettingsDto,
    val anomalies: List<AchievementPlayerAnomalyDto>,
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

data class AchievementAnomalySettingsDto(
    val minPlayerGames: Int,
    val minApplicableSlots: Int,
    val priorGames: Double,
    val maxAnomalies: Int,
)

data class AchievementPlayerAnomalyDto(
    val fantasyPlayerId: Long,
    val polemicaUserId: Long,
    val nickname: String,
    val achievementId: String,
    val achievementName: String,
    val playerGames: Long,
    val applicableSlots: Long,
    val sumAppliedOccurrences: Long,
    val globalOccurrencesPerGame: Double,
    val playerOccurrencesPerGame: Double,
    val smoothedPlayerOccurrencesPerGame: Double,
    val playerOccurrencesPerApplicableSlot: Double?,
    val globalOccurrencesPerApplicableSlot: Double?,
    val lift: Double?,
    val baselineBonusPerGame: Double,
    val expectedBonusPerGame: Double,
    val excessBonusPerGame: Double,
)
