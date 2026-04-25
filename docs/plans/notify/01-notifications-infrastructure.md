# План 1: Инфраструктура уведомлений

> **Предусловия:** нет  
> **Результат:** Flyway-миграция, enum `NotificationCategory`, JPA-сущности, inline keyboard в `TelegramBotApiClient`, централизованный `NotificationDeliveryService`, миграция всех существующих listeners  
> **Дизайн-документ:** §3 (Категории), §4 (Настройки), §5 (bot_blocked), §7 (DeliveryService), §11 (Миграция), §14 (Inline-кнопки)

---

## Шаги

### 1. Flyway-миграция: таблицы + колонки

**Файл:** `V34__notification_preferences.sql`  
**Путь:** `src/main/resources/db/migration/`

#### 1.1 Таблица `notification_preference`

```sql
CREATE TABLE notification_preference (
    id               BIGSERIAL PRIMARY KEY,
    telegram_user_id BIGINT NOT NULL REFERENCES telegram_user(id),
    category         VARCHAR(32) NOT NULL,
    enabled          BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_notif_pref_user_category UNIQUE (telegram_user_id, category)
);
```

#### 1.2 Таблица `tournament_subscription`

```sql
CREATE TABLE tournament_subscription (
    id               BIGSERIAL PRIMARY KEY,
    telegram_user_id BIGINT NOT NULL REFERENCES telegram_user(id),
    tournament_id    BIGINT NOT NULL REFERENCES tournament(id),
    CONSTRAINT uk_tournament_sub UNIQUE (telegram_user_id, tournament_id)
);
```

#### 1.3 Флаг `bot_blocked` в `telegram_user`

```sql
ALTER TABLE telegram_user ADD COLUMN bot_blocked BOOLEAN NOT NULL DEFAULT FALSE;
```

#### 1.4 Таблица `deadline_reminder` (для Плана 3)

```sql
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
```

#### 1.5 Таблицы marketplace watch (для Плана 4)

```sql
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

CREATE TABLE marketplace_watch_pending (
    id               BIGSERIAL PRIMARY KEY,
    telegram_user_id BIGINT NOT NULL REFERENCES telegram_user(id),
    listing_id       BIGINT NOT NULL REFERENCES marketplace_listing(id),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_mw_pending_user ON marketplace_watch_pending (telegram_user_id);
```

Вся миграция в одном файле — таблицы не зависят друг от друга, а разбивать на несколько V-файлов для одной фичи нет необходимости.

### 2. Enum `NotificationCategory`

**Файл:** `entity/NotificationCategory.kt` (новый)  
**Пакет:** `io.github.mralex1810.fantasy.entity`

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

### 3. JPA-сущность: `NotificationPreference`

**Файл:** `entity/NotificationPreference.kt` (новый)

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
```

### 4. JPA-сущность: `TournamentSubscription`

**Файл:** `entity/TournamentSubscription.kt` (новый)

```kotlin
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

### 5. Обновить `TelegramUser`: поле `botBlocked`

**Файл:** `entity/TelegramUser.kt`

Добавить:

```kotlin
@Column(name = "bot_blocked", nullable = false)
var botBlocked: Boolean = false,
```

### 6. Репозитории

**Файлы:** `repository/NotificationPreferenceRepository.kt`, `repository/TournamentSubscriptionRepository.kt` (новые)

```kotlin
interface NotificationPreferenceRepository : JpaRepository<NotificationPreference, Long> {
    fun findByTelegramUser_IdAndCategory(telegramUserId: Long, category: NotificationCategory): NotificationPreference?
    fun findAllByTelegramUser_Id(telegramUserId: Long): List<NotificationPreference>
    fun deleteAllByTelegramUser_Id(telegramUserId: Long)
}

interface TournamentSubscriptionRepository : JpaRepository<TournamentSubscription, Long> {
    fun findAllByTelegramUser_Id(telegramUserId: Long): List<TournamentSubscription>
    fun deleteAllByTelegramUser_Id(telegramUserId: Long)
    fun existsByTelegramUser_Id(telegramUserId: Long): Boolean
}
```

