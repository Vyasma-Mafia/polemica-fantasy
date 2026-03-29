package io.github.mralex1810.fantasy.dto.admin.request

import io.github.mralex1810.fantasy.entity.OccurrenceType

data class UpdateAchievementRequest(
    val name: String? = null,
    val description: String? = null,
    val bonusPoints: Double? = null,
    val occurrenceType: OccurrenceType? = null,
    /** Full replacement when present (including empty = clear all roles). */
    val applicableRoles: List<String>? = null,
    val canAppearOnRandomCards: Boolean? = null,
)
