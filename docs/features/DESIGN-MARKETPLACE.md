# Polemica Fantasy — Маркетплейс карточек (Design Document)

> **Статус:** СОГЛАСОВАН — готов к реализации  
> Общий контекст системы: [`../architecture/DESIGN.md`](../architecture/DESIGN.md)  
> Экономика карт: [`../architecture/DESIGN.md` §4–§5](../architecture/DESIGN.md)  
> Легендарные карты: [`DESIGN-LEGENDARY-CARDS.md`](./DESIGN-LEGENDARY-CARDS.md)

---

## 1. Мотивация

Сейчас карточки можно получить **только** из паков (случайно) или от администратора.
Ненужные карты перерабатываются за фантики — обратного пути нет.
Если пользователю нужен конкретный игрок, он может только надеяться, что карта выпадет.

**Цель:** дать пользователям возможность **продавать и покупать** карточки друг у друга
за фантики. Маркетплейс создаёт вторичный рынок, где ценность карты определяется
спросом сообщества, а не только алгоритмом паков.

### Что это даёт

- **Целенаправленная сборка команды:** можно купить именно того игрока, который нужен на серию
- **Fantiki-sink:** комиссия 10% с каждой сделки сжигает фантики → борьба с инфляцией
- **Мотивация:** ненужные карты можно продать выгоднее, чем переработать
- **Социальность:** лента сделок, «рынок легендарок», провенанс карточек
- **Стратегия:** продать до того, как карта «износится», или купить нужного игрока перед серией

---

## 2. Основная механика

### 2.1 Выставление на продажу (листинг)

Пользователь выбирает карточку из коллекции и задаёт цену в фантиках.

| Шаг | Действие |
|-----|----------|
| 1 | Выбрать карту из коллекции |
| 2 | Указать цену (≥ минимальная для редкости) |
| 3 | Подтвердить — карта появляется на маркетплейсе |

Карточка **остаётся** у продавца, но **заблокирована** — её нельзя:
- Поставить в фэнтези-команду
- Переработать (recycle)
- Апгрейдить до LEGENDARY
- Выставить повторно (уже выставлена)

### 2.2 Покупка

Покупатель находит карту на маркетплейсе и покупает в один клик.

| Шаг | Действие |
|-----|----------|
| 1 | Списание полной цены с покупателя |
| 2 | Начисление продавцу: цена − 10% комиссия |
| 3 | Карта переходит покупателю со **свежим контрактом** |
| 4 | Запись в историю владельцев |
| 5 | Telegram-уведомление продавцу |

### 2.3 Свежий контракт при покупке

При смене владельца через маркетплейс карта **обновляется**:

| Поле UserCard | До покупки | После покупки |
|---------------|------------|---------------|
| `telegram_user_id` | Продавец | Покупатель |
| `card_template_id` | Без изменений | Без изменений |
| `uses_remaining` | Любое (> 0) | Начальное для редкости (`card.uses.*`) |
| `times_renewed` | Любое | **0** |
| `crafted_by_*` | Без изменений | Без изменений |
| `acquired_at` | Без изменений | Без изменений |

Покупатель всегда получает карту как будто из пака — полный контракт,
все продления доступны.

### 2.4 Снятие с продажи

Продавец может отменить листинг в любой момент. Карта возвращается
в обычное состояние (разблокируется). Uses и renewals **не меняются** —
сброс контракта происходит только при реальной продаже.

### 2.5 Минимальная цена

Минимальная цена листинга задаётся отдельными ключами **`marketplace.min_price.*`**
в `economy_config` (по редкости). Значения по умолчанию совпадают со стоимостью
продления (`renewal.cost.*`), но администратор может менять их независимо.

| Редкость | Минимальная цена (дефолт) | economy_config key |
|----------|---------------------------|--------------------|
| COMMON | 30 | `marketplace.min_price.COMMON` |
| RARE | 60 | `marketplace.min_price.RARE` |
| EPIC | 120 | `marketplace.min_price.EPIC` |
| LEGENDARY | 250 | `marketplace.min_price.LEGENDARY` |

