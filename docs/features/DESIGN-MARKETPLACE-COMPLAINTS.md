# Polemica Fantasy — Жалобы на маркетплейсе и прозрачность сделок (Design Document)

> **Статус:** DRAFT — на согласовании  
> **Зависимости:**  
> - Маркетплейс: [`DESIGN-MARKETPLACE.md`](./DESIGN-MARKETPLACE.md)  
> - Уведомления: [`DESIGN-NOTIFICATIONS.md`](./DESIGN-NOTIFICATIONS.md)  
> - Антимодерация пар: текущий `MarketplaceAdminService` + `ban-pair` (см. `activeContext.md`)

---

## 1. Мотивация

### 1.1 Проблема

Игроки договариваются о сделках вне маркетплейса: один выставляет карту по минимальной
цене, второй мгновенно покупает. Это обесценивает рыночную механику и позволяет
перекачивать фантики между аккаунтами.

Существующие механизмы модерации (admin `pair-analysis` + `ban-pair`) работают реактивно
и опираются только на усмотрение администратора. У сообщества нет инструмента
для сигнализации о подозрительных сделках.

### 1.2 Цели

1. **Прозрачность:** каждая сделка получает отдельную страницу с полной информацией.
2. **Community moderation:** игроки могут жаловаться на подозрительные сделки.
3. **Инструменты админа:** список жалоб, санкции за нерыночные сделки, временные баны.
4. **Сдерживание:** публичная отметка санкционированных сделок + уведомления.
5. **Снижение координации:** убрать из листингов имя продавца и ценность карты.

### 1.3 Что НЕ входит

- Превентивные автоматические ограничения (кулдауны, лимиты на пару) — могут задеть
  добросовестных пользователей.
- Полный откат (аннулирование) сделок — слишком много edge-cases (перепродажа, апгрейд,
  переработка). Вместо этого — штрафы участников.

---

## 2. Изменения в отображении листингов

### 2.1 Убрать имя продавца из каталога

В `MarketplaceListingEntryDto` поле `seller` (`MarketplaceSellerBriefDto`) перестаёт
возвращаться для каталога (`GET /marketplace/listings`). Контроллер передаёт `seller = null`
для анонимности. На своих листингах (`GET /marketplace/my-listings`) продавец по-прежнему
виден (это твои карты).

**Обоснование:** если покупатель не знает, кто продаёт, координация «я выставлю — ты купи»
усложняется. Продавец виден только на странице завершённой сделки (§3).

### 2.2 Убрать ценность карты из каталога

Поле `card.value` в `MarketplaceListingCardDto` перестаёт возвращаться в каталоге листингов.
На странице коллекции ценность по-прежнему видна (при выставлении карты пользователь
видит ценность своей карты).

**Обоснование:** ценность карты может использоваться для «наводки» на конкретный лот
при координации.

### 2.3 Затронутые API

| Endpoint | `seller` | `card.value` |
|----------|----------|--------------|
| `GET /marketplace/listings` | `null` | `null` |
| `GET /marketplace/my-listings` | Показан | Показана |
| `GET /marketplace/feed` | Не меняется (имена продавца и покупателя уже показаны — это завершённые сделки) | Не меняется |
| `GET /marketplace/transactions/{id}` (новый) | Показан | Показана |

---

## 3. Страница сделки (Transaction Detail)

### 3.1 Назначение

Отдельная страница для каждой завершённой сделки (`marketplace_listing` со статусом `SOLD`).
Показывает полную информацию: участники, карта, цена, перки, статус модерации.

### 3.2 API

```
GET /api/v1/marketplace/transactions/{listingId}
```

Response `MarketplaceTransactionDetailDto`:

