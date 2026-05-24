package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.entity.ProductAudience
import io.github.mralex1810.fantasy.repository.FantasyTeamRepository
import io.github.mralex1810.fantasy.repository.TelegramUserRepository
import io.github.mralex1810.fantasy.repository.UserCardRepository
import io.github.mralex1810.fantasy.telegram.InlineKeyboardMarkup
import io.github.mralex1810.fantasy.telegram.NotificationButtonFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class TelegramStartMenu(
    val text: String,
    val replyMarkup: InlineKeyboardMarkup,
)

@Service
class TelegramStartMenuService(
    private val telegramUserRepository: TelegramUserRepository,
    private val userSegmentService: UserSegmentService,
    private val onboardingService: OnboardingService,
    private val fantasyTeamRepository: FantasyTeamRepository,
    private val userCardRepository: UserCardRepository,
    private val notificationButtonFactory: NotificationButtonFactory,
) {
    @Transactional(readOnly = true)
    fun build(telegramId: Long): TelegramStartMenu {
        val user = telegramUserRepository.findByTelegramId(telegramId)
            ?: return TelegramStartMenu(
                text = """
                    Polemica Fantasy — фэнтези по спортивной мафии.

                    Откройте мини-приложение, получите первые карты и соберите команду на ближайшую серию.
                """.trimIndent(),
                replyMarkup = notificationButtonFactory.startMenu(),
            )
        val userId = user.id!!
        val submitTeamTarget = onboardingService.firstOpenTeamTarget(userId)
        val submitTeamPath = submitTeamTarget?.path
        val hasCards = userCardRepository.countByTelegramUser_IdAndDeletedAtIsNull(userId) > 0
        val hasTeam = fantasyTeamRepository.countByTelegramUser_Id(userId) > 0
        val segment = userSegmentService.audienceForUser(userId)
        val text = when {
            submitTeamTarget != null && hasCards -> """
                Есть открытая серия для состава.

                Подайте команду до ${formatDeadlineTime(submitTeamTarget.deadline)}, чтобы попасть в лидерборд. Если не уверены, начните с основной лиги — состав можно менять до дедлайна.
            """.trimIndent()
            segment == ProductAudience.NEVER_ACTIVATED -> """
                Начните с первого пака.

                Откройте карты, зайдите в коллекцию и соберите команду на ближайшую серию.
            """.trimIndent()
            hasCards && !hasTeam -> """
                У вас уже есть карты для состава.

                Следующий шаг — выбрать 1-3 карты на серию. После игр бот пришлёт результаты, если уведомления включены.
            """.trimIndent()
            else -> """
                Polemica Fantasy: карты, составы, серии и лидерборды.

                Откройте приложение, чтобы проверить дедлайны, коллекцию и результаты.
            """.trimIndent()
        }
        return TelegramStartMenu(
            text = text,
            replyMarkup = notificationButtonFactory.startMenu(submitTeamPath),
        )
    }

    private fun formatDeadlineTime(deadline: java.time.Instant): String =
        DEADLINE_TIME.format(deadline.atZone(ZoneId.systemDefault()))

    companion object {
        private val DEADLINE_TIME = DateTimeFormatter.ofPattern("HH:mm")
    }
}
