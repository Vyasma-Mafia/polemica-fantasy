package io.github.mralex1810.fantasy.dto.admin.request

import com.fasterxml.jackson.annotation.JsonAnySetter

data class UpdateAchievementAdminRequest(
    val title: String,
    val description: String?,
    val iconUrl: String?,
    val accentColor: String?,
    val rarity: String,
    val visibility: String,
    val enabled: Boolean,
    val displayOrder: Int,
    val rewards: List<UpdateAchievementAdminRewardRequest>,
) {
    @JsonAnySetter
    fun rejectUnknownField(name: String, value: Any?) {
        throw IllegalArgumentException("Unknown field: $name")
    }
}

data class UpdateAchievementAdminRewardRequest(
    val type: String,
    val amount: Long?,
    val code: String?,
    val metadata: String?,
    val displayOrder: Int,
) {
    @JsonAnySetter
    fun rejectUnknownField(name: String, value: Any?) {
        throw IllegalArgumentException("Unknown field: $name")
    }
}