```json
{
  "listingId": 42,
  "price": 180,
  "soldAt": "2026-04-08T15:00:00Z",
  "commission": 18,
  "sellerReceived": 162,
  "seller": {
    "telegramId": 123456789,
    "displayName": "Алиса"
  },
  "buyer": {
    "telegramId": 987654321,
    "displayName": "Борис"
  },
  "card": {
    "fantasyPlayerId": 5,
    "playerName": "Петров",
    "playerPhotoUrl": "https://...",
    "rarity": "EPIC",
    "perks": [
      { "perkId": "WON_GAME", "name": "Победа", "bonusPoints": 1.0 },
      { "perkId": "BEST_MOVE", "name": "Лучший ход", "bonusPoints": 1.0 }
    ]
  },
  "complaint": {
    "totalComplaints": 3,
    "userAlreadyComplained": true
  },
  "sanction": null
}
```

Если сделка санкционирована:

```json
{
  "sanction": {
    "sanctionedAt": "2026-04-10T12:00:00Z",
    "reason": "Нерыночная сделка"
  }
}
```

**Валидация:** листинг должен быть в статусе `SOLD`. Иначе — `404`.

### 3.3 DTO

```kotlin
data class MarketplaceTransactionDetailDto(
    val listingId: Long,
    val price: Long,
    val soldAt: Instant,
    val commission: Long,
    val sellerReceived: Long,
    val seller: TransactionParticipantDto,
    val buyer: TransactionParticipantDto,
    val card: TransactionCardDto,
    val complaint: TransactionComplaintInfoDto,
    val sanction: TransactionSanctionInfoDto?,
)

data class TransactionParticipantDto(
    val telegramId: Long,
    val displayName: String,
)

data class TransactionCardDto(
    val fantasyPlayerId: Long,
    val playerName: String,
    val playerPhotoUrl: String?,
    val rarity: Rarity,
    val perks: List<MarketplaceCardPerkDto>,
)

data class TransactionComplaintInfoDto(
    val totalComplaints: Int,
    val userAlreadyComplained: Boolean,
)

data class TransactionSanctionInfoDto(
    val sanctionedAt: Instant,
    val reason: String,
)
```

### 3.4 Точки входа в TMA

| Откуда | Как |
|--------|-----|
| Лента сделок (`/marketplace` feed) | Клик по элементу ленты → `/marketplace/transactions/{id}` |
| Профиль игрока (`/players/:telegramId`) | Клик по сделке в «Последние сделки» → `/marketplace/transactions/{id}` |

Для этого в `MarketplaceFeedItemDto` и `PlayerMarketplaceTradeDto` добавляется поле
`listingId: Long`.

### 3.5 TMA маршрут

```
/marketplace/transactions/:listingId
```

Страница показывает:
- Карту с фото, рамкой по редкости, списком перков
- Цену, комиссию, полученную продавцом сумму
- Продавца и покупателя (ссылки на `/players/:telegramId`)
- Дату сделки
- Кнопку «Пожаловаться» (§4)
- Метку санкции, если сделка признана нерыночной (§6)

---

## 4. Система жалоб

### 4.1 Модель данных

```sql
CREATE TABLE marketplace_complaint (
    id               BIGSERIAL PRIMARY KEY,
    listing_id       BIGINT NOT NULL REFERENCES marketplace_listing(id),
    telegram_user_id BIGINT NOT NULL REFERENCES telegram_user(id),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uk_complaint_listing_user UNIQUE (listing_id, telegram_user_id)
);

CREATE INDEX idx_complaint_listing ON marketplace_complaint (listing_id);
CREATE INDEX idx_complaint_user ON marketplace_complaint (telegram_user_id);
```

Одна жалоба на пользователя на сделку (UNIQUE constraint).

### 4.2 Лимит жалоб

**5 жалоб в сутки** на пользователя. Проверяется при создании:

```sql
SELECT COUNT(*) FROM marketplace_complaint
WHERE telegram_user_id = :userId
  AND created_at >= :startOfDay  -- now() - interval '24 hours'
```