**Обоснование:** нижний порог цены сделки ограничивает перелив фантиков через
символические сделки; при необходимости его можно согласовать с балансом
продления отдельно от `renewal.cost.*`.

---

## 3. Защита от злоупотреблений

### 3.1 История владельцев (провенанс)

Для каждой `user_card` хранится **полная история владельцев**: кто и когда
владел картой, каким способом получил.

### 3.2 Запрет повторной покупки

Пользователь **не может купить карту, которой когда-либо владел**.
Проверка — по записям в `user_card_ownership_history`.

Это исключает циклы перепродаж (A → B → A), которые иначе позволили бы
бесконечно обновлять контракт одной карты между двумя аккаунтами.

В маленьком сообществе карта естественно «стареет»: пул потенциальных
новых покупателей конечен.

### 3.3 Ограничение листинга

| Условие | Иначе |
|---------|-------|
| Карта принадлежит пользователю | 400: «Card not found or not owned» |
| `uses_remaining > 0` | 400: «Cannot sell an expired card» |
| Карта НЕ в команде незавершённой серии | 400: «Cannot sell a card in an active team» |
| Карта НЕ выставлена на маркетплейсе | 400: «Card is already listed» |
| Цена ≥ минимальной для редкости | 400: «Price below minimum for this rarity» |

### 3.4 Ограничения покупки

| Условие | Иначе |
|---------|-------|
| Листинг в статусе ACTIVE | 404: «Listing not found» |
| Покупатель ≠ продавец | 400: «Cannot buy your own card» |
| Покупатель никогда не владел этой картой | 400: «Cannot buy a card you previously owned» |
| Достаточно фантиков у покупателя | 400: «Insufficient balance» |

---

## 4. Экономика

### 4.1 Комиссия

**10%** от цены сделки удерживается с продавца.

```
Покупатель платит:  price
Продавец получает:  price − ⌊price × 0.10⌋
Сгорает (sink):     ⌊price × 0.10⌋
```

Округление вниз — в пользу продавца при нечётных числах.

Пример: карта продана за 150 ₣ → покупатель платит 150, продавец получает 135,
сгорает 15.

### 4.2 Сравнение с альтернативами

Для продавца маркетплейс выгоднее переработки:

| Редкость | Recycle | Min. продажа (после комиссии) | Разница |
|----------|---------|-------------------------------|---------|
| COMMON | 10 | 27 | +17 |
| RARE | 25 | 54 | +29 |
| EPIC | 60 | 108 | +48 |
| LEGENDARY | 200 | 225 | +25 |

Мотивация продавать: получить больше, чем при переработке.
Мотивация покупать: получить свежую карту конкретного игрока без рандома паков.

### 4.3 economy_config ключи

| Ключ | Значение | Описание |
|------|----------|----------|
| `marketplace.commission_percent` | `10` | Комиссия с продажи (%) |
| `marketplace.min_price.COMMON` | `30` | Минимальная цена листинга COMMON |
| `marketplace.min_price.RARE` | `60` | Минимальная цена листинга RARE |
| `marketplace.min_price.EPIC` | `120` | Минимальная цена листинга EPIC |
| `marketplace.min_price.LEGENDARY` | `250` | Минимальная цена листинга LEGENDARY |

Потолок цены — ключи `marketplace.max_price.*` (см. миграции и админку экономики).

### 4.4 Транзакции фантиков

Два новых типа `FantikiTransactionReason`:

| Причина | Сумма | Кто |
|---------|-------|-----|
| `MARKETPLACE_PURCHASE` | −price | Покупатель |
| `MARKETPLACE_SALE` | +(price − commission) | Продавец |

Комиссия не создаёт отдельной транзакции — она просто «исчезает»
(разница между списанием покупателя и начислением продавца).

---

## 5. История владельцев (провенанс)

### 5.1 Назначение

