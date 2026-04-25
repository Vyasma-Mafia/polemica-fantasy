# План 2: User API настроек + уведомление о старте серии

> **Предусловия:** План 1 (NotificationDeliveryService, entities, миграция)  
> **Результат:** REST API настроек уведомлений и подписок на турниры; admin batch-start эндпоинт; уведомление о старте серии  
> **Дизайн-документ:** §4 (Настройки), §6 (Старт серии), §8 (User API), §9 (Admin API batch start)

---

## Шаги

### 1. DTO настроек уведомлений

**Файл:** `dto/user/response/NotificationSettingsDtos.kt` (новый)

```kotlin
data class NotificationCategoryDto(
    val category: String,
    val enabled: Boolean,
    val toggleable: Boolean,
    val description: String,
)

data class NotificationSettingsResponse(
    val categories: List<NotificationCategoryDto>,
)
```

**Файл:** `dto/user/request/NotificationSettingsRequest.kt` (новый)

```kotlin
data class UpdateNotificationSettingsRequest(
    val categories: Map<String, Boolean>,
)
```

### 2. Описания категорий

**Файл:** `entity/NotificationCategory.kt`

Добавить поле `description` в enum:

```kotlin
enum class NotificationCategory(
    val userToggleable: Boolean,
    val enabledByDefault: Boolean,
    val description: String,
) {
    ADMIN_BROADCAST(false, true, "Сообщения от администрации"),
    SERIES_START(true, true, "Уведомления о старте серии"),
    TEAM_DEADLINE_REMINDER(true, true, "Напоминание о дедлайне команды"),
    SERIES_FINALIZED(true, true, "Результаты серии"),
    SERIES_ROSTER_CHANGE(true, true, "Замена карт в составе серии"),
    MARKETPLACE_SALE(true, true, "Продажа вашей карты на маркетплейсе"),
    MARKETPLACE_WATCH(true, true, "Отслеживание карт на маркетплейсе"),
    PAIR_BAN(false, true, "Уведомления о санкциях"),
}
```

### 3. `NotificationSettingsService`

**Файл:** `service/NotificationSettingsService.kt` (новый)

```kotlin
@Service
class NotificationSettingsService(
    private val notificationPreferenceRepository: NotificationPreferenceRepository,
    private val telegramUserRepository: TelegramUserRepository,
) {
    @Transactional(readOnly = true)
    fun getSettings(internalUserId: Long): NotificationSettingsResponse {
        val overrides = notificationPreferenceRepository
            .findAllByTelegramUser_Id(internalUserId)
            .associateBy { it.category }
        val categories = NotificationCategory.entries.map { cat ->
            NotificationCategoryDto(
                category = cat.name,
                enabled = overrides[cat]?.enabled ?: cat.enabledByDefault,
                toggleable = cat.userToggleable,
                description = cat.description,
            )
        }
        return NotificationSettingsResponse(categories)
    }

    @Transactional
    fun updateSettings(
        internalUserId: Long,
        request: UpdateNotificationSettingsRequest,
    ): NotificationSettingsResponse {
        val user = telegramUserRepository.findById(internalUserId).orElseThrow()
        for ((categoryName, enabled) in request.categories) {
            val cat = NotificationCategory.valueOf(categoryName)
            if (!cat.userToggleable) continue  // игнорировать неотключаемые
            val existing = notificationPreferenceRepository
                .findByTelegramUser_IdAndCategory(internalUserId, cat)
            if (existing != null) {
                existing.enabled = enabled
                notificationPreferenceRepository.save(existing)
            } else if (enabled != cat.enabledByDefault) {
                notificationPreferenceRepository.save(
                    NotificationPreference(
                        telegramUser = user,
                        category = cat,
                        enabled = enabled,
                    )
                )
            }
        }
        return getSettings(internalUserId)
    }
}
```

### 4. DTO подписок на турниры

**Файл:** `dto/user/response/TournamentSubscriptionDtos.kt` (новый)

```kotlin
data class TournamentSubscriptionEntry(
    val tournamentId: Long,
    val tournamentName: String,
    val subscribed: Boolean,
)

data class TournamentSubscriptionsResponse(
    val subscriptions: List<TournamentSubscriptionEntry>,
    val availableTournaments: List<TournamentSubscriptionEntry>,
)
```

**Файл:** `dto/user/request/TournamentSubscriptionsRequest.kt` (новый)

```kotlin
data class UpdateTournamentSubscriptionsRequest(
    val tournamentIds: List<Long>,
)
```

### 5. `TournamentSubscriptionService`

**Файл:** `service/TournamentSubscriptionService.kt` (новый)