### 7. Модель inline keyboard

**Файл:** `telegram/InlineKeyboardMarkup.kt` (новый)

```kotlin
data class InlineKeyboardMarkup(
    val inlineKeyboard: List<List<InlineKeyboardButton>>,
) {
    fun toMap(): Map<String, Any> = mapOf(
        "inline_keyboard" to inlineKeyboard.map { row -> row.map { it.toMap() } }
    )
}

sealed class InlineKeyboardButton {
    abstract val text: String
    abstract fun toMap(): Map<String, Any>

    data class WebApp(
        override val text: String,
        val url: String,
    ) : InlineKeyboardButton() {
        override fun toMap() = mapOf("text" to text, "web_app" to mapOf("url" to url))
    }

    data class Callback(
        override val text: String,
        val callbackData: String,
    ) : InlineKeyboardButton() {
        override fun toMap() = mapOf("text" to text, "callback_data" to callbackData)
    }

    data class Url(
        override val text: String,
        val url: String,
    ) : InlineKeyboardButton() {
        override fun toMap() = mapOf("text" to text, "url" to url)
    }
}
```

### 8. Расширить `TelegramBotApiClient.sendMessage`

**Файл:** `telegram/TelegramBotApiClient.kt`

Добавить параметр `replyMarkup: InlineKeyboardMarkup? = null` в `sendMessage`:

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

Обратная совместимость: параметр nullable с дефолтом, вызовы без `replyMarkup` не меняются.

### 9. Результат отправки: `TelegramSendResult`

**Файл:** `telegram/TelegramSendResult.kt` (новый)

```kotlin
sealed class TelegramSendResult {
    data object Success : TelegramSendResult()
    data class BotBlocked(val description: String) : TelegramSendResult()
    data class RateLimited(val retryAfterSeconds: Int) : TelegramSendResult()
    data class OtherError(val code: Int, val description: String) : TelegramSendResult()
}
```

### 10. Безопасная отправка: `TelegramBotApiClient.sendMessageSafe`

**Файл:** `telegram/TelegramBotApiClient.kt`

Новый метод — не бросает исключение, а возвращает `TelegramSendResult`:

```kotlin
fun sendMessageSafe(
    botToken: String,
    chatId: Long,
    text: String,
    parseMode: String? = null,
    replyMarkup: InlineKeyboardMarkup? = null,
): TelegramSendResult {
    val body = buildMap<String, Any> {
        put("chat_id", chatId)
        put("text", text)
        if (parseMode != null) put("parse_mode", parseMode)
        if (replyMarkup != null) put("reply_markup", replyMarkup.toMap())
    }
    val tree = apiPost(botToken, "sendMessage", body)
    if (tree.path("ok").asBoolean()) return TelegramSendResult.Success
    val code = tree.path("error_code").asInt(0)
    val desc = tree.path("description").asText("")
    if (code == 403) return TelegramSendResult.BotBlocked(desc)
    if (code == 429) {
        val retryAfter = tree.path("parameters").path("retry_after").asInt(5)
        return TelegramSendResult.RateLimited(retryAfter)
    }
    return TelegramSendResult.OtherError(code, desc)
}
```

Старый `sendMessage` остаётся для обратной совместимости (вызовы в forum topic и т.п.).

### 11. Конфигурация `AppProperties`

**Файл:** `config/AppProperties.kt` (новый)

```kotlin
@ConfigurationProperties(prefix = "app")
data class AppProperties(
    val webappBaseUrl: String = "https://fantasy.polemica.ru",
)
```

**Файл:** `application.yml`

```yaml
app:
  webapp-base-url: ${WEBAPP_BASE_URL:https://fantasy.polemica.ru}
```

Зарегистрировать `@EnableConfigurationProperties(AppProperties::class)` в конфигурации.

### 12. `NotificationButtonFactory`

