# Система уведомлений

> **Статус:** Draft  
> **Файл:** [`docs/features/DESIGN-NOTIFICATIONS.md`](./DESIGN-NOTIFICATIONS.md)

---

## 1. Цели

1. **Пользовательские настройки уведомлений** — дать пользователю возможность управлять тем, какие уведомления он получает, и по каким сериям/турнирам.
2. **Уведомление о старте серии** — новый тип уведомления с возможностью подписки на конкретные турниры; батч-старт нескольких серий формирует одно сообщение.
3. **Устойчивость к блокировке бота** — перестать слать уведомления пользователям, заблокировавшим бота; не засорять логи.
4. **Неотключаемые уведомления** — административные сообщения всегда доставляются (для пользователей, не заблокировавших бота).

---

## 2. Текущее состояние (as-is)

### 2.1 Типы уведомлений

| # | Тип | Event class | Получатель | Отключаем? |
|---|-----|-------------|-----------|------------|
| 1 | Финализация серии | `SeriesFinalizedNotificationEvent` | Каждый участник серии (с fantasy team) | Сейчас нет |
| 2 | Рассылка от администратора | `AdminBroadcastNotificationService` | Все пользователи | Не должен быть |
| 3 | Продажа карты на маркетплейсе | `MarketplaceSaleNotificationEvent` | Продавец | Сейчас нет |
| 4 | Санкция за перелив (бан пары) | `PairBanNotificationEvent` | Нарушитель | Сейчас нет |
| 5 | Замена карты в составе серии | `SeriesRosterReplacementNotificationEvent` | Владелец изъятых карт | Сейчас нет |

### 2.2 Архитектурные паттерны

- **Доставка:** Telegram Bot API (`sendMessage`) через `TelegramBotApiClient` + Spring `RestClient`.
- **Асинхронность:** `@TransactionalEventListener(AFTER_COMMIT)` + `@Async` для domain events; `@Async` для broadcast.
- **Rate limiting:** broadcast — `Thread.sleep(50ms)` между сообщениями; domain events — без задержки.
- **Ошибки:** `try/catch` с `log.warn`; нет retry, нет отслеживания `403 Forbidden` (заблокированный бот).
- **Настройки:** единственный глобальный флаг `telegram.bot.notifications.enabled` (application.yml). Пользовательских настроек нет.

### 2.3 Проблемы

1. Нет пользовательских настроек — нельзя отключить назойливые уведомления.
2. Нет уведомления о старте серии.
3. Заблокированные пользователи генерируют `WARN` в логах на каждую отправку.
4. Broadcast идёт всем без исключения — при 1000+ пользователей это замедлит доставку.
5. Нет механизма подписки на турнир/серию — все получают всё.

---

## 3. Категории уведомлений (to-be)

Вводим перечисление `NotificationCategory`:

| Enum-значение | Описание | Отключаемо? | По умолчанию |
|---------------|----------|-------------|-------------|
| `ADMIN_BROADCAST` | Рассылка от администратора | **Нет** | Вкл |
| `SERIES_START` | Старт серии (новый тип) | Да | Вкл |
| `TEAM_DEADLINE_REMINDER` | Напоминание за час до дедлайна | Да | Вкл |
| `SERIES_FINALIZED` | Финализация серии (результаты) | Да | Вкл |
| `SERIES_ROSTER_CHANGE` | Замена карты в составе серии | Да | Вкл |
| `MARKETPLACE_SALE` | Продажа вашей карты | Да | Вкл |
| `MARKETPLACE_WATCH` | Появление отслеживаемой карты | Да | Вкл |
| `PAIR_BAN` | Санкция за нарушение | **Нет** | Вкл |

**Принцип:** административные и дисциплинарные уведомления нельзя отключить. Всё остальное — настраиваемо.

### 3.1 Обоснование неотключаемости `PAIR_BAN`

Санкция за перелив — это юридически значимое уведомление о наложении санкции. Пользователь должен знать, что к нему применены ограничения. Если разрешить отключение, пользователь может не узнать о конфискации фантиков и карт, что приведёт к жалобам.

---

## 4. Пользовательские настройки уведомлений

### 4.1 Модель данных

Новая таблица `notification_preference`:

```sql
CREATE TABLE notification_preference (
    id              BIGSERIAL PRIMARY KEY,
    telegram_user_id BIGINT NOT NULL REFERENCES telegram_user(id),
    category        VARCHAR(32) NOT NULL,    -- NotificationCategory enum
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_notif_pref_user_category UNIQUE (telegram_user_id, category)
);
```

**Логика:** если строки для пары `(user, category)` нет — используется дефолтное значение (включено). Таблица хранит только явные overrides.

### 4.2 Подписка на турниры (для SERIES_START)

Уведомление `SERIES_START` дополнительно фильтруется по турнирам. Новая таблица:

```sql
CREATE TABLE tournament_subscription (
    id              BIGSERIAL PRIMARY KEY,
    telegram_user_id BIGINT NOT NULL REFERENCES telegram_user(id),
    tournament_id   BIGINT NOT NULL REFERENCES tournament(id),
    CONSTRAINT uk_tournament_sub UNIQUE (telegram_user_id, tournament_id)
);
```

**Семантика:**
- Если пользователь отключил категорию `SERIES_START` — подписки на турниры игнорируются, уведомления не шлются.
- Если категория `SERIES_START` включена:
  - Есть хотя бы одна подписка → шлём только по подписанным турнирам.
  - Нет ни одной подписки → шлём по **всем** активным турнирам (обратная совместимость).

### 4.3 JPA-сущности

```kotlin
@Entity
@Table(name = "notification_preference")
class NotificationPreference(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "telegram_user_id", nullable = false)
    var telegramUser: TelegramUser? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var category: NotificationCategory,

    @Column(nullable = false)
    var enabled: Boolean = true,
)

@Entity
@Table(name = "tournament_subscription")
class TournamentSubscription(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "telegram_user_id", nullable = false)
    var telegramUser: TelegramUser? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tournament_id", nullable = false)
    var tournament: Tournament? = null,
)
```