При превышении — `429 Too Many Requests: "Daily complaint limit reached (5)"`.

Лимит хранится в `economy_config` как `marketplace.daily_complaint_limit` (default `5`),
чтобы админ мог корректировать без деплоя.

### 4.3 Правила подачи жалобы

| Условие | Ошибка |
|---------|--------|
| Листинг в статусе `SOLD` | `400: "Can only complain about completed transactions"` |
| Жалобщик ≠ продавец и жалобщик ≠ покупатель | `400: "Cannot complain about your own transaction"` |
| Пользователь ещё не жаловался на эту сделку | `409: "Already complained"` |
| Лимит жалоб в сутки не исчерпан | `429: "Daily complaint limit reached"` |
| Сделка не санкционирована (нет строки в `marketplace_listing_sanction`) | `400: "Transaction already sanctioned"` |

**Примечание:** участники сделки (продавец, покупатель) **не могут** жаловаться на свою
собственную сделку. Жалобы подают только сторонние наблюдатели.

### 4.4 User API

```
POST /api/v1/marketplace/transactions/{listingId}/complain
```

Request: пустое тело.

Response (success): `201 Created`

```json
{
  "listingId": 42,
  "totalComplaints": 4,
  "remainingToday": 3
}
```

### 4.5 JPA-сущность

```kotlin
@Entity
@Table(name = "marketplace_complaint")
class MarketplaceComplaint(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "listing_id", nullable = false)
    var listing: MarketplaceListing? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "telegram_user_id", nullable = false)
    var telegramUser: TelegramUser? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
)
```

---

## 5. Санкции за нерыночные сделки

### 5.1 Модель данных

```sql
CREATE TABLE marketplace_listing_sanction (
    id               BIGSERIAL PRIMARY KEY,
    listing_id       BIGINT NOT NULL REFERENCES marketplace_listing(id),
    reason           TEXT NOT NULL,
    seller_fine      BIGINT NOT NULL DEFAULT 0,
    buyer_fine       BIGINT NOT NULL DEFAULT 0,
    complainant_reward BIGINT NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    admin_username   VARCHAR(64) NOT NULL,

    CONSTRAINT uk_sanction_listing UNIQUE (listing_id)
);
```

Одна санкция на сделку (UNIQUE на `listing_id`).

### 5.2 Механика санкции

Администратор в админке выбирает сделку, видит детали и жалобы, и применяет санкцию.
При санкционировании:

1. **Штраф продавцу:** списание фантиков через `UserService.forceDeductBalance`
   (как в `ban-pair` — может уйти в минус). Транзакция с причиной
   `MARKETPLACE_SANCTION_FINE`.
2. **Штраф покупателю:** аналогично.
3. **Награда жалобщикам:** каждому пользователю из `marketplace_complaint` для этой сделки
   начисляется `complainant_reward` фантиков. Транзакция с причиной
   `MARKETPLACE_COMPLAINT_REWARD`.
4. **Запись в `marketplace_listing_sanction`** для аудита и отображения.

### 5.3 Расчёт штрафов

Предлагаемый дефолт (администратор может менять в UI при применении):

| Параметр | Формула | Обоснование |
|----------|---------|-------------|
| `seller_fine` | `sellerReceived` (полное нетто) | Продавец возвращает всю полученную сумму |
| `buyer_fine` | `0` | Покупатель уже потратил фантики на покупку |
| `complainant_reward` | `⌊commission / complainantCount⌋` | Комиссия сделки делится между жалобщиками |

Администратор видит рекомендуемые значения, но может их изменить перед применением.

Если `complainant_reward × complainantCount > commission`, админ получает предупреждение,
но может подтвердить (награда создаётся из общего пула, а не из конфискованного).

### 5.4 JPA-сущность