**Файл:** `telegram/NotificationButtonFactory.kt` (новый)

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
}
```

### 13. `NotificationDeliveryService`

**Файл:** `service/NotificationDeliveryService.kt` (новый)

Централизованный сервис доставки. Инкапсулирует:
- Проверку `bot_blocked`
- Проверку `notification_preference`
- Отправку через `TelegramBotApiClient.sendMessageSafe`
- Обработку `403 Forbidden` → обновление `bot_blocked = true`
- Обработку `429 Rate limited` → retry после `retry_after`
- Rate limiting (50ms между отправками для batch)

```kotlin
@Service
class NotificationDeliveryService(
    private val telegramBotApiClient: TelegramBotApiClient,
    private val telegramProperties: TelegramProperties,
    private val telegramUserRepository: TelegramUserRepository,
    private val notificationPreferenceRepository: NotificationPreferenceRepository,
) {
    fun deliver(
        telegramChatId: Long,
        category: NotificationCategory,
        text: String,
        parseMode: String? = null,
        replyMarkup: InlineKeyboardMarkup? = null,
    ): Boolean

    fun deliverToMany(
        recipients: List<Long>,
        category: NotificationCategory,
        textProvider: (telegramChatId: Long) -> String,
        parseMode: String? = null,
        replyMarkup: InlineKeyboardMarkup? = null,
    ): DeliveryReport

    fun broadcast(text: String, parseMode: String?): DeliveryReport
}