### 4.4 Enum `NotificationCategory`

```kotlin
enum class NotificationCategory(
    val userToggleable: Boolean,
    val enabledByDefault: Boolean,
) {
    ADMIN_BROADCAST(userToggleable = false, enabledByDefault = true),
    SERIES_START(userToggleable = true, enabledByDefault = true),
    TEAM_DEADLINE_REMINDER(userToggleable = true, enabledByDefault = true),
    SERIES_FINALIZED(userToggleable = true, enabledByDefault = true),
    SERIES_ROSTER_CHANGE(userToggleable = true, enabledByDefault = true),
    MARKETPLACE_SALE(userToggleable = true, enabledByDefault = true),
    MARKETPLACE_WATCH(userToggleable = true, enabledByDefault = true),
    PAIR_BAN(userToggleable = false, enabledByDefault = true),
}
```

---

## 5. Отслеживание заблокированных ботов

### 5.1 Флаг `bot_blocked`

Добавить в `telegram_user`:

```sql
ALTER TABLE telegram_user ADD COLUMN bot_blocked BOOLEAN NOT NULL DEFAULT FALSE;
```

### 5.2 Логика обновления

**Установка `bot_blocked = true`:**
- При получении от Telegram API ответа с `error_code = 403` и `description` содержащим `"bot was blocked by the user"` или `"user is deactivated"` — обновляем `bot_blocked = true`.
- Делается **асинхронно** (fire-and-forget), чтобы не блокировать основной поток уведомлений.

**Сброс `bot_blocked = false`:**
- При любом успешном взаимодействии пользователя через TMA (Mini App) — пользователь открыл приложение, значит бот не заблокирован. Обновляем при `AuthService.authenticateOrCreate`.

### 5.3 Фильтрация при отправке

Все notification listeners и broadcast sender проверяют `bot_blocked` **до** вызова Telegram API:

```kotlin
// В NotificationDeliveryService (новый сервис)
fun shouldDeliver(telegramUserId: Long, category: NotificationCategory): Boolean {
    val user = telegramUserRepository.findById(telegramUserId) ?: return false
    if (user.botBlocked) return false
    if (!category.userToggleable) return true  // ADMIN_BROADCAST, PAIR_BAN
    return notificationPreferenceRepository
        .findByTelegramUserIdAndCategory(telegramUserId, category)
        ?.enabled
        ?: category.enabledByDefault
}
```

### 5.4 Парсинг ошибки Telegram

Расширяем `TelegramBotApiClient.sendMessage` или создаём обёртку:

```kotlin
sealed class TelegramSendResult {
    data object Success : TelegramSendResult()
    data class BotBlocked(val description: String) : TelegramSendResult()
    data class OtherError(val description: String) : TelegramSendResult()
}

fun sendMessageSafe(botToken: String, chatId: Long, text: String, ...): TelegramSendResult {
    val tree = apiPost(botToken, "sendMessage", body)
    if (tree.path("ok").asBoolean()) return TelegramSendResult.Success
    val desc = tree.path("description").asText("")
    val code = tree.path("error_code").asInt(0)
    if (code == 403) return TelegramSendResult.BotBlocked(desc)
    return TelegramSendResult.OtherError(desc)
}
```

---

## 6. Уведомление о старте серии

### 6.1 Триггер

Уведомление отправляется при переводе серии из `UPCOMING` в `ACTIVE` (через admin API `updateSeries` с `status = ACTIVE`).

### 6.2 Батч-старт

**Задача:** администратор может одновременно стартовать несколько серий из разных турниров одним действием, при этом пользователь получает **одно** агрегированное сообщение.

**Новый admin-эндпоинт:**

```
POST /api/v1/admin/series/batch-start
Body: { "seriesIds": [1, 2, 5] }
```

**Логика:**
1. Все указанные серии переводятся из `UPCOMING` → `ACTIVE` (или `DRAFT` → `ACTIVE` для серий, которые были в UPCOMING).
2. Публикуется **один** `SeriesBatchStartedEvent` с полным списком стартовавших серий.
3. Listener собирает подписчиков, группирует по пользователям и формирует одно сообщение на пользователя.

### 6.3 Event

```kotlin
data class SeriesBatchStartedEvent(
    val startedSeries: List<StartedSeriesInfo>,
)

data class StartedSeriesInfo(
    val seriesId: Long,
    val seriesName: String,
    val tournamentId: Long,
    val tournamentName: String,
    val teamDeadline: Instant,
)
```

### 6.4 Определение получателей

Получатели — пользователи с `SERIES_START` enabled И подпиской на соответствующий турнир (или без подписок вовсе, как описано в §4.2).

```sql
-- Пользователи, которым нужно отправить уведомление о старте серий из турниров :tournamentIds
SELECT DISTINCT tu.telegram_id
FROM telegram_user tu
WHERE tu.bot_blocked = FALSE
  AND NOT EXISTS (
      SELECT 1 FROM notification_preference np
      WHERE np.telegram_user_id = tu.id
        AND np.category = 'SERIES_START'
        AND np.enabled = FALSE
  )
  AND (
      -- Нет подписок → подписан на всё
      NOT EXISTS (
          SELECT 1 FROM tournament_subscription ts WHERE ts.telegram_user_id = tu.id
      )
      OR
      -- Есть подписка на хотя бы один из стартующих турниров
      EXISTS (
          SELECT 1 FROM tournament_subscription ts
          WHERE ts.telegram_user_id = tu.id AND ts.tournament_id IN (:tournamentIds)
      )
  );
```

### 6.5 Формат сообщения

**Одна серия:**
```
🏁 Серия «Кубок Полемики — Серия 5» началась!
Дедлайн подачи команды: 28 апреля 2026, 20:00 МСК.
```

