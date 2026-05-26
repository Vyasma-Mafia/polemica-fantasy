package io.github.mralex1810.fantasy.dto.user.request

import com.fasterxml.jackson.annotation.JsonAnySetter

data class AchievementCardChoiceRequest(
    val optionIds: List<String>,
) {
    @JsonAnySetter
    fun rejectUnknownField(name: String, value: Any?) {
        throw IllegalArgumentException("Unknown field: $name")
    }
}