- **Защита:** проверка «владел ли пользователь этой картой» за O(1) запрос
- **Фича:** отображение истории карты в UI («прошла через 4 игрока»)
- **Аналитика:** понимание динамики вторичного рынка

### 5.2 Когда создаётся запись

| Событие | `acquisition_type` |
|---------|--------------------|
| Открытие пака (покупка в магазине) | `PACK_OPENING` |
| Выдача админом | `ADMIN_GRANT` |
| Покупка на маркетплейсе | `MARKETPLACE_PURCHASE` |

Запись создаётся при получении карты — одна строка на одного владельца.

### 5.3 Начальная миграция

Для существующих карт: один `INSERT` — текущий владелец, `acquired_at` из `user_card`,
тип `PACK_OPENING` (большинство карт получены из паков; точный источник
неизвестен для исторических данных).

---

## 6. Лента сделок и уведомления

### 6.1 Лента последних сделок

Публичный эндпоинт: список последних N покупок (по умолчанию 20).
Каждая запись содержит:
- Имя игрока на карте
- Редкость
- Цена продажи
- Время сделки
- Ник покупателя (displayName)

**Не** показываются: ник продавца (приватность), внутренние id.

### 6.2 Уведомления продавцу

При продаже карты — сообщение в Telegram через Bot API:

```
Карта «{playerName}» ({rarity}) куплена за {price} ₣.
Вы получили {sellerAmount} ₣ (комиссия {commission} ₣).
Баланс: {newBalance} ₣.
```

Отправка — асинхронная (`@Async`), через существующий `TelegramBotApiClient`.
Сбой отправки не влияет на сделку.

---

## 7. Модель данных

### 7.1 Новые таблицы

#### marketplace_listing

```sql
CREATE TABLE marketplace_listing (
    id              BIGSERIAL PRIMARY KEY,
    seller_id       BIGINT NOT NULL REFERENCES telegram_user(id),
    user_card_id    BIGINT NOT NULL REFERENCES user_card(id),
    price           BIGINT NOT NULL,
    status          VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    sold_at         TIMESTAMP,
    buyer_id        BIGINT REFERENCES telegram_user(id),

    CONSTRAINT marketplace_listing_price_positive CHECK (price > 0),
    CONSTRAINT marketplace_listing_status_check
        CHECK (status IN ('ACTIVE', 'SOLD', 'CANCELLED'))
);

CREATE UNIQUE INDEX marketplace_listing_active_card
    ON marketplace_listing (user_card_id)
    WHERE status = 'ACTIVE';
```

Partial unique index гарантирует: у одной карты максимум один активный листинг.

#### user_card_ownership_history

```sql
CREATE TABLE user_card_ownership_history (
    id                  BIGSERIAL PRIMARY KEY,
    user_card_id        BIGINT NOT NULL REFERENCES user_card(id),
    telegram_user_id    BIGINT NOT NULL REFERENCES telegram_user(id),
    acquired_at         TIMESTAMP NOT NULL DEFAULT NOW(),
    acquisition_type    VARCHAR(32) NOT NULL,

    CONSTRAINT ownership_acquisition_type_check
        CHECK (acquisition_type IN ('PACK_OPENING', 'ADMIN_GRANT', 'MARKETPLACE_PURCHASE'))
);

CREATE INDEX idx_ownership_history_card_user
    ON user_card_ownership_history (user_card_id, telegram_user_id);
```

Индекс по `(user_card_id, telegram_user_id)` — быстрая проверка
«владел ли пользователь этой картой».

### 7.2 Новые ключи economy_config

```sql
INSERT INTO economy_config (key, value, description) VALUES
    ('marketplace.commission_percent', '10', 'Комиссия маркетплейса (%)');
```

### 7.3 Бэкфилл истории владельцев

```sql
INSERT INTO user_card_ownership_history (user_card_id, telegram_user_id, acquired_at, acquisition_type)
SELECT id, telegram_user_id, acquired_at, 'PACK_OPENING'
FROM user_card;
```