**Несколько серий (batch):**
```
🏁 Начались новые серии:

• Кубок Полемики — Серия 5 (дедлайн: 28 апреля, 20:00)
• Летний турнир — Серия 1 (дедлайн: 29 апреля, 18:00)

Подавайте составы до дедлайна!
```

### 6.6 Обратная совместимость одиночного старта

При обновлении одной серии через `PUT /api/v1/admin/series/{id}` с `status = ACTIVE` (прежний API) также публикуется `SeriesBatchStartedEvent` с одной серией в списке. Это позволяет не дублировать логику.

---

## 7. Сервис доставки уведомлений (NotificationDeliveryService)

### 7.1 Назначение

Централизованный сервис, инкапсулирующий:
1. Проверку `bot_blocked`.
2. Проверку `notification_preference`.
3. Отправку через `TelegramBotApiClient`.
4. Обработку `403 Forbidden` → обновление `bot_blocked`.
5. Rate limiting (задержка между отправками).

### 7.2 Публичный API

```kotlin
@Service
class NotificationDeliveryService(
    private val telegramBotApiClient: TelegramBotApiClient,
    private val telegramProperties: TelegramProperties,
    private val telegramUserRepository: TelegramUserRepository,
    private val notificationPreferenceRepository: NotificationPreferenceRepository,
) {
    /**
     * Отправить уведомление одному пользователю.
     * Возвращает false, если не доставлено (отключено, бот заблокирован и т.д.)
     */
    fun deliver(
        telegramChatId: Long,
        category: NotificationCategory,
        text: String,
        parseMode: String? = null,
        replyMarkup: InlineKeyboardMarkup? = null,
    ): Boolean

    /**
     * Отправить одно уведомление списку пользователей.
     * Фильтрует по категории и bot_blocked. Rate-limited.
     */
    fun deliverToMany(
        recipients: List<Long>,  // telegram chat ids
        category: NotificationCategory,
        textProvider: (telegramChatId: Long) -> String,
        parseMode: String? = null,
        replyMarkup: InlineKeyboardMarkup? = null,
    ): DeliveryReport

    /**
     * Broadcast — без проверки категории (ADMIN_BROADCAST неотключаем).
     * Фильтрует только по bot_blocked.
     */
    fun broadcast(text: String, parseMode: String?): DeliveryReport
}

data class DeliveryReport(
    val sent: Int,
    val skippedBlocked: Int,
    val skippedPreference: Int,
    val failed: Int,
)
```

### 7.3 Миграция существующих listeners

Все существующие listeners (`SeriesFinalizedNotificationListener`, `MarketplaceSaleNotificationListener`, `PairBanNotificationListener`, `SeriesRosterReplacementNotificationListener`) и `TelegramBroadcastAsyncSender` переходят на `NotificationDeliveryService` вместо прямого вызова `TelegramBotApiClient`.

---

## 8. User API (настройки уведомлений)

### 8.1 Эндпоинты

```
GET  /api/v1/settings/notifications
PUT  /api/v1/settings/notifications
GET  /api/v1/settings/tournament-subscriptions
PUT  /api/v1/settings/tournament-subscriptions
```

### 8.2 GET /api/v1/settings/notifications

Возвращает текущие настройки пользователя (merged с дефолтами):

```json
{
  "categories": [
    {
      "category": "SERIES_START",
      "enabled": true,
      "toggleable": true,
      "description": "Уведомления о старте серии"
    },
    {
      "category": "SERIES_FINALIZED",
      "enabled": true,
      "toggleable": true,
      "description": "Результаты серии"
    },
    {
      "category": "SERIES_ROSTER_CHANGE",
      "enabled": false,
      "toggleable": true,
      "description": "Замена карт в составе серии"
    },
    {
      "category": "MARKETPLACE_SALE",
      "enabled": true,
      "toggleable": true,
      "description": "Продажа вашей карты на маркетплейсе"
    },
    {
      "category": "ADMIN_BROADCAST",
      "enabled": true,
      "toggleable": false,
      "description": "Сообщения от администрации"
    },
    {
      "category": "PAIR_BAN",
      "enabled": true,
      "toggleable": false,
      "description": "Уведомления о санкциях"
    }
  ]
}
```

### 8.3 PUT /api/v1/settings/notifications

```json
{
  "categories": {
    "SERIES_START": true,
    "SERIES_FINALIZED": false,
    "MARKETPLACE_SALE": true
  }
}
```

Сервер **игнорирует** попытки изменить `userToggleable = false` категории. Возвращает обновлённый список (как GET).

### 8.4 GET /api/v1/settings/tournament-subscriptions

```json
{
  "subscriptions": [
    { "tournamentId": 1, "tournamentName": "Кубок Полемики" },
    { "tournamentId": 3, "tournamentName": "Летний турнир" }
  ],
  "availableTournaments": [
    { "tournamentId": 1, "tournamentName": "Кубок Полемики", "subscribed": true },
    { "tournamentId": 2, "tournamentName": "Тренировочный", "subscribed": false },
    { "tournamentId": 3, "tournamentName": "Летний турнир", "subscribed": true }
  ]
}
```

### 8.5 PUT /api/v1/settings/tournament-subscriptions

```json
{
  "tournamentIds": [1, 3]
}
```

Полная замена: переданный список = новые подписки. Пустой список `[]` = подписан на всё (сброс в дефолт; все строки удаляются).

---

## 9. Admin API (batch start)

### 9.1 Новый эндпоинт

```
POST /api/v1/admin/series/batch-start
```

```json
{
  "seriesIds": [4, 7, 12]
}
```

**Ответ:**

```json
{
  "startedSeries": [
    { "seriesId": 4, "name": "Серия 5", "tournamentName": "Кубок Полемики", "previousStatus": "UPCOMING" },
    { "seriesId": 7, "name": "Серия 1", "tournamentName": "Летний турнир", "previousStatus": "UPCOMING" }
  ],
  "skipped": [
    { "seriesId": 12, "reason": "Already ACTIVE" }
  ],
  "notificationRecipientCount": 142
}
```

### 9.2 Валидация