data class DeliveryReport(
    val sent: Int,
    val skippedBlocked: Int,
    val skippedPreference: Int,
    val failed: Int,
)
```

Внутренний метод `shouldDeliver`:

```kotlin
private fun shouldDeliver(telegramUserId: Long, category: NotificationCategory): Boolean {
    val user = telegramUserRepository.findById(telegramUserId) ?: return false
    if (user.botBlocked) return false
    if (!category.userToggleable) return true
    return notificationPreferenceRepository
        .findByTelegramUser_IdAndCategory(telegramUserId, category)
        ?.enabled
        ?: category.enabledByDefault
}
```

Обработка результата отправки:

```kotlin
private fun handleSendResult(result: TelegramSendResult, chatId: Long) {
    when (result) {
        is TelegramSendResult.BotBlocked -> {
            telegramUserRepository.findByTelegramId(chatId)?.let {
                if (!it.botBlocked) {
                    it.botBlocked = true
                    telegramUserRepository.save(it)
                    log.info("Marked user chatId={} as bot_blocked", chatId)
                }
            }
        }
        is TelegramSendResult.RateLimited -> {
            Thread.sleep(result.retryAfterSeconds * 1000L)
            // caller retries
        }
        is TelegramSendResult.OtherError ->
            log.warn("Telegram error chatId={}: {} {}", chatId, result.code, result.description)
        is TelegramSendResult.Success -> {}
    }
}
```

Уровни логирования — по таблице из §10.2 дизайн-документа.

### 14. Сброс `bot_blocked` при взаимодействии через TMA

**Файл:** `service/UserService.kt`

В `getOrCreateAndUpdateProfile` — если `existing.botBlocked == true`, сбросить:

```kotlin
if (existing.botBlocked) {
    existing.botBlocked = false
}
```

Пользователь открыл Mini App → бот не заблокирован.

### 15. Миграция `SeriesFinalizedNotificationListener`

**Файл:** `event/SeriesFinalizedNotificationListener.kt`

Заменить прямой вызов `TelegramBotApiClient` на `NotificationDeliveryService.deliver`:

```kotlin
@Component
class SeriesFinalizedNotificationListener(
    private val notificationDeliveryService: NotificationDeliveryService,
    private val notificationButtonFactory: NotificationButtonFactory,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    fun onSeriesFinalized(event: SeriesFinalizedNotificationEvent) {
        for (recipient in event.recipients) {
            val text = buildSeriesFinalizedTelegramMessage(
                event.tournamentName, event.seriesName,
                event.winnerPublicName, recipient,
            )
            notificationDeliveryService.deliver(
                telegramChatId = recipient.telegramId,
                category = NotificationCategory.SERIES_FINALIZED,
                text = text,
                replyMarkup = notificationButtonFactory.openSeriesLeaderboardButton(event.seriesId),
            )
        }
    }
}
```

Убрать: проверку `telegramProperties.notifications.enabled` и `token.isBlank()` (это теперь внутри `NotificationDeliveryService`), `try/catch` (обработка ошибок в сервисе), прямую зависимость от `TelegramBotApiClient` и `TelegramProperties`.

Добавить: `seriesId` в `SeriesFinalizedNotificationEvent` (для кнопки).

### 16. Миграция `MarketplaceSaleNotificationListener`

**Файл:** `event/MarketplaceSaleNotificationListener.kt`

Аналогично шагу 15:

```kotlin
@Component
class MarketplaceSaleNotificationListener(
    private val notificationDeliveryService: NotificationDeliveryService,
    private val notificationButtonFactory: NotificationButtonFactory,
    private val userService: UserService,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    fun onMarketplaceSale(event: MarketplaceSaleNotificationEvent) {
        val newBalance = userService.getBalance(event.sellerInternalUserId)
        val text = buildString {
            append("Карта «${event.playerName}» (${event.rarity}) куплена за ${event.price} ₣.\n")
            append("Вы получили ${event.sellerReceived} ₣ (комиссия ${event.commission} ₣).\n")
            append("Баланс: $newBalance ₣.")
        }
        notificationDeliveryService.deliver(
            telegramChatId = event.sellerTelegramChatId,
            category = NotificationCategory.MARKETPLACE_SALE,
            text = text,
            replyMarkup = notificationButtonFactory.openCardsButton(),
        )
    }
}
```

### 17. Миграция `PairBanNotificationListener`

**Файл:** `event/PairBanNotificationListener.kt`

Использовать `NotificationCategory.PAIR_BAN` (неотключаемый). Без inline-кнопки.

### 18. Миграция `SeriesRosterReplacementNotificationListener`

**Файл:** `event/SeriesRosterReplacementNotificationListener.kt`

Использовать `NotificationCategory.SERIES_ROSTER_CHANGE`, кнопка `openCardsButton()`.

### 19. Миграция `TelegramBroadcastAsyncSender`

**Файл:** `telegram/TelegramBroadcastAsyncSender.kt`

Заменить прямой цикл на `NotificationDeliveryService.broadcast`:

```kotlin
@Component
class TelegramBroadcastAsyncSender(
    private val notificationDeliveryService: NotificationDeliveryService,
) {
    @Async
    fun sendToAllChats(text: String): DeliveryReport {
        return notificationDeliveryService.broadcast(
            text = text,
            parseMode = TelegramBotApiClient.PARSE_MODE_MARKDOWN_V2,
        )
    }
}
```

Обновить `AdminBroadcastNotificationService` — передавать только текст, без `botToken` и `chatIds` (их `NotificationDeliveryService` получает сам, фильтруя по `bot_blocked`).

---

## Проверка готовности

- [ ] Миграция V34 применяется без ошибок
- [ ] `telegram_user.bot_blocked` = `false` для всех существующих пользователей
- [ ] Все 4 notification listener'а используют `NotificationDeliveryService` вместо прямого `TelegramBotApiClient`
- [ ] Broadcast фильтрует `bot_blocked` пользователей
- [ ] Inline-кнопки отображаются в Telegram (SERIES_FINALIZED → «Результаты серии», MARKETPLACE_SALE → «Моя коллекция», SERIES_ROSTER_CHANGE → «Моя коллекция»)
- [ ] При 403 от Telegram: `bot_blocked = true`, последующие уведомления не отправляются
- [ ] При открытии TMA заблокированным пользователем: `bot_blocked` сбрасывается в `false`
- [ ] При 429 от Telegram: retry после `retry_after`
- [ ] Старые вызовы `sendMessage` (forum topics и т.п.) продолжают работать