### 7.4 Flyway-миграция

Одна миграция `V{next}__marketplace.sql`:
- CREATE TABLE `marketplace_listing` + partial unique index
- CREATE TABLE `user_card_ownership_history` + index
- INSERT `economy_config` ключ
- Бэкфилл `user_card_ownership_history` из существующих карт

---

## 8. API

### 8.1 User API (TMA)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/marketplace/listings` | Список активных листингов (фильтры, пагинация) |
| GET | `/marketplace/my-listings` | Листинги текущего пользователя (ACTIVE) |
| POST | `/marketplace/listings` | Выставить карту на продажу |
| DELETE | `/marketplace/listings/{id}` | Снять с продажи |
| POST | `/marketplace/listings/{id}/buy` | Купить карту |
| GET | `/marketplace/feed` | Лента последних сделок |

---

**`GET /marketplace/listings`**

Query parameters:
- `fantasyPlayerId` (optional) — фильтр по игроку
- `rarity` (optional) — фильтр по редкости
- `minPrice` / `maxPrice` (optional) — диапазон цены
- `sortBy` (optional) — `price_asc`, `price_desc`, `created_at_desc` (default)
- `page` / `size` (optional) — пагинация (default: page=0, size=20)

Response:
```json
{
  "content": [
    {
      "listingId": 42,
      "price": 150,
      "createdAt": "2026-04-08T14:30:00Z",
      "card": {
        "userCardId": 123,
        "fantasyPlayerId": 5,
        "playerName": "МихалычЪ",
        "playerPhotoUrl": "https://...",
        "rarity": "EPIC",
        "achievements": [
          { "achievementId": "WON_GAME", "name": "Победа", "bonusPoints": 1.0 },
          { "achievementId": "BEST_MOVE", "name": "Лучший ход", "bonusPoints": 1.0 }
        ]
      },
      "seller": {
        "displayName": "Алекс"
      },
      "canBuy": true,
      "canBuyReason": null
    }
  ],
  "totalElements": 84,
  "totalPages": 5,
  "page": 0,
  "size": 20
}
```

Поле `canBuy` — для текущего пользователя: `false` если это его листинг,
если он владел этой картой, или если недостаточно фантиков.
`canBuyReason` — человекочитаемая причина, если `canBuy = false`.

---

**`GET /marketplace/my-listings`**

Response: массив листингов текущего пользователя (ACTIVE).

---

**`POST /marketplace/listings`**

Request:
```json
{
  "userCardId": 123,
  "price": 150
}
```

Response (success): `MarketplaceListingDto` (созданный листинг).

Ошибки:
- `400` — карта не найдена или не принадлежит пользователю
- `400` — `uses_remaining = 0`
- `400` — карта в команде незавершённой серии
- `400` — карта уже выставлена
- `400` — цена ниже минимальной

---

**`DELETE /marketplace/listings/{id}`**

Снятие с продажи. Response: `204 No Content`.

Ошибки:
- `404` — листинг не найден или не принадлежит пользователю
- `400` — листинг не в статусе ACTIVE

---

**`POST /marketplace/listings/{id}/buy`**

Request: пустое тело.

Response (success):
```json
{
  "listing": { ... },
  "card": { ... },
  "pricePaid": 150,
  "sellerReceived": 135,
  "commission": 15,
  "newBalance": 850
}
```

Ошибки:
- `404` — листинг не найден или не ACTIVE
- `400` — покупатель = продавец
- `400` — покупатель ранее владел этой картой
- `400` — недостаточно фантиков

---

**`GET /marketplace/feed`**

Query parameters:
- `limit` (optional, default 20, max 50)

Response:
```json
{
  "items": [
    {
      "playerName": "МихалычЪ",
      "rarity": "EPIC",
      "price": 150,
      "soldAt": "2026-04-08T15:00:00Z",
      "buyerDisplayName": "Борис"
    }
  ]
}
```

---

### 8.2 Admin API

