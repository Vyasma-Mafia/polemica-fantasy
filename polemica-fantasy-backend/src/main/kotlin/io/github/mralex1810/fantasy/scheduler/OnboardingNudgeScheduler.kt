package io.github.mralex1810.fantasy.scheduler

import io.github.mralex1810.fantasy.service.OnboardingNudgeService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class OnboardingNudgeScheduler(
    private val onboardingNudgeService: OnboardingNudgeService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedRate = 3_600_000)
    fun sendDueNudges() {
        try {
            onboardingNudgeService.sendDueNudges()
        } catch (e: Exception) {
            log.warn("Failed to send onboarding nudges", e)
        }
    }
}