```kotlin
@Service
class TournamentSubscriptionService(
    private val tournamentSubscriptionRepository: TournamentSubscriptionRepository,
    private val tournamentRepository: TournamentRepository,
    private val telegramUserRepository: TelegramUserRepository,
) {
    @Transactional(readOnly = true)
    fun getSubscriptions(internalUserId: Long): TournamentSubscriptionsResponse {
        val subs = tournamentSubscriptionRepository
            .findAllByTelegramUser_Id(internalUserId)
            .map { it.tournament!!.id!! }
            .toSet()
        val tournaments = tournamentRepository.findAllActive()  // или аналогичный метод
        return TournamentSubscriptionsResponse(
            subscriptions = tournaments.filter { it.id!! in subs }.map { /* ... */ },
            availableTournaments = tournaments.map {
                TournamentSubscriptionEntry(it.id!!, it.name, it.id!! in subs)
            },
        )
    }

    @Transactional
    fun updateSubscriptions(
        internalUserId: Long,
        request: UpdateTournamentSubscriptionsRequest,
    ): TournamentSubscriptionsResponse {
        val user = telegramUserRepository.findById(internalUserId).orElseThrow()
        tournamentSubscriptionRepository.deleteAllByTelegramUser_Id(internalUserId)
        for (tournamentId in request.tournamentIds) {
            val tournament = tournamentRepository.findById(tournamentId).orElseThrow()
            tournamentSubscriptionRepository.save(
                TournamentSubscription(telegramUser = user, tournament = tournament)
            )
        }
        return getSubscriptions(internalUserId)
    }
}
```

Пустой `tournamentIds = []` → все подписки удалены → пользователь подписан на всё (дефолт).

### 6. Controller: настройки уведомлений

**Файл:** `controller/user/NotificationSettingsController.kt` (новый)

```kotlin
@RestController
@RequestMapping("/api/v1/settings")
class NotificationSettingsController(
    private val notificationSettingsService: NotificationSettingsService,
    private val tournamentSubscriptionService: TournamentSubscriptionService,
) {
    @GetMapping("/notifications")
    fun getNotifications(@AuthenticationPrincipal user: TelegramUser) =
        notificationSettingsService.getSettings(user.id!!)

    @PutMapping("/notifications")
    fun updateNotifications(
        @AuthenticationPrincipal user: TelegramUser,
        @RequestBody request: UpdateNotificationSettingsRequest,
    ) = notificationSettingsService.updateSettings(user.id!!, request)

    @GetMapping("/tournament-subscriptions")
    fun getTournamentSubscriptions(@AuthenticationPrincipal user: TelegramUser) =
        tournamentSubscriptionService.getSubscriptions(user.id!!)

    @PutMapping("/tournament-subscriptions")
    fun updateTournamentSubscriptions(
        @AuthenticationPrincipal user: TelegramUser,
        @RequestBody request: UpdateTournamentSubscriptionsRequest,
    ) = tournamentSubscriptionService.updateSubscriptions(user.id!!, request)
}
```

### 7. Event: `SeriesBatchStartedEvent`

**Файл:** `event/SeriesBatchStartedEvent.kt` (новый)

```kotlin
data class SeriesBatchStartedEvent(
    val startedSeries: List<StartedSeriesInfo>,
)

data class StartedSeriesInfo(
    val seriesId: Long,
    val seriesName: String,
    val tournamentId: Long,
    val tournamentName: String,
    val teamDeadline: Instant?,
)
```

### 8. Admin эндпоинт: `POST /api/v1/admin/series/batch-start`

**Файл:** `controller/admin/SeriesAdminController.kt`

Добавить метод:

```kotlin
@PostMapping("/series/batch-start")
fun batchStartSeries(@RequestBody request: BatchStartSeriesRequest): BatchStartSeriesResponse {
    return seriesService.batchStartSeries(request.seriesIds)
}
```

**Файл:** `dto/admin/request/SeriesRequests.kt`

```kotlin
data class BatchStartSeriesRequest(val seriesIds: List<Long>)
```

**Файл:** `dto/admin/response/BatchStartSeriesResponse.kt` (новый)

```kotlin
data class BatchStartSeriesResponse(
    val startedSeries: List<StartedSeriesEntry>,
    val skipped: List<SkippedSeriesEntry>,
    val notificationRecipientCount: Int,
)

data class StartedSeriesEntry(
    val seriesId: Long,
    val name: String,
    val tournamentName: String,
    val previousStatus: String,
)

data class SkippedSeriesEntry(
    val seriesId: Long,
    val reason: String,
)
```

### 9. Логика batch-start в `SeriesService`

**Файл:** `service/SeriesService.kt`

Новый метод:

```kotlin
@Transactional
fun batchStartSeries(seriesIds: List<Long>): BatchStartSeriesResponse {
    val started = mutableListOf<StartedSeriesEntry>()
    val skipped = mutableListOf<SkippedSeriesEntry>()
    val startedInfos = mutableListOf<StartedSeriesInfo>()

    for (seriesId in seriesIds) {
        val series = seriesRepository.findById(seriesId).orElse(null)
        if (series == null) {
            skipped.add(SkippedSeriesEntry(seriesId, "Not found"))
            continue
        }
        if (series.status != SeriesStatus.UPCOMING) {
            skipped.add(SkippedSeriesEntry(seriesId, "Already ${series.status}"))
            continue
        }
        val prevStatus = series.status
        series.status = SeriesStatus.ACTIVE
        seriesRepository.save(series)

        started.add(StartedSeriesEntry(seriesId, series.name, series.tournament!!.name, prevStatus.name))
        startedInfos.add(StartedSeriesInfo(
            seriesId = seriesId,
            seriesName = series.name,
            tournamentId = series.tournament!!.id!!,
            tournamentName = series.tournament!!.name,
            teamDeadline = series.teamDeadline,
        ))
    }

    if (startedInfos.isNotEmpty()) {
        applicationEventPublisher.publishEvent(SeriesBatchStartedEvent(startedInfos))
    }

    return BatchStartSeriesResponse(started, skipped, notificationRecipientCount = 0)
    // recipientCount заполняется позже (async listener подсчитывает)
}
```

### 10. Интеграция с одиночным стартом серии

**Файл:** `service/SeriesService.kt`

В `updateSeries` — при переходе `UPCOMING → ACTIVE` публиковать `SeriesBatchStartedEvent` с одной серией:

```kotlin
if (request.status == SeriesStatus.ACTIVE && previousStatus == SeriesStatus.UPCOMING) {
    applicationEventPublisher.publishEvent(SeriesBatchStartedEvent(listOf(
        StartedSeriesInfo(
            seriesId = s.id!!,
            seriesName = s.name,
            tournamentId = s.tournament!!.id!!,
            tournamentName = s.tournament!!.name,
            teamDeadline = s.teamDeadline,
        ),
    )))
}
```

Сохранить `previousStatus` до применения `request.status`.

### 11. Listener: `SeriesStartNotificationListener`

**Файл:** `event/SeriesStartNotificationListener.kt` (новый)

```kotlin
@Component
class SeriesStartNotificationListener(
    private val notificationDeliveryService: NotificationDeliveryService,
    private val notificationButtonFactory: NotificationButtonFactory,
    private val telegramUserRepository: TelegramUserRepository,
    private val notificationPreferenceRepository: NotificationPreferenceRepository,
    private val tournamentSubscriptionRepository: TournamentSubscriptionRepository,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    fun onSeriesBatchStarted(event: SeriesBatchStartedEvent) {
        val tournamentIds = event.startedSeries.map { it.tournamentId }.toSet()
        val recipients = findRecipients(tournamentIds)
        for (recipient in recipients) {
            val relevantSeries = filterByUserSubscriptions(recipient, event.startedSeries)
            if (relevantSeries.isEmpty()) continue
            val text = buildStartMessage(relevantSeries)
            val replyMarkup = if (relevantSeries.size == 1) {
                notificationButtonFactory.submitTeamButton(relevantSeries.first().seriesId)
            } else null
            notificationDeliveryService.deliver(
                telegramChatId = recipient.telegramId,
                category = NotificationCategory.SERIES_START,
                text = text,
                replyMarkup = replyMarkup,
            )
        }
    }
}
```

Определение получателей — SQL-запрос из §6.4 дизайн-документа (проверка `bot_blocked`, preference `SERIES_START`, `tournament_subscription`).

### 12. Формат сообщений о старте серии

**Файл:** `event/SeriesStartNotificationListener.kt`

Одна серия:
```
🏁 Серия «{name}» началась!
Дедлайн подачи команды: {deadline}.
```

Несколько серий (batch):
```
🏁 Начались новые серии:

• {name1} (дедлайн: {deadline1})
• {name2} (дедлайн: {deadline2})

Подавайте составы до дедлайна!
```

---

## Проверка готовности

- [ ] `GET /api/v1/settings/notifications` — возвращает все 8 категорий с дефолтными значениями
- [ ] `PUT /api/v1/settings/notifications` — toggleable категории переключаются, неотключаемые (`ADMIN_BROADCAST`, `PAIR_BAN`) игнорируются
- [ ] `GET /api/v1/settings/tournament-subscriptions` — возвращает доступные турниры и подписки
- [ ] `PUT /api/v1/settings/tournament-subscriptions` — полная замена подписок; пустой список = подписан на всё
- [ ] `POST /api/v1/admin/series/batch-start` — переводит серии UPCOMING → ACTIVE, skipped для неподходящих
- [ ] Одиночный `PUT /api/v1/admin/series/{id}` с `status = ACTIVE` — публикует тот же event
- [ ] Уведомление о старте: пользователь с подпиской на турнир получает; без подписок → получает всё
- [ ] Уведомление о старте: батч формирует одно сообщение
- [ ] Пользователь с `SERIES_START = false` → не получает уведомление
- [ ] Пользователь с `bot_blocked = true` → не получает уведомление
