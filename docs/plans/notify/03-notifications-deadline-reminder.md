# План 3: Напоминание о дедлайне команды (TEAM_DEADLINE_REMINDER)

> **Предусловия:** План 1 (NotificationDeliveryService, таблица `deadline_reminder`), План 2 (NotificationCategory.TEAM_DEADLINE_REMINDER, tournament_subscription)  
> **Результат:** планировщик напоминаний за 1 час до дедлайна подачи команды; автоматический upsert при создании/обновлении серии  
> **Дизайн-документ:** §12 (TEAM_DEADLINE_REMINDER)

---

## Шаги

### 1. JPA-сущность: `DeadlineReminder`

**Файл:** `entity/DeadlineReminder.kt` (новый)

```kotlin
@Entity
@Table(name = "deadline_reminder")
class DeadlineReminder(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "series_id", nullable = false)
    var series: Series? = null,

    @Column(name = "remind_at", nullable = false)
    var remindAt: Instant,

    @Column(nullable = false)
    var sent: Boolean = false,

    @Column(name = "sent_at")
    var sentAt: Instant? = null,

    @Column(name = "recipient_count")
    var recipientCount: Int? = null,
)
```

### 2. Repository: `DeadlineReminderRepository`

**Файл:** `repository/DeadlineReminderRepository.kt` (новый)

```kotlin
interface DeadlineReminderRepository : JpaRepository<DeadlineReminder, Long> {
    fun findBySeries_Id(seriesId: Long): DeadlineReminder?
    fun findAllByRemindAtBeforeAndSentIsFalse(now: Instant): List<DeadlineReminder>
}
```

### 3. Upsert при создании/обновлении серии

**Файл:** `service/SeriesService.kt`

Вынести в отдельный метод, вызывать из `createSeries` и `updateSeries`:

```kotlin
private fun upsertDeadlineReminder(series: Series) {
    val teamDeadline = series.teamDeadline ?: return
    val remindAt = teamDeadline.minus(1, ChronoUnit.HOURS)

    val existing = deadlineReminderRepository.findBySeries_Id(series.id!!)
    if (existing != null) {
        existing.remindAt = remindAt
        if (remindAt.isAfter(Instant.now()) && existing.sent) {
            existing.sent = false
            existing.sentAt = null
            existing.recipientCount = null
        }
        deadlineReminderRepository.save(existing)
    } else {
        deadlineReminderRepository.save(
            DeadlineReminder(series = series, remindAt = remindAt)
        )
    }
}
```

Вызов: в конце `createSeries` и `updateSeries`, после сохранения серии.

Edge cases:
- `teamDeadline = null` → не создавать reminder
- Дедлайн перенесён на будущее после отправки → сброс `sent = false`
- Дедлайн перенесён на время < 1 часа от текущего → `remindAt` в прошлом, шедулер обработает при следующем тике

### 4. Определение получателей

**Файл:** `service/DeadlineReminderService.kt` (новый)

Получатели — пользователи, для которых:
1. `bot_blocked = false`
2. `TEAM_DEADLINE_REMINDER` включён (preference check)
3. **Нет** команды в этой серии (нет `fantasy_team` для `(user, series)`)
4. Подписаны на турнир серии (или нет подписок — получают всё)

Кастомный запрос в репозитории (JPQL или native SQL):

```kotlin
@Query(nativeQuery = true, value = """
    SELECT DISTINCT tu.telegram_id
    FROM telegram_user tu
    WHERE tu.bot_blocked = FALSE
      AND NOT EXISTS (
          SELECT 1 FROM notification_preference np
          WHERE np.telegram_user_id = tu.id
            AND np.category = 'TEAM_DEADLINE_REMINDER'
            AND np.enabled = FALSE
      )
      AND NOT EXISTS (
          SELECT 1 FROM fantasy_team ft
          WHERE ft.telegram_user_id = tu.id AND ft.series_id = :seriesId
      )
      AND (
          NOT EXISTS (
              SELECT 1 FROM tournament_subscription ts WHERE ts.telegram_user_id = tu.id
          )
          OR EXISTS (
              SELECT 1 FROM tournament_subscription ts
              WHERE ts.telegram_user_id = tu.id AND ts.tournament_id = :tournamentId
          )
      )
""")
fun findDeadlineReminderRecipients(seriesId: Long, tournamentId: Long): List<Long>
```