На MVP — без специальных admin-эндпоинтов. Новый ключ `marketplace.commission_percent`
доступен через существующий CRUD `/economy-config`.

В будущем (опционально):
- `GET /admin/marketplace/listings` — все листинги (модерация)
- `DELETE /admin/marketplace/listings/{id}` — принудительное снятие

---

## 9. Backend (сервисный слой)

### 9.1 Новый сервис: `MarketplaceService`

```kotlin
@Service
class MarketplaceService(
    private val listingRepository: MarketplaceListingRepository,
    private val ownershipHistoryRepository: UserCardOwnershipHistoryRepository,
    private val userCardRepository: UserCardRepository,
    private val fantasyTeamCardRepository: FantasyTeamCardRepository,
    private val economyConfigService: EconomyConfigService,
    private val userService: UserService,
    private val telegramNotificationService: TelegramNotificationService,
) {
    @Transactional
    fun createListing(user: TelegramUser, request: CreateListingRequest): MarketplaceListing {
        // 1. Найти UserCard, проверить ownership
        // 2. Проверить uses_remaining > 0
        // 3. Проверить: не в команде незавершённой серии
        // 4. Проверить: нет активного листинга на эту карту
        // 5. Проверить: price >= renewal cost для редкости
        // 6. Создать MarketplaceListing(status = ACTIVE)
    }

    @Transactional
    fun cancelListing(user: TelegramUser, listingId: Long) {
        // 1. Найти листинг, проверить ownership и статус ACTIVE
        // 2. Установить status = CANCELLED
    }

    @Transactional
    fun buyCard(buyer: TelegramUser, listingId: Long): BuyCardResult {
        // 1. Найти листинг, проверить статус ACTIVE (SELECT FOR UPDATE)
        // 2. Проверить buyer != seller
        // 3. Проверить: покупатель не владел этой картой (ownership history)
        // 4. Проверить баланс покупателя
        // 5. Рассчитать комиссию
        // 6. Списать фантики у покупателя (MARKETPLACE_PURCHASE)
        // 7. Начислить продавцу price - commission (MARKETPLACE_SALE)
        // 8. Передать карту: user_card.telegram_user_id = buyer
        // 9. Сбросить контракт: uses_remaining = initial, times_renewed = 0
        // 10. Добавить запись в ownership history (MARKETPLACE_PURCHASE)
        // 11. Обновить листинг: status = SOLD, sold_at, buyer_id
        // 12. Async: отправить уведомление продавцу
    }

    @Transactional(readOnly = true)
    fun getListings(filters: ListingFilters, pageable: Pageable): Page<MarketplaceListingDto>

    @Transactional(readOnly = true)
    fun getMyListings(user: TelegramUser): List<MarketplaceListingDto>

    @Transactional(readOnly = true)
    fun getFeed(limit: Int): List<MarketplaceFeedItemDto>
}
```

### 9.2 Конкурентность при покупке

`SELECT ... FOR UPDATE` на `marketplace_listing` при покупке предотвращает
одновременную покупку одной карты двумя пользователями. Если листинг
уже не ACTIVE — `404`.

### 9.3 Блокировка карты при листинге

Существующие сервисы должны проверять, что карта не выставлена:

| Сервис | Проверка |
|--------|----------|
| `UserFantasyTeamService.attachCards` | Карта не в активном листинге |
| `CardLifecycleService.recycleCard` | Карта не в активном листинге |
| `CardLifecycleService.renewCard` | Карта не в активном листинге |
| `LegendaryUpgradeService.upgradeToLegendary` | Карта не в активном листинге |

Проверка: `MarketplaceListingRepository.existsByUserCardIdAndStatus(cardId, ACTIVE)`.

### 9.4 Запись в ownership history при выдаче карт

Существующий код выдачи карт дополняется записью в `user_card_ownership_history`:

| Место выдачи | `acquisition_type` |
|--------------|--------------------|
| `UserStoreService` (покупка пака) | `PACK_OPENING` |
| `CardService.giveCards` (выдача админом) | `ADMIN_GRANT` |
| `CardService.openPack` (открытие пака через админку) | `PACK_OPENING` |