```kotlin
@Entity
@Table(name = "marketplace_listing_sanction")
class MarketplaceListingSanction(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "listing_id", nullable = false)
    var listing: MarketplaceListing? = null,

    @Column(nullable = false, columnDefinition = "TEXT")
    var reason: String = "",

    @Column(name = "seller_fine", nullable = false)
    var sellerFine: Long = 0,

    @Column(name = "buyer_fine", nullable = false)
    var buyerFine: Long = 0,

    @Column(name = "complainant_reward", nullable = false)
    var complainantReward: Long = 0,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "admin_username", nullable = false, length = 64)
    var adminUsername: String = "",
)
```

### 5.5 Новые причины `FantikiTransactionReason`

```kotlin
enum class FantikiTransactionReason {
    // ... existing ...
    MARKETPLACE_SANCTION_FINE,
    MARKETPLACE_COMPLAINT_REWARD,
}
```

---

## 6. Публичное отображение санкций

### 6.1 Лента сделок

В `MarketplaceFeedItemDto` добавляется:

```kotlin
val sanctioned: Boolean  // true, если есть marketplace_listing_sanction
```

В TMA в ленте санкционированная сделка визуально помечается (перечёркнутая цена,
красный бейдж «Нерыночная сделка»).

### 6.2 Страница сделки

На странице транзакции (`/marketplace/transactions/:id`) отображается блок санкции:

```
⚠️ Сделка признана нерыночной
Причина: Координированная покупка по минимальной цене
Дата: 10 апреля 2026, 12:00 МСК
```

### 6.3 Профиль игрока

В `PlayerMarketplaceTradeDto` добавляется:

```kotlin
val sanctioned: Boolean
```

В списке «Последние сделки» на профиле — аналогичная пометка.

---

## 7. Уведомления

### 7.1 Новые категории

В `NotificationCategory` добавляются:

```kotlin
MARKETPLACE_COMPLAINT_RESOLVED(userToggleable = true, enabledByDefault = true),
```

Одна категория покрывает уведомления и для участников сделки, и для жалобщиков.

`MARKETPLACE_SANCTION_FINE` — неотключаемо (как `PAIR_BAN`):

```kotlin
MARKETPLACE_SANCTION_APPLIED(userToggleable = false, enabledByDefault = true),
```

### 7.2 Уведомление участникам (продавец / покупатель)

Категория: `MARKETPLACE_SANCTION_APPLIED` (неотключаемо).

Триггер: создание `marketplace_listing_sanction`.

**Продавцу:**
```
⚠️ Сделка по карте «Петров» (EPIC) за 180 ₣ признана нерыночной.
Причина: Координированная покупка по минимальной цене.
Штраф: −162 ₣.
Баланс: 238 ₣.
```

**Покупателю** (если `buyerFine > 0`):
```
⚠️ Сделка по карте «Петров» (EPIC) за 180 ₣ признана нерыночной.
Причина: Координированная покупка по минимальной цене.
Штраф: −50 ₣.
Баланс: 370 ₣.
```

**Покупателю** (если `buyerFine == 0`):
```
ℹ️ Сделка по карте «Петров» (EPIC) за 180 ₣ признана нерыночной.
Причина: Координированная покупка по минимальной цене.
К вам штраф не применён.
```

### 7.3 Уведомление жалобщикам

Категория: `MARKETPLACE_COMPLAINT_RESOLVED` (отключаемо).

```
✅ По вашей жалобе на сделку «Петров» (EPIC) за 180 ₣ принято решение.
Сделка признана нерыночной.
Награда: +6 ₣.
Баланс: 1206 ₣.
```

Inline-кнопка: «Посмотреть сделку» → `/marketplace/transactions/{listingId}`.

### 7.4 Event

```kotlin
data class MarketplaceSanctionAppliedEvent(
    val listingId: Long,
    val playerName: String,
    val rarity: Rarity,
    val price: Long,
    val reason: String,
    val sellerTelegramChatId: Long,
    val sellerFine: Long,
    val sellerNewBalance: Long,
    val buyerTelegramChatId: Long,
    val buyerFine: Long,
    val buyerNewBalance: Long,
    val complainants: List<ComplainantRewardInfo>,
)

data class ComplainantRewardInfo(
    val telegramChatId: Long,
    val reward: Long,
    val newBalance: Long,
)
```