- Серии должны быть в статусе `UPCOMING`. Серии в других статусах пропускаются (не ошибка).
- Каждая серия проходит ту же бизнес-валидацию, что и `updateSeries(status=ACTIVE)`.

---

## 10. Обработка ошибок и Rate Limiting

### 10.1 Telegram Rate Limits

Telegram рекомендует не более ~30 сообщений/сек для бота. Текущая задержка 50ms (~20 msg/sec) — приемлема.

Для `NotificationDeliveryService.deliverToMany` сохраняем стратегию:
- 50ms задержка между отправками.
- При получении `429 Too Many Requests` — `retry_after` из ответа Telegram + повторная отправка.

### 10.2 Уровни логирования

| Ситуация | Уровень | Действие |
|----------|---------|----------|
| Успешная доставка | `DEBUG` | — |
| `bot_blocked` skip | `DEBUG` | — |
| Preference skip | `DEBUG` | — |
| 403 Forbidden (первый раз) | `INFO` | Обновить `bot_blocked = true` |
| 403 Forbidden (повторно, `bot_blocked` уже true) | `DEBUG` | — |
| 429 Rate limited | `WARN` | Retry после `retry_after` |
| Другая ошибка | `WARN` | Логировать, продолжать |

---

## 11. Flyway-миграция

```sql
-- V34__notification_preferences.sql

-- 1. Пользовательские настройки категорий
CREATE TABLE notification_preference (
    id               BIGSERIAL PRIMARY KEY,
    telegram_user_id BIGINT NOT NULL REFERENCES telegram_user(id),
    category         VARCHAR(32) NOT NULL,
    enabled          BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_notif_pref_user_category UNIQUE (telegram_user_id, category)
);

-- 2. Подписки на турниры (для SERIES_START и TEAM_DEADLINE_REMINDER)
CREATE TABLE tournament_subscription (
    id               BIGSERIAL PRIMARY KEY,
    telegram_user_id BIGINT NOT NULL REFERENCES telegram_user(id),
    tournament_id    BIGINT NOT NULL REFERENCES tournament(id),
    CONSTRAINT uk_tournament_sub UNIQUE (telegram_user_id, tournament_id)
);

-- 3. Флаг заблокированного бота
ALTER TABLE telegram_user ADD COLUMN bot_blocked BOOLEAN NOT NULL DEFAULT FALSE;

-- 4. Очередь напоминаний о дедлайнах (§12)
CREATE TABLE deadline_reminder (
    id               BIGSERIAL PRIMARY KEY,
    series_id        BIGINT NOT NULL REFERENCES series(id),
    remind_at        TIMESTAMPTZ NOT NULL,
    sent             BOOLEAN NOT NULL DEFAULT FALSE,
    sent_at          TIMESTAMPTZ,
    recipient_count  INT,
    CONSTRAINT uk_deadline_reminder_series UNIQUE (series_id)
);

CREATE INDEX idx_deadline_reminder_pending ON deadline_reminder (remind_at)
    WHERE sent = FALSE;

-- 5. Фильтры отслеживания маркетплейса (§13)
CREATE TABLE marketplace_watch_filter (
    id                BIGSERIAL PRIMARY KEY,
    telegram_user_id  BIGINT NOT NULL REFERENCES telegram_user(id),
    fantasy_player_id BIGINT REFERENCES fantasy_player(id),
    tournament_id     BIGINT REFERENCES tournament(id),
    rarity            VARCHAR(32),
    max_price         BIGINT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_marketplace_watch UNIQUE (
        telegram_user_id, fantasy_player_id, tournament_id, rarity, max_price
    )
);

CREATE INDEX idx_marketplace_watch_player ON marketplace_watch_filter (fantasy_player_id)
    WHERE fantasy_player_id IS NOT NULL;

-- 6. Pending-очередь для батчинга watch-уведомлений (§13.6)
CREATE TABLE marketplace_watch_pending (
    id               BIGSERIAL PRIMARY KEY,
    telegram_user_id BIGINT NOT NULL REFERENCES telegram_user(id),
    listing_id       BIGINT NOT NULL REFERENCES marketplace_listing(id),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_mw_pending_user ON marketplace_watch_pending (telegram_user_id);
```

---

## 12. TEAM_DEADLINE_REMINDER — напоминание о дедлайне подачи команды

### 12.1 Суть

Уведомление за 1 час до `series.team_deadline` для пользователей, которые ещё **не подали** команду в эту серию. Самая востребованная фича — пользователи забывают подать состав.

### 12.2 Категория

```kotlin
TEAM_DEADLINE_REMINDER(userToggleable = true, enabledByDefault = true),
```

### 12.3 Модель: таблица очереди запланированных напоминаний

Для deadline reminder нужен **scheduled job**, а не event-driven listener. Серии создаются и обновляются администратором; дедлайны могут меняться. Нужна таблица-очередь, по которой шедулер определяет, кому и когда слать.

```sql
CREATE TABLE deadline_reminder (
    id               BIGSERIAL PRIMARY KEY,
    series_id        BIGINT NOT NULL REFERENCES series(id),
    remind_at        TIMESTAMPTZ NOT NULL,  -- team_deadline - 1 hour
    sent             BOOLEAN NOT NULL DEFAULT FALSE,
    sent_at          TIMESTAMPTZ,
    recipient_count  INT,
    CONSTRAINT uk_deadline_reminder_series UNIQUE (series_id)
);

CREATE INDEX idx_deadline_reminder_pending ON deadline_reminder (remind_at)
    WHERE sent = FALSE;
```

**Одна строка на серию.** При создании/обновлении серии — upsert `deadline_reminder` с `remind_at = team_deadline - 1 hour`. Если `team_deadline` изменился — обновляем `remind_at` (и сбрасываем `sent = false`, если новый дедлайн ещё не наступил).

### 12.4 Планировщик (`DeadlineReminderScheduler`)

