package io.github.mralex1810.fantasy.dto.user.response

data class OnboardingChecklistItemDto(
    val step: String,
    val title: String,
    val description: String,
    val completed: Boolean,
    val ctaLabel: String,
    val ctaPath: String,
)

data class OnboardingChecklistDto(
    val completedCount: Int,
    val totalCount: Int,
    val primaryCta: OnboardingChecklistItemDto?,
    val items: List<OnboardingChecklistItemDto>,
)
