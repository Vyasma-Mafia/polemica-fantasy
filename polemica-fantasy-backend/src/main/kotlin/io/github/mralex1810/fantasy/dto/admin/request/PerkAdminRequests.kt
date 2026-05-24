package io.github.mralex1810.fantasy.dto.admin.request

import io.github.mralex1810.fantasy.entity.OccurrenceType

data class UpdatePerkRequest(
    val name: String? = null,
    val description: String? = null,
    val bonusPoints: Double? = null,
    val occurrenceType: OccurrenceType? = null,
    /** Full replacement when present (including empty = clear all roles). */
    val applicableRoles: List<String>? = null,
    val canAppearOnRandomCards: Boolean? = null,
)

data class PerkStatisticsRequest(
    /** Minimum loaded games for a fantasy player to appear in anomaly results. */
    val minPlayerGames: Int? = null,
    /** Minimum role-applicable games for this player + perk pair. */
    val minApplicableSlots: Int? = null,
    /** Empirical-Bayes prior strength, in player-games, used to dampen small samples. */
    val priorGames: Double? = null,
    val maxAnomalies: Int? = null,
)