### 9.5 Новые причины транзакции

```kotlin
enum class FantikiTransactionReason {
    // ... existing ...
    MARKETPLACE_PURCHASE,
    MARKETPLACE_SALE,
}
```

### 9.6 EconomyConfigService — новые методы

```kotlin
fun getMarketplaceCommissionPercent(): Int   // marketplace.commission_percent
```

Минимальная цена читается из существующего `getRenewalCost(rarity)`.

---

## 10. Frontend (TMA)

### 10.1 Новые страницы

| Маршрут | Страница | Описание |
|---------|----------|----------|
| `/marketplace` | `MarketplacePage` | Список листингов, фильтры, лента |
| `/marketplace/my` | `MyListingsPage` | Мои активные листинги |

### 10.2 Навигация

Новый пункт **«Маркетплейс»** в навигации TMA (рядом с «Магазин»).

### 10.3 Страница маркетплейса

**Верхний блок — лента сделок:** горизонтальный скролл или карусель
последних покупок («МихалычЪ EPIC за 150 ₣», «Котов RARE за 80 ₣»).

**Фильтры:**
- По редкости (кнопки-чипы: All / Common / Rare / Epic / Legendary)
- По игроку (селект с поиском)
- Сортировка: цена ↑, цена ↓, новые

**Сетка листингов:** карточка с фото игрока, рамкой по редкости,
ценой, ачивками. Кнопка «Купить за N ₣» или метка «Недоступна»
с причиной (ваша карта / уже владели / не хватает фантиков).

### 10.4 Продажа из коллекции

На странице коллекции (`/cards`) — в модалке карточки и/или в тулбаре
добавляется кнопка **«Продать»** (рядом с «Переработать» и «Продлить»).

Условия показа:
- `uses_remaining > 0`
- Карта не в команде незавершённой серии
- Карта не выставлена на маркетплейсе

По нажатию — модалка с полем цены:
- Минимальная цена предзаполнена
- Показана комиссия и итоговая сумма продавцу
- Кнопка «Выставить на продажу»

### 10.5 Мои листинги

Доступ: кнопка на странице маркетплейса или из профиля.
Список карт, выставленных текущим пользователем.
Для каждой — цена, время листинга, кнопка «Снять с продажи».

### 10.6 История владельцев (провенанс)

В модалке деталей карточки (коллекция, лидерборд) — блок **«История»**:
список владельцев с типом получения и датой.

```
📋 История карточки
  1. Алекс — из пака (12.03.2026)
  2. Борис — куплена на маркетплейсе (25.03.2026)
  3. Вы — куплена на маркетплейсе (08.04.2026)
```

---

## 11. Админка

### 11.1 Economy config

Новый ключ `marketplace.commission_percent` автоматически появится
на странице `/economy`.

### 11.2 Будущее (не MVP)

- Страница модерации маркетплейса
- Статистика: объём торгов, средние цены, топ-карты

---

## 12. Взаимодействие с существующими механиками

| Механика | Поведение |
|----------|-----------|
| Сборка команды | Карта в активном листинге **недоступна** для команды |
| Скоринг | Не затрагивается (карта в листинге не в команде) |
| Финализация серии | Не затрагивается (карта в листинге не в команде) |
| Переработка (recycle) | Карта в активном листинге **недоступна** |
| Продление (renew) | Карта в активном листинге **недоступна** |
| Легендарный апгрейд | Карта в активном листинге **недоступна** |
| Паки | Создание `ownership_history` при выдаче |
| Лимит LEGENDARY в команде | Не затрагивается |
| Ростер-прунинг (`FantasyTeamRosterPruningService`) | Не затрагивается (карта не в команде) |

---

## 13. Порядок реализации