Listener: `@TransactionalEventListener(AFTER_COMMIT)` + `@Async`, доставка через
`NotificationDeliveryService`.

---

## 8. Временный бан маркетплейса

### 8.1 Модель

Текущий `telegram_user.marketplace_banned` — boolean, не поддерживает временные баны.

Добавляется колонка:

```sql
ALTER TABLE telegram_user ADD COLUMN marketplace_banned_until TIMESTAMPTZ;
```

**Семантика:**
- `marketplace_banned = true` — перманентный бан (как сейчас)
- `marketplace_banned_until IS NOT NULL AND marketplace_banned_until > now()` — временный бан
- Оба условия проверяются в `MarketplaceService.createListing` и `buyCard`:

```kotlin
fun isMarketplaceBanned(user: TelegramUser): Boolean {
    if (user.marketplaceBanned) return true
    val until = user.marketplaceBannedUntil ?: return false
    return until.isAfter(Instant.now())
}
```

### 8.2 Администрирование

Длительность бана — на усмотрение администратора. Рекомендуемые пресеты:

| Нарушение | Рекомендуемый срок |
|-----------|--------------------|
| Первое предупреждение | 3 дня |
| Повторное | 7 дней |
| Злостное | 30 дней |
| Перманент | `marketplace_banned = true` |

### 8.3 Связь с санкцией сделки

Временный бан — **отдельное** действие от санкции сделки. Админ может:
- Санкционировать сделку **без** бана (мягкий случай)
- Санкционировать сделку **с** баном участников (серьёзный случай)
- Забанить пользователя **без** санкции конкретной сделки (по совокупности)

---

## 9. Списки в админке

### 9.1 Список транзакций с жалобами

Новый раздел в `MarketplaceModerationPage` или отдельная страница.

**API:**

```
GET /api/v1/admin/marketplace/complained-transactions
    ?page=0&size=20
    &minComplaints=1
    &sortBy=complaints_desc|sold_at_desc
```

Response `PagedComplainedTransactionsDto`:

```json
{
  "content": [
    {
      "listingId": 42,
      "playerName": "Петров",
      "rarity": "EPIC",
      "price": 180,
      "soldAt": "2026-04-08T15:00:00Z",
      "seller": { "telegramId": 123, "displayName": "Алиса" },
      "buyer": { "telegramId": 456, "displayName": "Борис" },
      "complaintsCount": 5,
      "sanctioned": false
    }
  ],
  "totalElements": 12,
  "page": 0,
  "size": 20
}
```

**Детали жалоб по транзакции:**

```
GET /api/v1/admin/marketplace/transactions/{listingId}/complaints
```

Response: список жалобщиков:

```json
{
  "complaints": [
    {
      "userId": 789,
      "displayName": "Вася",
      "telegramId": 111222333,
      "complainedAt": "2026-04-09T10:00:00Z"
    }
  ]
}
```

### 9.2 Санкционирование сделки

```
POST /api/v1/admin/marketplace/transactions/{listingId}/sanction
```

Request `SanctionTransactionRequest`:

```json
{
  "reason": "Координированная покупка по минимальной цене",
  "sellerFine": 162,
  "buyerFine": 0,
  "complainantReward": 6,
  "banSeller": { "days": 3 },
  "banBuyer": null
}
```

Поля `banSeller` / `banBuyer` — опциональные; если заданы, выставляют
`marketplace_banned_until = now() + days`.

Response `SanctionTransactionResultDto`:

```json
{
  "listingId": 42,
  "sellerFined": 162,
  "sellerNewBalance": 238,
  "sellerBannedUntil": "2026-04-13T12:00:00Z",
  "buyerFined": 0,
  "buyerNewBalance": 420,
  "buyerBannedUntil": null,
  "complainantsRewarded": 3,
  "totalRewardPaid": 18
}
```

### 9.3 Список игроков по жалобам

```
GET /api/v1/admin/marketplace/users-by-complaints
    ?page=0&size=20
    &sortBy=total_complaints_desc|avg_complaints_desc
```

Response: агрегация по пользователям, участвовавшим в сделках с жалобами:

```json
{
  "content": [
    {
      "telegramId": 123456789,
      "displayName": "Алиса",
      "totalComplaints": 15,
      "transactionsWithComplaints": 4,
      "avgComplaintsPerTransaction": 3.75,
      "sanctionedTransactions": 1,
      "marketplaceBanned": false,
      "marketplaceBannedUntil": null
    }
  ],
  "totalElements": 8,
  "page": 0,
  "size": 20
}
```

**Кнопка «Забанить» из списка:**

```
POST /api/v1/admin/marketplace/users/{telegramId}/ban
```

Request:

```json
{
  "days": 7
}
```

`days = null` → перманентный бан (`marketplace_banned = true`).  
`days > 0` → `marketplace_banned_until = now() + days`.

**Кнопка «Разбанить»:** существующий `POST /api/v1/admin/marketplace/unban/{telegramId}`
расширяется — сбрасывает **оба** поля: `marketplace_banned = false`,
`marketplace_banned_until = null`.

---

## 10. Flyway-миграция

```sql
-- V39__marketplace_complaints.sql

-- 1. Жалобы на сделки
CREATE TABLE marketplace_complaint (
    id               BIGSERIAL PRIMARY KEY,
    listing_id       BIGINT NOT NULL REFERENCES marketplace_listing(id),
    telegram_user_id BIGINT NOT NULL REFERENCES telegram_user(id),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_complaint_listing_user UNIQUE (listing_id, telegram_user_id)
);

CREATE INDEX idx_complaint_listing ON marketplace_complaint (listing_id);
CREATE INDEX idx_complaint_user ON marketplace_complaint (telegram_user_id);

-- 2. Санкции на сделки
CREATE TABLE marketplace_listing_sanction (
    id                  BIGSERIAL PRIMARY KEY,
    listing_id          BIGINT NOT NULL REFERENCES marketplace_listing(id),
    reason              TEXT NOT NULL,
    seller_fine         BIGINT NOT NULL DEFAULT 0,
    buyer_fine          BIGINT NOT NULL DEFAULT 0,
    complainant_reward  BIGINT NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    admin_username      VARCHAR(64) NOT NULL,
    CONSTRAINT uk_sanction_listing UNIQUE (listing_id)
);

-- 3. Временный бан маркетплейса
ALTER TABLE telegram_user ADD COLUMN marketplace_banned_until TIMESTAMPTZ;

-- 4. Лимит жалоб в сутки
INSERT INTO economy_config (key, value, description) VALUES
    ('marketplace.daily_complaint_limit', '5', 'Максимум жалоб на маркетплейсе в сутки');
```

---

## 11. Изменения в существующих DTO

### 11.1 `MarketplaceFeedItemDto`

Добавляются поля:

```kotlin
val listingId: Long       // для перехода на страницу сделки
val sanctioned: Boolean   // true, если есть marketplace_listing_sanction
```

### 11.2 `PlayerMarketplaceTradeDto`

Добавляются поля:

```kotlin
val listingId: Long       // для перехода на страницу сделки
val sanctioned: Boolean
```

### 11.3 `MarketplaceListingEntryDto`

Для каталога (`GET /listings`):
- `seller` → `null` (анонимность)
- `card.value` → `null`

Для своих листингов (`GET /my-listings`) — без изменений.

### 11.4 `MarketplaceService.isMarketplaceBanned`