Разместить в `TelegramUserRepository` или в отдельном `NotificationRecipientRepository`.

### 5. `DeadlineReminderService`

**Файл:** `service/DeadlineReminderService.kt` (новый)

```kotlin
@Service
class DeadlineReminderService(
    private val deadlineReminderRepository: DeadlineReminderRepository,
    private val notificationDeliveryService: NotificationDeliveryService,
    private val notificationButtonFactory: NotificationButtonFactory,
    private val telegramUserRepository: TelegramUserRepository,
    private val seriesRepository: SeriesRepository,
) {
    @Transactional
    fun sendReminder(reminder: DeadlineReminder) {
        val series = reminder.series!!
        if (series.status == SeriesStatus.FINISHED || series.status == SeriesStatus.SCORING) {
            markSentWithoutSending(reminder)
            return
        }
        val recipients = telegramUserRepository
            .findDeadlineReminderRecipients(series.id!!, series.tournament!!.id!!)
        if (recipients.isEmpty()) {
            markSentWithoutSending(reminder)
            return
        }

        val text = buildReminderMessage(series)
        val replyMarkup = notificationButtonFactory.submitTeamButton(series.id!!)

        var sentCount = 0
        for (chatId in recipients) {
            val delivered = notificationDeliveryService.deliver(
                telegramChatId = chatId,
                category = NotificationCategory.TEAM_DEADLINE_REMINDER,
                text = text,
                replyMarkup = replyMarkup,
            )
            if (delivered) sentCount++
        }

        reminder.sent = true
        reminder.sentAt = Instant.now()
        reminder.recipientCount = sentCount
        deadlineReminderRepository.save(reminder)
    }

    private fun markSentWithoutSending(reminder: DeadlineReminder) {
        reminder.sent = true
        reminder.sentAt = Instant.now()
        reminder.recipientCount = 0
        deadlineReminderRepository.save(reminder)
    }
}
```

### 6. Планировщик: `DeadlineReminderScheduler`

**Файл:** `scheduler/DeadlineReminderScheduler.kt` (новый)

```kotlin
@Component
class DeadlineReminderScheduler(
    private val deadlineReminderRepository: DeadlineReminderRepository,
    private val deadlineReminderService: DeadlineReminderService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedRate = 60_000)
    fun processReminders() {
        val pending = deadlineReminderRepository
            .findAllByRemindAtBeforeAndSentIsFalse(Instant.now())
        for (reminder in pending) {
            try {
                deadlineReminderService.sendReminder(reminder)
            } catch (e: Exception) {
                log.error("Failed to process deadline reminder id={}", reminder.id, e)
            }
        }
    }
}
```

Период — 1 минута. Достаточная точность для напоминания за час.

Убедиться, что `@EnableScheduling` уже включён в конфигурации приложения (или добавить).

### 7. Формат сообщения

**Файл:** `service/DeadlineReminderService.kt`

```kotlin
private fun buildReminderMessage(series: Series): String = buildString {
    append("⏰ Через час истекает дедлайн подачи команды!\n\n")
    append("Серия: ${series.tournament!!.name} — ${series.name}\n")
    series.teamDeadline?.let { deadline ->
        val formatted = DateTimeFormatter
            .ofPattern("d MMMM yyyy, HH:mm", Locale("ru"))
            .withZone(ZoneId.of("Europe/Moscow"))
            .format(deadline)
        append("Дедлайн: $formatted МСК\n")
    }
    append("\nУ вас ещё нет команды в этой серии.")
}
```

Кнопка: «📝 Подать команду» → `/series/{id}/team`.

---

## Проверка готовности

- [ ] При создании серии с `teamDeadline` — создаётся `deadline_reminder` с `remind_at = teamDeadline - 1h`
- [ ] При обновлении `teamDeadline` — обновляется `remind_at`
- [ ] При переносе дедлайна в будущее после отправки — `sent` сбрасывается
- [ ] Шедулер (каждые 60с) находит pending reminders и отправляет
- [ ] Получатели: нет команды в серии + `TEAM_DEADLINE_REMINDER` включён + `bot_blocked = false` + подписка на турнир (или нет подписок)
- [ ] Серия в статусе `FINISHED`/`SCORING` — reminder помечается `sent` без отправки
- [ ] Кнопка «Подать команду» ведёт на правильный URL в TMA
- [ ] Рестарт сервера: пропущенные reminder'ы обрабатываются при первом тике