| Блок | Что | Зависимости |
|------|-----|-------------|
| M1 | Flyway: `marketplace_listing`, `user_card_ownership_history`, economy_config ключ, бэкфилл | — |
| M2 | Entity + Repository: `MarketplaceListing`, `UserCardOwnershipHistory`; `FantikiTransactionReason` | M1 |
| M3 | `MarketplaceService`: листинг, покупка, снятие, лента; запись ownership при выдаче карт | M2 |
| M4 | Блокировка карты в листинге (team, recycle, renew, upgrade) | M2 |
| M5 | API: `MarketplaceController` (user endpoints) | M3, M4 |
| M6 | Telegram-уведомление продавцу при продаже | M3 |
| M7 | TMA: страница маркетплейса, фильтры, покупка | M5 |
| M8 | TMA: кнопка «Продать» в коллекции, создание листинга | M5 |
| M9 | TMA: «Мои листинги», снятие с продажи | M5 |
| M10 | TMA: лента сделок | M5 |
| M11 | TMA: провенанс карточки в модалке деталей | M5 |

M3 и M4 могут выполняться параллельно.
M7–M11 могут выполняться параллельно после M5.

---

## 14. Принятые решения

| Вопрос | Решение |
|--------|---------|
| Тип маркетплейса | Продажа за фантики (не обмен, не аукцион) |
| Ценообразование | Продавец задаёт цену, минимум = renewal cost |
| Контракт при покупке | Полный сброс: uses → начальное, times_renewed → 0 |
| Комиссия | 10% с продавца |
| Кто платит комиссию | Продавец (покупатель видит конечную цену) |
| Карта с uses = 0 | Нельзя выставить (только recycle/renew) |
| Карта в команде | Нельзя выставить |
| Листинг | Бессрочный, можно снять |
| Защита от циклов | История владельцев + запрет повторной покупки |
| Провенанс | Полная история как фича (UI + защита) |
| Лента сделок | MVP: последние 20 покупок |
| Уведомления | При продаже — сообщение продавцу в Telegram |
| Admin API | Не нужен на MVP, ключ экономики через CRUD |

---

## 15. На будущее (не MVP)

| Фича | Описание |
|------|----------|
| **Buy orders** | Заявки на покупку: «Ищу EPIC Иванова, готов платить 200 ₣». Автоматический мэтчинг с листингами |
| **Связь с сериями** | Подсветка карт игроков из ростера предстоящей серии |
| **Статистика цен** | Средняя цена продажи по шаблону/редкости за период |
| **Wishlist** | Подписка на появление карты конкретного игрока |
| **Обмен (бартер)** | Прямой обмен карточка на карточку (без фантиков) |
| **Модерация** | Admin API для просмотра и снятия листингов |
| **Срок листинга** | Автоснятие через N дней (если рынок замусорится) |

---

## 16. Пример user journey

> У Алисы есть EPIC-карта «Петров» с 2 использованиями. Она собрала
> легендарную команду и Петров ей больше не нужен. Баланс: 400 ₣.
>
> 1. Алиса открывает коллекцию → нажимает на карту «Петров» → «Продать»
> 2. Минимальная цена: 120 ₣. Алиса ставит 180 ₣
> 3. Видит: «Вы получите 162 ₣ (комиссия 18 ₣)»
> 4. Подтверждает → карта появляется на маркетплейсе
>
> Борис ищет карту Петрова для предстоящей серии. Баланс: 600 ₣.
>
> 5. Борис открывает маркетплейс → фильтр по игроку «Петров»
> 6. Видит EPIC «Петров» за 180 ₣, кнопка «Купить за 180 ₣»
> 7. Подтверждает → карта переходит Борису: 4 использования, 0 продлений
> 8. Баланс Бориса: 420 ₣
>
> Алиса получает уведомление в Telegram:
> «Карта «Петров» (EPIC) куплена за 180 ₣. Вы получили 162 ₣. Баланс: 562 ₣.»
>
> В модалке карточки у Бориса — история:
>   1. Алиса — из пака (01.03.2026)
>   2. Борис — куплена на маркетплейсе (08.04.2026)
