package io.github.mralex1810.fantasy.controller.user

import io.github.mralex1810.fantasy.entity.OnboardingStep
import io.github.mralex1810.fantasy.entity.TelegramUser
import io.github.mralex1810.fantasy.service.OnboardingService
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/v1/onboarding")
class OnboardingController(
    private val onboardingService: OnboardingService,
) {
    @GetMapping
    fun getChecklist(@AuthenticationPrincipal user: TelegramUser) = onboardingService.checklist(user)

    @PostMapping("/steps/{step}/complete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun markStep(
        @AuthenticationPrincipal user: TelegramUser,
        @PathVariable step: String,
    ) {
        val parsed = try {
            OnboardingStep.valueOf(step.trim().uppercase())
        } catch (_: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown onboarding step: $step")
        }
        onboardingService.markStep(user.id!!, parsed)
    }
}