```kotlin
@Component
class DeadlineReminderScheduler(
    private val deadlineReminderRepository: DeadlineReminderRepository,
    private val deadlineReminderService: DeadlineReminderService,
) {
    @Scheduled(fixedRate = 60_000) // каждую минуту
    fun processReminders() {
        val pending = deadlineReminderRepository
            .findAllByRemindAtBeforeAndSentIsFalse(Instant.now())
        for (reminder in pending) {
            deadlineReminderService.sendReminder(reminder)
        }
    }
}
```

Период проверки — **1 минута**. Достаточная точность для напоминания за час.

### 12.5 Определение получателей

Получатели — пользователи, которые:
1. `bot_blocked = false`
2. `TEAM_DEADLINE_REMINDER` включен (preference check)
3. **Не подали** команду в эту серию (нет строки в `fantasy_team` для `(user, series)`)
4. Подписаны на турнир (или нет подписок вовсе) — используем ту же логику `tournament_subscription`, что и для `SERIES_START`

```sql
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
  );
```

### 12.6 Формат сообщения

```
⏰ Через час истекает дедлайн подачи команды!

Серия: Кубок Полемики — Серия 5
Дедлайн: 28 апреля 2026, 20:00 МСК

У вас ещё нет команды в этой серии.

[Подать команду]  ← inline button, открывает TMA
```

### 12.7 Edge cases

| Ситуация | Поведение |
|----------|-----------|
| Дедлайн изменён на более раннее время (< 1 час) | `remind_at` пересчитывается; если уже в прошлом и `sent = false` — отправляется при ближайшем тике шедулера |
| Дедлайн изменён после отправки reminder | Если новый `remind_at` в будущем — сбрасываем `sent = false`, пошлём повторно |
| Серия отменена / `status = FINISHED` | Шедулер проверяет `series.status`; если серия `FINISHED` — помечаем reminder как `sent` без отправки |
| Пользователь подал команду после отправки reminder | Допустимо: reminder уже ушёл, пользователь увидит, что команда подана |

### 12.8 Почему не cron, а таблица-очередь

- У каждой серии **свой** дедлайн — нельзя вычислить один cron на все серии.
- Дедлайны **изменяются** — нужна персистентная очередь.
- При рестарте сервера — пропущенные напоминания обработаются при первом тике.
- Альтернатива (Spring `TaskScheduler` / Quartz) тяжелее и не нужна при текущем масштабе.

---

## 13. MARKETPLACE_WATCH — отслеживание появлений карт на маркетплейсе

### 13.1 Суть

Пользователь создаёт **фильтры** (watchlist). Когда на маркетплейсе появляется новый лот, совпадающий с хотя бы одним фильтром, пользователь получает уведомление.

### 13.2 Категория

```kotlin
MARKETPLACE_WATCH(userToggleable = true, enabledByDefault = true),
```

### 13.3 Модель данных

```sql
CREATE TABLE marketplace_watch_filter (
    id               BIGSERIAL PRIMARY KEY,
    telegram_user_id BIGINT NOT NULL REFERENCES telegram_user(id),

    -- Критерии фильтра (все nullable — NULL = "любой")
    fantasy_player_id BIGINT REFERENCES fantasy_player(id),
    tournament_id     BIGINT REFERENCES tournament(id),
    rarity            VARCHAR(32),   -- Rarity enum
    max_price         BIGINT,        -- максимальная цена, до которой интересно

    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uk_marketplace_watch UNIQUE (
        telegram_user_id, fantasy_player_id, tournament_id, rarity, max_price
    )
);

CREATE INDEX idx_marketplace_watch_player ON marketplace_watch_filter (fantasy_player_id)
    WHERE fantasy_player_id IS NOT NULL;
CREATE INDEX idx_marketplace_watch_rarity ON marketplace_watch_filter (rarity)
    WHERE rarity IS NOT NULL;
```

**Ограничения:**
- Максимум **10 фильтров** на пользователя (защита от злоупотреблений).
- Уникальный constraint предотвращает дубли.

### 13.4 JPA-сущность

```kotlin
@Entity
@Table(name = "marketplace_watch_filter")
class MarketplaceWatchFilter(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "telegram_user_id", nullable = false)
    var telegramUser: TelegramUser? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fantasy_player_id")
    var fantasyPlayer: FantasyPlayer? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tournament_id")
    var tournament: Tournament? = null,

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    var rarity: Rarity? = null,

    @Column(name = "max_price")
    var maxPrice: Long? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
)
```

### 13.5 Matching: когда появляется новый лот

При создании нового лота (`MarketplaceService.createListing`) после успешного сохранения публикуется event:

```kotlin
data class MarketplaceListingCreatedEvent(
    val listingId: Long,
    val sellerId: Long,
    val fantasyPlayerId: Long,
    val tournamentIds: List<Long>,  // турниры, в которых участвует этот игрок
    val rarity: Rarity,
    val price: Long,
    val playerName: String,
    val playerPhotoUrl: String?,
)
```

Listener выполняет matching:

```sql
SELECT DISTINCT mwf.telegram_user_id, tu.telegram_id
FROM marketplace_watch_filter mwf
JOIN telegram_user tu ON tu.id = mwf.telegram_user_id
WHERE tu.bot_blocked = FALSE
  AND mwf.telegram_user_id != :sellerId  -- не уведомлять продавца о своём лоте
  AND (mwf.fantasy_player_id IS NULL OR mwf.fantasy_player_id = :fantasyPlayerId)
  AND (mwf.rarity IS NULL OR mwf.rarity = :rarity)
  AND (mwf.max_price IS NULL OR mwf.max_price >= :price)
  AND (mwf.tournament_id IS NULL OR mwf.tournament_id IN (:tournamentIds))
  AND NOT EXISTS (
      SELECT 1 FROM notification_preference np
      WHERE np.telegram_user_id = mwf.telegram_user_id
        AND np.category = 'MARKETPLACE_WATCH'
        AND np.enabled = FALSE
  );
```

### 13.6 Антиспам: батчинг и дедупликация