Расширяется проверкой `marketplace_banned_until`:

```kotlin
fun isMarketplaceBanned(user: TelegramUser): Boolean {
    if (user.marketplaceBanned) return true
    val until = user.marketplaceBannedUntil ?: return false
    return until.isAfter(Instant.now())
}
```

---

## 12. Взаимодействие с существующими механиками

| Механика | Поведение |
|----------|-----------|
| `ban-pair` (антимодерация) | Не меняется. Санкция сделки — отдельный инструмент. Админ может использовать оба: `ban-pair` для перелива фантиков между парой, санкцию сделки для конкретной операции. |
| `pair-analysis` | Не меняется. Количество жалоб может дополнительно подсвечиваться в pair-analysis (будущее). |
| `marketplace_banned` (перманент) | Проверяется **первым**; если true, `marketplace_banned_until` не проверяется. |
| Уведомления | Новые категории `MARKETPLACE_SANCTION_APPLIED` (неотключаемо) и `MARKETPLACE_COMPLAINT_RESOLVED` (отключаемо) добавляются в `NotificationCategory`. |
| `economy_config` | Новый ключ `marketplace.daily_complaint_limit`. |

---

## 13. Admin API — сводка новых эндпоинтов

| Method | Path | Описание |
|--------|------|----------|
| GET | `/admin/marketplace/complained-transactions` | Список SOLD-сделок с жалобами (пагинация, фильтры) |
| GET | `/admin/marketplace/transactions/{id}/complaints` | Список жалобщиков конкретной сделки |
| POST | `/admin/marketplace/transactions/{id}/sanction` | Применить санкцию к сделке |
| GET | `/admin/marketplace/users-by-complaints` | Список пользователей по числу жалоб |
| POST | `/admin/marketplace/users/{telegramId}/ban` | Временный или перманентный бан |

Существующий `POST /admin/marketplace/unban/{telegramId}` расширяется для сброса обоих
полей (`marketplace_banned`, `marketplace_banned_until`).

---

## 14. User API — сводка новых эндпоинтов

| Method | Path | Описание |
|--------|------|----------|
| GET | `/marketplace/transactions/{listingId}` | Детали завершённой сделки |
| POST | `/marketplace/transactions/{listingId}/complain` | Пожаловаться на сделку |

---

## 15. TMA UI — новые экраны и изменения

### 15.1 Страница сделки (`/marketplace/transactions/:listingId`)

- Карта: фото, рамка по редкости, список перков
- Продавец → ссылка на `/players/:telegramId`
- Покупатель → ссылка на `/players/:telegramId`
- Цена, комиссия, сумма продавцу
- Дата сделки
- Кнопка **«Пожаловаться»** (disabled, если уже жаловался или участник сделки)
- Счётчик жалоб: «N жалоб»
- Блок санкции (если есть): причина, дата, красный бейдж

### 15.2 Лента маркетплейса

- Каждый элемент ленты кликабелен → переход на страницу сделки
- Санкционированные сделки: перечёркнутая цена, бейдж «Нерыночная»

### 15.3 Каталог листингов

- Убрано имя продавца
- Убрана ценность карты

### 15.4 Профиль игрока

- Сделки в «Последние сделки» кликабельны → страница сделки
- Санкционированные сделки помечены

---

## 16. Админка — новые разделы

### 16.1 Вкладка «Жалобы» на странице Marketplace Moderation

Таблица с колонками:
- Карта (имя, редкость)
- Цена
- Продавец / Покупатель
- Дата сделки
- Жалобы (число)
- Статус (ожидает / санкционирована)
- Действие: кнопка «Санкционировать»

Фильтр: минимум жалоб, статус санкции.

### 16.2 Модалка санкции

- Информация о сделке
- Список жалобщиков
- Поля (с рекомендуемыми значениями):
  - Штраф продавцу (дефолт: `sellerReceived`)
  - Штраф покупателю (дефолт: `0`)
  - Награда жалобщику (дефолт: `⌊commission / complainantsCount⌋`)
