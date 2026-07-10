package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.dto.user.response.OnboardingChecklistDto
import io.github.mralex1810.fantasy.dto.user.response.OnboardingChecklistItemDto
import io.github.mralex1810.fantasy.entity.OnboardingNudgeType
import io.github.mralex1810.fantasy.entity.OnboardingProgress
import io.github.mralex1810.fantasy.entity.OnboardingStep
import io.github.mralex1810.fantasy.entity.SeriesStatus
import io.github.mralex1810.fantasy.entity.TelegramUser
import io.github.mralex1810.fantasy.entity.TournamentStatus
import io.github.mralex1810.fantasy.repository.FantasyTeamRepository
import io.github.mralex1810.fantasy.repository.OnboardingProgressRepository
import io.github.mralex1810.fantasy.repository.SeriesRepository
import io.github.mralex1810.fantasy.repository.TelegramUserRepository
import io.github.mralex1810.fantasy.repository.UserCardRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

data class OpenTeamTarget(
    val path: String,
    val deadline: Instant,
)

@Service
class OnboardingService(
    private val onboardingProgressRepository: OnboardingProgressRepository,
    private val telegramUserRepository: TelegramUserRepository,
    private val fantasyTeamRepository: FantasyTeamRepository,
    private val userCardRepository: UserCardRepository,
    private val seriesRepository: SeriesRepository,
    private val productEventService: ProductEventService,
) {
    @Transactional(readOnly = true)
    fun checklist(user: TelegramUser): OnboardingChecklistDto {
        val userId = user.id!!
        val progress = onboardingProgressRepository.findById(userId).orElse(null)
        val packDone = progress?.firstPackOpenedAt != null || user.packOpensCount > 0
        val collectionDone = progress?.collectionViewedAt != null
        val teamDone = progress?.firstTeamSubmittedAt != null || fantasyTeamRepository.countByTelegramUser_Id(userId) > 0
        val notificationsDone = progress?.notificationsViewedAt != null
        val resultsDone = progress?.resultsViewedAt != null
        val teamCta = firstOpenTeamPath(userId) ?: "/"
        val items = listOf(
            OnboardingChecklistItemDto(
                step = OnboardingStep.OPEN_PACK.name,
                title = "Откройте первый пак",
                description = "Получите карты, из которых можно собрать состав.",
                completed = packDone,
                ctaLabel = "В магазин",
                ctaPath = "/store",
            ),
            OnboardingChecklistItemDto(
                step = OnboardingStep.VIEW_COLLECTION.name,
                title = "Посмотрите коллекцию",
                description = "Оцените редкости, перки и оставшиеся использования карт.",
                completed = collectionDone,
                ctaLabel = "Коллекция",
                ctaPath = "/cards",
            ),
            OnboardingChecklistItemDto(
                step = OnboardingStep.SUBMIT_FIRST_TEAM.name,
                title = "Соберите команду",
                description = "Подайте состав на открытую серию, чтобы попасть в лидерборд.",
                completed = teamDone,
                ctaLabel = "Собрать",
                ctaPath = teamCta,
            ),
            OnboardingChecklistItemDto(
                step = OnboardingStep.OPEN_NOTIFICATIONS.name,
                title = "Проверьте уведомления",
                description = "Оставьте включёнными дедлайны, результаты и полезные подсказки.",
                completed = notificationsDone,
                ctaLabel = "Уведомления",
                ctaPath = "/notifications",
            ),
            OnboardingChecklistItemDto(
                step = OnboardingStep.VIEW_RESULTS.name,
                title = "Посмотрите результаты",
                description = "После игр проверьте очки, место и детализацию по картам.",
                completed = resultsDone,
                ctaLabel = "Рейтинг",
                ctaPath = "/rating",
            ),
        )
        val completed = items.count { it.completed }
        val primary = if (!teamDone && hasLiveCards(userId) && teamCta != "/") {
            items.first { it.step == OnboardingStep.SUBMIT_FIRST_TEAM.name }
        } else {
            items.firstOrNull { !it.completed }
        }
        return OnboardingChecklistDto(
            completedCount = completed,
            totalCount = items.size,
            primaryCta = primary,
            items = items,
        )
    }

    @Transactional
    fun markStep(userId: Long, step: OnboardingStep) {
        val now = Instant.now()
        val progress = ensureProgress(userId)
        var completedNow = false
        fun setOnce(current: Instant?, set: () -> Unit) {
            if (current == null) {
                set()
                completedNow = true
            }
        }
        when (step) {
            OnboardingStep.OPEN_STORE -> setOnce(progress.storeOpenedAt) { progress.storeOpenedAt = now }
            OnboardingStep.OPEN_PACK -> setOnce(progress.firstPackOpenedAt) { progress.firstPackOpenedAt = now }
            OnboardingStep.VIEW_COLLECTION -> setOnce(progress.collectionViewedAt) { progress.collectionViewedAt = now }
            OnboardingStep.SUBMIT_FIRST_TEAM -> setOnce(progress.firstTeamSubmittedAt) { progress.firstTeamSubmittedAt = now }
            OnboardingStep.OPEN_NOTIFICATIONS -> setOnce(progress.notificationsViewedAt) { progress.notificationsViewedAt = now }
            OnboardingStep.VIEW_RESULTS -> setOnce(progress.resultsViewedAt) { progress.resultsViewedAt = now }
        }
        progress.updatedAt = now
        onboardingProgressRepository.save(progress)
        if (completedNow) {
            productEventService.record(
                userId = userId,
                eventType = "ONBOARDING_STEP_COMPLETED",
                subjectType = step.name,
                source = "SERVER",
            )
        }
    }

    @Transactional
    fun markNudgeSent(userId: Long, type: OnboardingNudgeType) {
        val now = Instant.now()
        val progress = ensureProgress(userId)
        when (type) {
            OnboardingNudgeType.NO_ACTION -> progress.noActionNudgeSentAt = now
            OnboardingNudgeType.ACTION_NO_TEAM -> progress.actionNoTeamNudgeSentAt = now
            OnboardingNudgeType.OPEN_DEADLINE -> progress.openDeadlineNudgeSentAt = now
            OnboardingNudgeType.AFTER_FIRST_TEAM -> progress.afterFirstTeamNudgeSentAt = now
        }
        progress.lastNudgeSentAt = now
        progress.nudgeCount += 1
        progress.updatedAt = now
        onboardingProgressRepository.save(progress)
    }

    @Transactional(readOnly = true)
    fun firstOpenTeamPath(userId: Long): String? = firstOpenTeamTarget(userId)?.path

    @Transactional(readOnly = true)
    fun firstOpenTeamTarget(userId: Long): OpenTeamTarget? {
        val series = seriesRepository.findAllOpenForTeamSubmission(
            tournamentStatus = TournamentStatus.ACTIVE,
            finishedStatus = SeriesStatus.FINISHED,
            now = Instant.now(),
        )
        val firstMissing = series.firstOrNull { !fantasyTeamRepository.existsByTelegramUser_IdAndSeries_Id(userId, it.id!!) }
        return firstMissing?.let { OpenTeamTarget(path = "/series/${it.id}/team", deadline = it.teamDeadline) }
    }

    @Transactional(readOnly = true)
    fun hasLiveCards(userId: Long): Boolean = userCardRepository.countByTelegramUser_IdAndDeletedAtIsNull(userId) > 0

    private fun ensureProgress(userId: Long): OnboardingProgress {
        val existing = onboardingProgressRepository.findById(userId).orElse(null)
        if (existing != null) return existing
        val user = telegramUserRepository.findById(userId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "User $userId not found")
        }
        return onboardingProgressRepository.save(OnboardingProgress(telegramUser = user))
    }
}