Проблема: если на маркетплейс за минуту выставлено 20 карт — пользователю может прилететь 20 сообщений.

**Решение: батчинг с задержкой.**

1. При матчинге вместо немедленной отправки создаётся запись в in-memory очередь (или легковесную таблицу `marketplace_watch_pending`).
2. Шедулер раз в **5 минут** агрегирует pending уведомления по пользователю и отправляет одно сообщение.

```sql
CREATE TABLE marketplace_watch_pending (
    id               BIGSERIAL PRIMARY KEY,
    telegram_user_id BIGINT NOT NULL REFERENCES telegram_user(id),
    listing_id       BIGINT NOT NULL REFERENCES marketplace_listing(id),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

Шедулер:
```kotlin
@Scheduled(fixedRate = 300_000) // 5 минут
fun flushMarketplaceWatchNotifications() {
    val batches = marketplaceWatchPendingRepository.findAllGroupedByUser()
    for ((userId, listings) in batches) {
        sendBatchedWatchNotification(userId, listings)
        marketplaceWatchPendingRepository.deleteAllByTelegramUserId(userId)
    }
}
```

### 13.7 Формат сообщения

**Одна карта:**
```
🔔 На маркетплейсе появилась карта из вашего отслеживания:

Игрок: Иванов Иван
Редкость: EPIC
Цена: 150 ₣

[Посмотреть на маркетплейсе]  ← inline button
```

**Несколько карт (batch):**
```
🔔 На маркетплейсе появились карты из вашего отслеживания:

• Иванов Иван (EPIC) — 150 ₣
• Петров Пётр (RARE) — 80 ₣
• Сидоров Сидор (LEGENDARY) — 500 ₣

[Открыть маркетплейс]  ← inline button
```

### 13.8 User API

```
GET    /api/v1/settings/marketplace-watches
POST   /api/v1/settings/marketplace-watches
DELETE /api/v1/settings/marketplace-watches/{id}
```

**GET** — список фильтров текущего пользователя:

```json
{
  "watches": [
    {
      "id": 1,
      "fantasyPlayer": { "id": 42, "nickname": "Иванов Иван" },
      "tournament": null,
      "rarity": "EPIC",
      "maxPrice": 200,
      "createdAt": "2026-04-20T12:00:00Z"
    },
    {
      "id": 2,
      "fantasyPlayer": null,
      "tournament": { "id": 1, "name": "Кубок Полемики" },
      "rarity": "LEGENDARY",
      "maxPrice": null,
      "createdAt": "2026-04-22T15:30:00Z"
    }
  ],
  "maxWatches": 10
}
```

**POST** — создать фильтр:

```json
{
  "fantasyPlayerId": 42,
  "tournamentId": null,
  "rarity": "EPIC",
  "maxPrice": 200
}
```

Валидация:
- Хотя бы одно поле (кроме `maxPrice`) должно быть задано — нельзя создать пустой фильтр «все карты».
- Не более 10 фильтров.
- `maxPrice` > 0 если задано.

**DELETE** — удалить фильтр по id (только свой).

### 13.9 UX: откуда пользователь создаёт фильтр

Два пути:

1. **Экран настроек** (`/settings/marketplace-watches`) — полное CRUD списка фильтров.
2. **Контекстная кнопка на маркетплейсе** — при просмотре результатов поиска кнопка «Отслеживать этот фильтр», которая автоматически создаёт watch из текущих параметров поиска.

Путь (2) — лучший UX, т.к. пользователь уже видит параметры фильтра. Но (1) нужен для управления.

### 13.10 Примеры типичных фильтров

| Описание | `fantasyPlayerId` | `tournamentId` | `rarity` | `maxPrice` |
|----------|-------------------|----------------|----------|------------|
| «Хочу любую карту Иванова» | 42 | NULL | NULL | NULL |
| «Хочу LEGENDARY за ≤ 500₣» | NULL | NULL | LEGENDARY | 500 |
| «Хочу EPIC из Кубка Полемики за ≤ 200₣» | NULL | 1 | EPIC | 200 |
| «Хочу конкретного Петрова из Летнего» | 15 | 3 | NULL | NULL |

---

## 14. Inline-кнопки в Telegram-уведомлениях

### 14.1 Обзор

Telegram Bot API позволяет прикреплять к сообщениям inline keyboard через `reply_markup`. Для TMA (Mini App) используется кнопка типа `web_app`:

```json
{
  "reply_markup": {
    "inline_keyboard": [[
      {
        "text": "Подать команду",
        "web_app": { "url": "https://fantasy.polemica.ru/series/5/team" }
      }
    ]]
  }
}
```

### 14.2 Расширение `TelegramBotApiClient`

Добавляем поддержку `reply_markup` в `sendMessage`:

```kotlin
fun sendMessage(
    botToken: String,
    chatId: Long,
    text: String,
    messageThreadId: Int? = null,
    parseMode: String? = null,
    replyMarkup: InlineKeyboardMarkup? = null,
) {
    val body = buildMap<String, Any> {
        put("chat_id", chatId)
        put("text", text)
        if (messageThreadId != null) put("message_thread_id", messageThreadId)
        if (parseMode != null) put("parse_mode", parseMode)
        if (replyMarkup != null) put("reply_markup", replyMarkup.toMap())
    }
    val tree = apiPost(botToken, "sendMessage", body)
    requireTelegramOk(tree, "sendMessage")
}
```

### 14.3 Модель для inline keyboard

```kotlin
data class InlineKeyboardMarkup(
    val inlineKeyboard: List<List<InlineKeyboardButton>>,
) {
    fun toMap(): Map<String, Any> = mapOf(
        "inline_keyboard" to inlineKeyboard.map { row ->
            row.map { it.toMap() }
        }
    )
}

sealed class InlineKeyboardButton {
    abstract val text: String
    abstract fun toMap(): Map<String, Any>

    /** Кнопка, открывающая TMA на указанном URL */
    data class WebApp(
        override val text: String,
        val url: String,
    ) : InlineKeyboardButton() {
        override fun toMap() = mapOf(
            "text" to text,
            "web_app" to mapOf("url" to url),
        )
    }