- Чекбоксы: «Забанить продавца на N дней» / «Забанить покупателя на N дней»
- Причина (текст)
- Кнопка «Применить» с подтверждением

### 16.3 Вкладка «Игроки по жалобам»

Таблица с колонками:
- Пользователь (displayName, telegramId)
- Всего жалоб
- Сделок с жалобами
- Среднее жалоб на сделку
- Санкционировано сделок
- Статус бана
- Действие: кнопка «Забанить» (выбор срока) / «Разбанить»

---

## 17. Порядок реализации

| Блок | Что | Зависимости |
|------|-----|-------------|
| C1 | Flyway V39: `marketplace_complaint`, `marketplace_listing_sanction`, `marketplace_banned_until`, `economy_config` ключ | — |
| C2 | Entity + Repository: `MarketplaceComplaint`, `MarketplaceListingSanction`; `FantikiTransactionReason` расширение | C1 |
| C3 | `MarketplaceComplaintService`: жалоба, лимит, проверка; `MarketplaceTransactionService`: детали сделки | C2 |
| C4 | `MarketplaceSanctionService`: применение санкции, штрафы, награды, событие + listener уведомлений | C2 |
| C5 | Анонимизация листингов: `seller = null`, `card.value = null` в `GET /listings`; проверка `marketplace_banned_until` в `isMarketplaceBanned` | C2 |
| C6 | Admin API: complained-transactions, complaints detail, sanction, users-by-complaints, ban | C3, C4 |
| C7 | User API: `GET /marketplace/transactions/{id}`, `POST .../complain` | C3 |
| C8 | Изменения DTO: `listingId` + `sanctioned` в feed и profile trades | C2 |
| C9 | TMA: страница сделки, кнопка «Пожаловаться», пометки санкций в ленте и профиле | C7, C8 |
| C10 | TMA: убрать продавца и ценность из каталога листингов | C5 |
| C11 | Админка: вкладка «Жалобы», модалка санкции, вкладка «Игроки по жалобам» | C6 |

C3 и C4 могут выполняться параллельно. C9–C11 могут выполняться параллельно после C7/C8.

---

## 18. Принятые решения

| Вопрос | Решение |
|--------|---------|
| Аннулирование vs штрафы | Штрафы (как `ban-pair`): списание фантиков, без отката карты |
| Кто может жаловаться | Все, кроме участников сделки (продавец/покупатель не могут жаловаться на себя) |
| Лимит жалоб | 5 в сутки на пользователя (настраиваемо через `economy_config`) |
| Продавец в листинге | Скрыт (анонимность для затруднения координации) |
| Ценность карты в листинге | Скрыта (против наводки на конкретный лот) |
| Награда жалобщикам | Из комиссии сделки, разделённой поровну |
| Публичность санкций | Пометка в ленте, профиле и на странице сделки |
| Уведомления участникам | Неотключаемо (`MARKETPLACE_SANCTION_APPLIED`) |
| Уведомления жалобщикам | Отключаемо (`MARKETPLACE_COMPLAINT_RESOLVED`) |
| Временный бан | `marketplace_banned_until` (timestamp), параллельно с перманентным `marketplace_banned` |

---

## 19. На будущее (не в этом дизайне)

| Идея | Описание |
|------|----------|
| **Автоматические флаги** | Подсвечивать сделки с аномальной ценой в админке (без блокировки) |
| **Репутация жалобщика** | Вес жалобы зависит от % подтверждённых жалоб пользователя |
| **Интеграция с pair-analysis** | Показывать количество жалоб по паре в таблице пар |
| **Escrow / аукцион** | Альтернативные механики продажи, исключающие координацию |
| **Штраф за ложные жалобы** | При систематически неподтверждённых жалобах — снижение лимита |
