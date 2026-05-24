package io.github.mralex1810.fantasy.repository

import io.github.mralex1810.fantasy.entity.OnboardingProgress
import org.springframework.data.jpa.repository.JpaRepository

interface OnboardingProgressRepository : JpaRepository<OnboardingProgress, Long>
