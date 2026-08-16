package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.entity.NotificationCategory
import io.github.mralex1810.fantasy.entity.OnboardingNudgeType
import io.github.mralex1810.fantasy.telegram.NotificationButtonFactory
import jakarta.persistence.EntityManager
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class OnboardingNudgeCandidate(
    val userId: Long,
    val telegramId: Long,
    val seriesId: Long? = null,
)

@Service
class OnboardingNudgeService(
    private val entityManager: EntityManager,
    private val notificationDeliveryService: NotificationDeliveryService,
    private val notificationButtonFactory: NotificationButtonFactory,
    private val onboardingService: OnboardingService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun sendDueNudges() {
        send(NO_ACTION_TEXT, OnboardingNudgeType.NO_ACTION, candidates(NO_ACTION_SQL)) {
            notificationButtonFactory.singleButton("Открыть магазин", "/store")
        }
        send(ACTION_NO_TEAM_TEXT, OnboardingNudgeType.ACTION_NO_TEAM, candidates(ACTION_NO_TEAM_SQL)) { candidate ->
            val path = candidate.seriesId?.let { "/series/$it/team" } ?: "/"
            notificationButtonFactory.singleButton("Собрать команду", path)
        }
        send(OPEN_DEADLINE_TEXT, OnboardingNudgeType.OPEN_DEADLINE, candidates(OPEN_DEADLINE_SQL)) { candidate ->
            val path = candidate.seriesId?.let { "/series/$it/team" } ?: "/"
            notificationButtonFactory.singleButton("Подать состав", path)
        }
        send(AFTER_FIRST_TEAM_TEXT, OnboardingNudgeType.AFTER_FIRST_TEAM, candidates(AFTER_FIRST_TEAM_SQL)) { candidate ->
            candidate.seriesId?.let(notificationButtonFactory::openSeriesLeaderboardButton)
                ?: notificationButtonFactory.singleButton("Где смотреть результаты", "/help#scoring")
        }
    }

    private fun send(
        text: String,
        type: OnboardingNudgeType,
        candidates: List<OnboardingNudgeCandidate>,
        button: (OnboardingNudgeCandidate) -> io.github.mralex1810.fantasy.telegram.InlineKeyboardMarkup,
    ) {
        for (candidate in candidates) {
            try {
                val delivered = notificationDeliveryService.deliver(
                    telegramChatId = candidate.telegramId,
                    category = NotificationCategory.ONBOARDING_TIPS,
                    text = text,
                    replyMarkup = button(candidate),
                )
                if (delivered) {
                    onboardingService.markNudgeSent(candidate.userId, type)
                }
            } catch (e: Exception) {
                log.warn("Failed to send onboarding nudge {}", type, e)
            }
        }
    }

    @Transactional(readOnly = true)
    fun candidates(sql: String): List<OnboardingNudgeCandidate> =
        entityManager.createNativeQuery(sql)
            .setMaxResults(50)
            .resultList
            .map {
                val row = it as Array<*>
                OnboardingNudgeCandidate(
                    userId = (row[0] as Number).toLong(),
                    telegramId = (row[1] as Number).toLong(),
                    seriesId = (row.getOrNull(2) as? Number)?.toLong(),
                )
            }

    companion object {
        private const val BASE_GUARD = """
            u.bot_blocked = FALSE
            AND NOT EXISTS (
              SELECT 1 FROM notification_preference np
              WHERE np.telegram_user_id = u.id
                AND np.category = 'ONBOARDING_TIPS'
                AND np.enabled = FALSE
            )
            AND COALESCE(op.nudge_count, 0) < 3
            AND (op.last_nudge_sent_at IS NULL OR op.last_nudge_sent_at < now() - interval '1 day')
        """

        private const val ACTION_EXISTS = """
            EXISTS (
              SELECT 1 FROM fantasy_team ft WHERE ft.telegram_user_id = u.id
              UNION ALL SELECT 1 FROM user_card_ownership_history h WHERE h.telegram_user_id = u.id AND h.acquisition_type = 'PACK_OPENING'
              UNION ALL SELECT 1 FROM fantiki_transaction f WHERE f.telegram_user_id = u.id
                AND f.reason IN ('PACK_PURCHASE','CARD_RECYCLE','CARD_RENEWAL','LEGENDARY_UPGRADE','MARKETPLACE_PURCHASE','MARKETPLACE_SALE','MARKETPLACE_COMPLAINT_REWARD')
              UNION ALL SELECT 1 FROM marketplace_listing ml WHERE ml.seller_id = u.id
              UNION ALL SELECT 1 FROM marketplace_listing ml WHERE ml.buyer_id = u.id
            )
        """

        private const val NO_ACTION_SQL = """
            SELECT u.id, u.telegram_id, NULL::bigint
            FROM telegram_user u
            LEFT JOIN onboarding_progress op ON op.telegram_user_id = u.id
            WHERE $BASE_GUARD
              AND op.no_action_nudge_sent_at IS NULL
              AND u.created_at < now() - interval '24 hours'
              AND NOT ($ACTION_EXISTS)
            ORDER BY u.created_at
        """

        private const val ACTION_NO_TEAM_SQL = """
            SELECT u.id, u.telegram_id, NULL::bigint
            FROM telegram_user u
            LEFT JOIN onboarding_progress op ON op.telegram_user_id = u.id
            WHERE $BASE_GUARD
              AND op.action_no_team_nudge_sent_at IS NULL
              AND ($ACTION_EXISTS)
              AND NOT EXISTS (SELECT 1 FROM fantasy_team ft WHERE ft.telegram_user_id = u.id)
              AND EXISTS (SELECT 1 FROM user_card uc WHERE uc.telegram_user_id = u.id AND uc.deleted_at IS NULL)
            ORDER BY u.created_at
        """

        private const val OPEN_DEADLINE_SQL = """
            SELECT u.id, u.telegram_id, MIN(s.id)::bigint
            FROM telegram_user u
            CROSS JOIN series s
            INNER JOIN tournament t ON t.id = s.tournament_id
            LEFT JOIN onboarding_progress op ON op.telegram_user_id = u.id
            WHERE $BASE_GUARD
              AND op.open_deadline_nudge_sent_at IS NULL
              AND t.status = 'ACTIVE'
              AND s.status <> 'FINISHED'
              AND s.team_deadline > now()
              AND EXISTS (SELECT 1 FROM user_card uc WHERE uc.telegram_user_id = u.id AND uc.deleted_at IS NULL)
              AND NOT EXISTS (SELECT 1 FROM fantasy_team ft WHERE ft.telegram_user_id = u.id AND ft.series_id = s.id)
            GROUP BY u.id, u.telegram_id, u.created_at
            ORDER BY u.created_at
        """

        private const val AFTER_FIRST_TEAM_SQL = """
            SELECT u.id, u.telegram_id, MIN(ft.series_id)::bigint
            FROM telegram_user u
            INNER JOIN fantasy_team ft ON ft.telegram_user_id = u.id
            LEFT JOIN onboarding_progress op ON op.telegram_user_id = u.id
            WHERE $BASE_GUARD
              AND op.after_first_team_nudge_sent_at IS NULL
              AND ft.submitted_at > now() - interval '3 days'
            GROUP BY u.id, u.telegram_id, u.created_at
            HAVING COUNT(ft.id) >= 1
            ORDER BY MIN(ft.submitted_at)
        """

        private const val NO_ACTION_TEXT =
            "У вас уже есть доступ к Polemica Fantasy. Начните с первого пака: карты нужны, чтобы собрать команду на серию."

        private const val ACTION_NO_TEAM_TEXT =
            "У вас уже есть карты, но команда ещё не подана. Соберите 1-3 карты на открытую серию, чтобы попасть в лидерборд."

        private const val OPEN_DEADLINE_TEXT =
            "Открыт дедлайн состава на серию. Если хотите участвовать в лидерборде, подайте команду до окончания дедлайна."

        private const val AFTER_FIRST_TEAM_TEXT =
            "Команда подана. После синка игр смотрите очки в лидерборде серии и детализацию по каждой карте."
    }
}