    /** Кнопка с callback_data (для будущих сценариев) */
    data class Callback(
        override val text: String,
        val callbackData: String,
    ) : InlineKeyboardButton() {
        override fun toMap() = mapOf(
            "text" to text,
            "callback_data" to callbackData,
        )
    }

    /** Кнопка-ссылка на внешний URL */
    data class Url(
        override val text: String,
        val url: String,
    ) : InlineKeyboardButton() {
        override fun toMap() = mapOf(
            "text" to text,
            "url" to url,
        )
    }
}
```

### 14.4 Конфигурация базового URL

Для формирования ссылок на TMA нужен базовый URL приложения:

```yaml
# application.yml
app:
  webapp-base-url: ${WEBAPP_BASE_URL:https://fantasy.polemica.ru}
```

```kotlin
@ConfigurationProperties(prefix = "app")
data class AppProperties(
    val webappBaseUrl: String = "https://fantasy.polemica.ru",
)
```

### 14.5 Хелпер для кнопок

```kotlin
@Component
class NotificationButtonFactory(
    private val appProperties: AppProperties,
) {
    fun submitTeamButton(seriesId: Long) = InlineKeyboardMarkup(
        inlineKeyboard = listOf(listOf(
            InlineKeyboardButton.WebApp(
                text = "📝 Подать команду",
                url = "${appProperties.webappBaseUrl}/series/$seriesId/team",
            ),
        )),
    )

    fun openMarketplaceButton() = InlineKeyboardMarkup(
        inlineKeyboard = listOf(listOf(
            InlineKeyboardButton.WebApp(
                text = "🛒 Открыть маркетплейс",
                url = "${appProperties.webappBaseUrl}/marketplace",
            ),
        )),
    )

    fun openMarketplaceFilteredButton(fantasyPlayerId: Long) = InlineKeyboardMarkup(
        inlineKeyboard = listOf(listOf(
            InlineKeyboardButton.WebApp(
                text = "🛒 Посмотреть на маркетплейсе",
                url = "${appProperties.webappBaseUrl}/marketplace?fantasyPlayerId=$fantasyPlayerId",
            ),
        )),
    )

    fun openSeriesLeaderboardButton(seriesId: Long) = InlineKeyboardMarkup(
        inlineKeyboard = listOf(listOf(
            InlineKeyboardButton.WebApp(
                text = "🏆 Результаты серии",
                url = "${appProperties.webappBaseUrl}/series/$seriesId/leaderboard",
            ),
        )),
    )

    fun openCardsButton() = InlineKeyboardMarkup(
        inlineKeyboard = listOf(listOf(
            InlineKeyboardButton.WebApp(
                text = "🃏 Моя коллекция",
                url = "${appProperties.webappBaseUrl}/cards",
            ),
        )),
    )
}
```

### 14.6 Расширение `NotificationDeliveryService`

```kotlin
fun deliver(
    telegramChatId: Long,
    category: NotificationCategory,
    text: String,
    parseMode: String? = null,
    replyMarkup: InlineKeyboardMarkup? = null,
): Boolean
```

### 14.7 Карта кнопок по типам уведомлений

| Уведомление | Кнопка | URL |
|-------------|--------|-----|
| `SERIES_START` | «Подать команду» | `/series/{id}/team` |
| `TEAM_DEADLINE_REMINDER` | «Подать команду» | `/series/{id}/team` |
| `SERIES_FINALIZED` | «Результаты серии» | `/series/{id}/leaderboard` |
| `SERIES_ROSTER_CHANGE` | «Моя коллекция» | `/cards` |
| `MARKETPLACE_SALE` | «Моя коллекция» | `/cards` |
| `MARKETPLACE_WATCH` | «Посмотреть на маркетплейсе» | `/marketplace?fantasyPlayerId={id}` или `/marketplace` |
| `PAIR_BAN` | — (нет кнопки) | — |
| `ADMIN_BROADCAST` | — (нет кнопки, текст произвольный) | — |

### 14.8 Ограничения `web_app` кнопок

- **HTTPS обязателен** — URL должен быть `https://`.
- **Домен должен быть подтверждён** в BotFather (`/setmenubutton` или `/setdomain`).
- **Не работает на десктопном клиенте** Telegram (только мобильные и web). На десктопе кнопка просто не отображается или открывает в браузере.
- **Альтернатива для desktop:** использовать `url` тип кнопки вместо `web_app` — откроет в браузере. Решение: предоставлять оба варианта нерационально; пользователи фэнтези преимущественно с мобильных устройств.

---

## 15. Дополнительные идеи (отложенные)

### 15.1 Рекомендуемые к добавлению в будущем

| Идея | Категория | Обоснование |
|------|-----------|-------------|
| **Новый пак доступен** | `NEW_PACK_AVAILABLE` | Стимулирует экономику; важно для пользователей, копящих фантики |
| **Новые результаты игр** | `SERIES_GAMES_SCORED` | «В серии X подсчитаны результаты 3 игр, ваш рейтинг обновлён» |

### 15.2 Настройки, не являющиеся категориями

| Настройка | Тип | Описание |
|-----------|-----|----------|
| **Тихие часы** (`quiet_hours_start`, `quiet_hours_end`) | `HH:mm` UTC | Не отправлять уведомления в ночное время |
| **Язык уведомлений** | `ru` / `en` | На будущее, если проект станет мультиязычным |

### 15.3 Почему НЕ стоит добавлять сейчас

- **Тихие часы** — сложность реализации (хранение таймзоны пользователя, планировщик отложенных сообщений) непропорциональна пользе при текущем масштабе.
- **Язык** — проект пока на русском; преждевременная интернационализация.

---

## 16. TMA UI: навигация и экраны настроек

### 16.1 Точка входа: кнопка уведомлений в top bar

В шапке приложения (`top__bar`) добавляется кнопка-колокольчик справа от баланса:

```
┌──────────────────────────────────────────────────┐
│ Polemica Fantasy          @username    120 ₣  🔔 │
│ [Турниры] [Коллекция] [Рейтинг] [Справка] ...   │
└──────────────────────────────────────────────────┘
```

Нажатие на `🔔` ведёт на `/notifications` — экран настроек уведомлений.

**Почему отдельная кнопка, а не пункт навигации:**
- Навигация уже содержит 6 пунктов — добавлять 7-й «Настройки» перегружает.
- Колокольчик — общепринятый UI-паттерн для уведомлений, интуитивно понятен.
- В будущем на колокольчике можно показывать badge с количеством непрочитанных (если появится in-app inbox).

### 16.2 Роутинг

Новые маршруты в `App.tsx`:

```
/notifications                    — настройки категорий уведомлений (главный экран)
/notifications/tournaments        — подписки на турниры
/notifications/marketplace-watches — фильтры отслеживания маркетплейса
```

### 16.3 Экран настроек категорий (`/notifications`)

```
🔔 Уведомления

━━ Турниры и серии ━━━━━━━━━━━━━━━━━━━
[toggle] Старт серии                         ✅
         Подписки на турниры: Кубок Полемики, Летний турнир  [>]
[toggle] Напоминание о дедлайне команды      ✅
[toggle] Результаты серии                     ✅
[toggle] Замена карт в составе                ✅

━━ Маркетплейс ━━━━━━━━━━━━━━━━━━━━━━
[toggle] Продажа вашей карты                  ✅
[toggle] Отслеживание карт                    ✅
         Фильтры отслеживания (3)  [>]

━━ Системные ━━━━━━━━━━━━━━━━━━━━━━━━
         Сообщения от администрации            🔒 Всегда вкл
         Уведомления о санкциях                🔒 Всегда вкл
```

### 16.4 Экран подписки на турниры (`/notifications/tournaments`)

```
🏆 Подписки на турниры

Если не выбран ни один — приходят уведомления обо всех турнирах.

[checkbox] Кубок Полемики        ☑️
[checkbox] Тренировочный         ☐
[checkbox] Летний турнир         ☑️
```

### 16.5 Экран фильтров отслеживания маркетплейса (`/notifications/marketplace-watches`)

```
🔔 Отслеживание карт (3 из 10)

┌─────────────────────────────────────────┐
│ Иванов Иван · EPIC · до 200 ₣    [✕]  │
├─────────────────────────────────────────┤
│ Любой · LEGENDARY · любая цена    [✕]  │
├─────────────────────────────────────────┤
│ Кубок Полемики · EPIC · до 200 ₣ [✕]  │
└─────────────────────────────────────────┘

[+ Добавить фильтр]
```

При нажатии «Добавить фильтр»:

```
🔎 Новый фильтр отслеживания

Игрок:    [▾ Выберите (необязательно)  ]
Турнир:   [▾ Выберите (необязательно)  ]
Редкость: [▾ Любая | COMMON | RARE | EPIC | LEGENDARY ]
Макс. цена: [__________] ₣ (необязательно)

                          [Сохранить]
```

### 16.6 Контекстная кнопка на маркетплейсе

На странице маркетплейса (`/marketplace`), когда пользователь задал фильтры поиска — дополнительная кнопка:

```
🔔 Отслеживать этот фильтр
```

Создаёт `marketplace_watch_filter` из текущих параметров поиска (fantasyPlayerId, tournamentId, rarity, maxPrice). Если такой фильтр уже существует — показывает «Уже отслеживается».

---

## 17. Последовательность реализации

| Этап | Что | Зависимости |
|------|-----|-------------|
| 1 | Flyway-миграция (§11), `NotificationCategory` enum, JPA entities | — |
| 2 | Inline keyboard support в `TelegramBotApiClient` (§14.2–14.3), `AppProperties` (§14.4) | — |
| 3 | `NotificationDeliveryService` (§7) + `NotificationButtonFactory` (§14.5): централизованная доставка с кнопками, `bot_blocked`, preferences | Этап 1, 2 |
| 4 | Миграция существующих listeners на `NotificationDeliveryService` с добавлением inline-кнопок | Этап 3 |
| 5 | User API (§8): GET/PUT notification settings, tournament subscriptions | Этап 1 |
| 6 | `SeriesBatchStartedEvent` + listener + admin endpoint (§6, §9) | Этап 3 |
| 7 | `TEAM_DEADLINE_REMINDER` (§12): scheduler + delivery | Этап 3 |
| 8 | `MARKETPLACE_WATCH` (§13): watch filter CRUD, matching, pending batch, scheduler | Этап 3 |
| 9 | User API (§13.8): marketplace watches CRUD | Этап 8 |
| 10 | TMA UI: экран настроек, подписки на турниры, фильтры маркетплейса (§16) | Этап 5, 9 |
| 11 | TMA: контекстная кнопка «Отслеживать фильтр» на маркетплейсе (§16.5) | Этап 9 |

---

## 18. Open questions

1. **Нужно ли хранить историю уведомлений (outbox)?** Полезно для debug и аналитики, но увеличивает нагрузку на БД. Можно решить позже; пока достаточно структурированного логирования.
2. **Нужно ли уведомление о новых играх в серии (sync)?** Например, «В серии "Кубок Полемики — Серия 5" появились результаты 3 новых игр». Может быть полезно, но создаёт шум.
3. **Домен TMA для `web_app` кнопок.** Нужно убедиться, что домен `fantasy.polemica.ru` (или текущий) зарегистрирован в BotFather через `/setmenubutton` или `/setdomain`.
4. **Батчинг marketplace watch: 5 минут или меньше?** 5 минут — баланс между частотой уведомлений и задержкой. Если маркетплейс активный — может быть стоит уменьшить до 2 минут.
5. **Badge на колокольчике.** В будущем можно показывать количество непрочитанных уведомлений, если появится in-app inbox. Пока колокольчик статический.
