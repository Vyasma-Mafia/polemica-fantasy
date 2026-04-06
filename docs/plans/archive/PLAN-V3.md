# Polemica Fantasy V3 — Экономика: Контракты + Цикл фантиков

> **Архив:** план реализован. Актуальный SDD: [`../../architecture/DESIGN.md`](../../architecture/DESIGN.md).

## Мотивация

Текущая проблема: карточки вечные, после сбора топ-колоды нет причины покупать новые паки.
Решение: карточки имеют ограниченное число использований (контракт), а экономика фантиков
замыкается через награды за лидерборд и переработку карт.

---

## Ключевые механики

### 1. Контракт карточки (uses_remaining)

Каждая карточка имеет лимит использований. Одно использование = участие в одной **серии**
(не в одной игре). После исчерпания использований карточка становится «истёкшей»: видна
в коллекции, но недоступна для команд.

| Редкость   | Использований |
|------------|---------------|
| COMMON     | 2             |
| RARE       | 3             |
| EPIC       | 4             |
| LEGENDARY  | 5             |

**Списание использования** — при **финализации серии** (новый шаг после скоринга). Админ
сначала считает очки (можно пересчитывать), затем «финализирует» серию → использования
декрементятся, награды начисляются, серия помечается как финализированная.

### 2. Переработка карт (Recycle)

Игрок может **переработать** любую карточку и получить фантики. Карточка удаляется,
транзакция с reason `CARD_RECYCLE` фиксируется в аудите.

| Редкость   | Фантики за переработку |
|------------|------------------------|
| COMMON     | 10                     |
| RARE       | 25                     |
| EPIC       | 60                     |
| LEGENDARY  | 200                    |

**Ограничение:** нельзя переработать карту, которая находится в команде незавершённой серии.

### 3. Продление контракта (Renewal)

Игрок может **продлить** истёкшую карточку (uses_remaining = 0), заплатив фантики.
Использования восстанавливаются до дефолта для редкости. Максимум 2 продления на карту.

| Редкость   | Стоимость продления |
|------------|---------------------|
| COMMON     | 30                  |
| RARE       | 60                  |
| EPIC       | 120                 |
| LEGENDARY  | 250                 |

### 4. Награды за лидерборд серии

При финализации серии участники получают фантики в зависимости от позиции:

| Позиция      | Фантики |
|--------------|---------|
| 1 место      | 100     |
| 2 место      | 70      |
| 3 место      | 50      |
| 4–10 место   | 30      |
| 11+ (участие)| 15      |

### 5. Баланс экономики

**Один пак (200₣):** 3 COMMON + 2 RARE + 1 EPIC = 16 card-uses ≈ 5 серий.

**Цикл игрока с 1000₣ стартовых:**
- Покупает 5 паков → 30 карт, 80 card-uses → хватает на ~26 серий
- После истечения перерабатывает: 5 × (3×10 + 2×25 + 1×60) = 700₣
- Награды за ~26 серий (~30₣ в среднем): ~780₣
- Итого после первого цикла: **1480₣** → 7 паков → экономика растёт

Все числа — значения по умолчанию. Админ может менять через таблицу `economy_config`.

---

## Жизненный цикл серии (обновлённый)

```
UPCOMING → ACTIVE → SCORING → FINALIZED (новый!) → FINISHED
                      │           │
                      │     ┌─────┴─────┐
                      │     │  Декремент │
                      │     │  uses      │
                      │     │  Награды   │
                      │     └───────────┘
                      │
              calculateScores (можно повторять)
```

Новый статус `FINALIZED` между `SCORING` и `FINISHED` или эндпоинт `POST .../series/{id}/finalize`
который:
1. Проверяет, что серия ещё не финализирована (`series.finalized = false`)
2. Декрементит `uses_remaining` для всех карт в командах серии
3. Начисляет награды за лидерборд
4. Устанавливает `series.finalized = true`

Альтернатива: не добавлять новый статус серии, а добавить boolean `finalized` к Series.
Это проще и не ломает существующий lifecycle. **Выбираем этот подход.**

---

## Порядок выполнения

```
  C1 (DB + Entities)
       │
       ├──────────────────┬────────────────┐
       │                  │                │
       ▼                  ▼                │
  C2 (Card Lifecycle    C3 (Economy       │
   + Series Rewards      Config Admin     │
   Backend)              Backend)          │
       │                  │                │
       └────────┬─────────┘                │
                │                          │
        ┌───────┴───────┐                  │
        ▼               ▼                  │
   C4 (Admin       C5 (TMA ◄──────────────┘
    Frontend)       Frontend)
```

**Параллельные пары после C1:** C2 ‖ C3
**Параллельные пары после C2+C3:** C4 ‖ C5

---

## Agent C1: Schema + Entities (Foundation V3)

**Scope:** Flyway V13, обновление JPA entities, новые DTO.

**Deliverables:**

### Flyway V13__economy_contracts.sql

```sql
-- 1. Контракт карточки
ALTER TABLE user_card ADD COLUMN uses_remaining INT NOT NULL DEFAULT 3;
ALTER TABLE user_card ADD COLUMN times_renewed INT NOT NULL DEFAULT 0;

-- 2. Финализация серии
ALTER TABLE series ADD COLUMN finalized BOOLEAN NOT NULL DEFAULT false;

-- 3. Конфигурация экономики (key-value, редактируется через админку)
CREATE TABLE economy_config (
    key VARCHAR(64) PRIMARY KEY,
    value VARCHAR(256) NOT NULL,
    description TEXT
);

-- Seed: использования по редкостям
INSERT INTO economy_config (key, value, description) VALUES
('card.uses.COMMON', '2', 'Кол-во использований для COMMON карт'),
('card.uses.RARE', '3', 'Кол-во использований для RARE карт'),
('card.uses.EPIC', '4', 'Кол-во использований для EPIC карт'),
('card.uses.LEGENDARY', '5', 'Кол-во использований для LEGENDARY карт'),
-- Переработка
('recycle.value.COMMON', '10', 'Фантики за переработку COMMON'),
('recycle.value.RARE', '25', 'Фантики за переработку RARE'),
('recycle.value.EPIC', '60', 'Фантики за переработку EPIC'),
('recycle.value.LEGENDARY', '200', 'Фантики за переработку LEGENDARY'),
-- Продление
('renewal.cost.COMMON', '30', 'Стоимость продления COMMON'),
('renewal.cost.RARE', '60', 'Стоимость продления RARE'),
('renewal.cost.EPIC', '120', 'Стоимость продления EPIC'),
('renewal.cost.LEGENDARY', '250', 'Стоимость продления LEGENDARY'),
('renewal.max_times', '2', 'Максимум продлений на карту'),
-- Награды лидерборда
('series.reward.1', '100', 'Награда за 1 место'),
('series.reward.2', '70', 'Награда за 2 место'),
('series.reward.3', '50', 'Награда за 3 место'),
('series.reward.top10', '30', 'Награда за 4-10 место'),
('series.reward.participation', '15', 'Награда за участие (11+)');

-- 4. Установить uses_remaining для существующих карт по их редкости
UPDATE user_card uc
SET uses_remaining = CASE
    WHEN ct.rarity = 'COMMON' THEN 2
    WHEN ct.rarity = 'RARE' THEN 3
    WHEN ct.rarity = 'EPIC' THEN 4
    WHEN ct.rarity = 'LEGENDARY' THEN 5
    ELSE 3
END
FROM card_template ct
WHERE uc.card_template_id = ct.id;
```

### JPA Entities

- **`UserCard`** — новые поля: `usesRemaining: Int`, `timesRenewed: Int`
- **`Series`** — новое поле: `finalized: Boolean = false`
- **`EconomyConfig`** (новый entity) — `key: String` (PK), `value: String`, `description: String?`
- **`EconomyConfigRepository`** — `findByKey`, `findAll`
- **`FantikiTransactionReason`** — новые значения: `SERIES_REWARD`, `CARD_RECYCLE`, `CARD_RENEWAL`

### DTO обновления

- **`UserCardItemDto`** — добавить `usesRemaining: Int`, `timesRenewed: Int`
- **`UserCardItemMapping.toUserCardItemDto()`** — маппить новые поля

**Outputs:** проект компилируется, миграция проходит, тесты контекста зелёные.

**Dependencies:** нет (стартует первым).

---

## Agent C2: Card Lifecycle + Series Rewards (Backend)

**Scope:** бизнес-логика контрактов, переработки, продления, финализации серии с наградами.

**Deliverables:**

### EconomyConfigService

- `fun getInt(key: String): Int` / `fun getLong(key: String): Long` — чтение значения из `economy_config`
- `fun getUsesForRarity(rarity: Rarity): Int` — `card.uses.{rarity}`
- `fun getRecycleValue(rarity: Rarity): Long` — `recycle.value.{rarity}`
- `fun getRenewalCost(rarity: Rarity): Long` — `renewal.cost.{rarity}`
- `fun getMaxRenewals(): Int` — `renewal.max_times`
- `fun getSeriesReward(position: Int, totalParticipants: Int): Long` — по позиции из конфига
- Кэшировать значения в памяти, инвалидировать при обновлении через админку

### CardLifecycleService (новый)

**Переработка:**
```kotlin
@Transactional
fun recycleCard(user: TelegramUser, userCardId: Long): RecycleResultDto
```
- Проверить, что карта принадлежит пользователю
- Проверить, что карта не в команде незавершённой серии:
  `SELECT 1 FROM fantasy_team_card ftc
   JOIN fantasy_team ft ON ftc.fantasy_team_id = ft.id
   JOIN series s ON ft.series_id = s.id
   WHERE ftc.user_card_id = :cardId AND s.finalized = false`
- Получить recycle value из `EconomyConfigService`
- Начислить фантики: `userService.addBalance(...)` с reason `CARD_RECYCLE`
- Удалить `UserCard`
- Вернуть `RecycleResultDto(fantikiEarned, newBalance)`

**Продление:**
```kotlin
@Transactional
fun renewCard(user: TelegramUser, userCardId: Long): RenewResultDto
```
- Проверить принадлежность
- Проверить `usesRemaining == 0` (только истёкшие карты)
- Проверить `timesRenewed < maxRenewals`
- Получить стоимость из `EconomyConfigService`
- Списать фантики с reason `CARD_RENEWAL`
- Установить `usesRemaining = getUsesForRarity(rarity)`, инкрементить `timesRenewed`
- Вернуть `RenewResultDto(cost, newBalance, newUsesRemaining)`

### SeriesFinalizationService (новый)

```kotlin
@Transactional
fun finalizeSeries(seriesId: Long): SeriesFinalizationResultDto
```
1. Загрузить серию, проверить `finalized == false`
2. Загрузить все `FantasyTeam` серии с карточками (через существующий `findAllWithCardsForScoring`)
3. **Декремент использований:** для каждой `FantasyTeamCard` → `userCard.usesRemaining = max(0, usesRemaining - 1)`
4. **Начисление наград:** отсортировать команды по `totalScore` desc → для каждой позиции:
   - Получить reward из `EconomyConfigService.getSeriesReward(position)`
   - `userService.addBalance(team.telegramUser, reward, SERIES_REWARD)`
5. `series.finalized = true`
6. Вернуть `SeriesFinalizationResultDto(rewardsDistributed: Int, cardsDecremented: Int)`

### Обновления существующего кода

- **`CardPackService.openPack()`** — при создании `UserCard`:
  `usesRemaining = economyConfigService.getUsesForRarity(cfg.rarity)`
- **`CardService.openPack()` / `giveCards()`** — аналогично, задавать `usesRemaining`
- **`UserFantasyTeamService.attachCards()`** — добавить проверку:
  `if (uc.usesRemaining <= 0) throw 400 "Card $ucId has no remaining uses"`
- **`GET /me/cards`** — не фильтровать expired (показывать все), но фронт различает по `usesRemaining`

### User API endpoints (новые)

| Method | Path | Description |
|--------|------|-------------|
| POST | `/me/cards/{id}/recycle` | Переработать карту → получить фантики |
| POST | `/me/cards/{id}/renew` | Продлить контракт карты → списать фантики |
| GET | `/me/economy-info` | Текущие значения экономики (uses, recycle, renewal, rewards) для UI |

### Admin API endpoint (новый)

| Method | Path | Description |
|--------|------|-------------|
| POST | `/admin/series/{id}/finalize` | Финализировать серию: декремент uses + награды |

### DTO (новые)

- `RecycleResultDto(fantikiEarned: Long, newBalance: Long)`
- `RenewResultDto(cost: Long, newBalance: Long, newUsesRemaining: Int)`
- `SeriesFinalizationResultDto(rewardsDistributed: Int, cardsDecremented: Int)`
- `EconomyInfoDto(usesPerRarity: Map<Rarity, Int>, recycleValues: Map<Rarity, Long>, renewalCosts: Map<Rarity, Long>, maxRenewals: Int, seriesRewards: List<RewardTierDto>)`
- `RewardTierDto(label: String, fantiki: Long)`

### Тесты

- Unit: `CardLifecycleService` — recycle (success, card in active team → 400, not owned → 400)
- Unit: `CardLifecycleService` — renew (success, not expired → 400, max renewals → 400, insufficient balance → 400)
- Unit: `SeriesFinalizationService` — finalize (uses decremented, rewards distributed, idempotent check)
- Integration: полный цикл buy → use in team → finalize → uses decremented → recycle → balance check

**Dependencies:** C1 (entities, миграция).

---

## Agent C3: Economy Config Admin (Backend)

**Scope:** CRUD для `economy_config`, интеграция с `EconomyConfigService`.

**Deliverables:**

### Admin API endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/admin/economy-config` | Все параметры экономики |
| PUT | `/admin/economy-config/{key}` | Обновить значение параметра |
| PUT | `/admin/economy-config` | Bulk-обновление (массив {key, value}) |

### Логика

- При обновлении — инвалидировать кэш `EconomyConfigService`
- Валидация: value должно быть числом (для числовых ключей)
- Не разрешать создание новых ключей (только обновление seed-данных)

### DTO

- `EconomyConfigItemDto(key: String, value: String, description: String?)`
- `UpdateEconomyConfigRequest(value: String)`
- `BulkUpdateEconomyConfigRequest(items: List<EconomyConfigItemDto>)`

### Тесты

- Integration: GET → обновить через PUT → GET → проверить новое значение
- Валидация: невалидное значение → 400

**Dependencies:** C1 (entity `EconomyConfig`).

---

## Agent C4: Admin Frontend V3

**Scope:** UI для финализации серии и настройки экономики.

**Deliverables:**

### Страница Economy Config (`/economy`)

- Таблица: ключ | описание | значение (editable)
- Группировка по категориям (Card Uses, Recycle, Renewal, Rewards)
- Inline-редактирование + кнопка «Сохранить всё»
- API client: `GET/PUT /admin/economy-config`

### Кнопка «Финализировать серию»

- На странице серии (рядом с «Calculate Scores»)
- Disabled если `series.finalized = true`
- Подтверждение: «Будут списаны использования карт и начислены награды. Это нельзя отменить.»
- После финализации: показать результат (сколько наград, сколько карт обновлено)
- API client: `POST /admin/series/{id}/finalize`

### Обновления DTO серии

- `SeriesDto` — добавить поле `finalized: Boolean`
- Отображать бейдж «Финализирована» в списке серий

**Dependencies:** C2 (finalize endpoint), C3 (economy config API).

---

## Agent C5: TMA Frontend V3

**Scope:** отображение использований, переработка, продление карт, информация о наградах.

**Deliverables:**

### Карточка — отображение использований

- На каждой карте в коллекции, сборке команды: бейдж «⚡2/3» (осталось / всего)
- Истёкшие карты (uses = 0): затемнение, красный бейдж «Истекла», нельзя выбрать в команду
- В модалке карточки: полная информация (осталось, использовано, продлений)

### Переработка

- На карточке в коллекции: кнопка «Переработать» (или swipe-action)
- Модалка подтверждения: «Вы получите X₣ за эту карту. Карта будет уничтожена.»
- После переработки: анимация получения фантиков, обновление баланса
- API: `POST /me/cards/{id}/recycle`
- Блокировка кнопки если карта в активной команде (показать tooltip)

### Продление

- На истёкшей карточке: кнопка «Продлить за X₣»
- Показать оставшиеся продления: «Продление 1 из 2»
- Модалка подтверждения: стоимость + итог
- После продления: обновить бейдж использований
- API: `POST /me/cards/{id}/renew`
- Disabled при недостаточном балансе (серая кнопка + tooltip)

### Информация об экономике

- Новый экран (из меню или тултип): правила экономики
  - Таблица использований по редкостям
  - Таблица наград за лидерборд
  - Цены переработки и продления
- Данные из `GET /me/economy-info`

### Обновления существующих экранов

- **Коллекция (`/cards`):**
  - Фильтр/таб: «Активные» / «Истёкшие» / «Все»
  - Сортировка по оставшимся использованиям
- **Сборка команды (`TeamPage`):**
  - Не показывать карты с `usesRemaining = 0` (или показывать серыми с лейблом)
  - Предупреждение если у карты осталось 1 использование: «Последнее использование!»
- **Лидерборд серии:**
  - После финализации серии: показать «Вы получили X₣» (одноразовый toast)
- **Хедер / баланс фантиков:**
  - Анимация изменения при получении/трате (count-up/down)

### TypeScript типы

- Обновить `UserCardItem`: добавить `usesRemaining: number`, `timesRenewed: number`
- Новые типы: `RecycleResult`, `RenewResult`, `EconomyInfo`, `RewardTier`
- Новые API-функции в `api/` модуле

**Dependencies:** C1 (DTO с usesRemaining), C2 (API endpoints).

---

## Сводка

| Agent | Scope | Зависимости | Параллельность |
|-------|-------|-------------|----------------|
| **C1** | Schema + Entities | — | Стартует первым |
| **C2** | Card Lifecycle + Rewards | C1 | C2 ‖ C3 |
| **C3** | Economy Config Admin | C1 | C2 ‖ C3 |
| **C4** | Admin Frontend | C2, C3 | C4 ‖ C5 |
| **C5** | TMA Frontend | C1, C2 | C4 ‖ C5 |

**Критический путь:** C1 → C2 → C5
**Всего агентов:** 5
**Полностью параллельные этапы:** 2 (C2‖C3; C4‖C5)

---

## Обновления DESIGN.md после реализации

Целевой файл: [`../../architecture/DESIGN.md`](../../architecture/DESIGN.md) (секции ниже там поддерживаются).

- §2 Glossary — термины: Card Contract, Recycle, Renewal, Finalize, Economy Config
- §4.1 ERD — `user_card` (новые поля), `economy_config` (новая таблица), `series.finalized`
- §4.2 Core Entities — обновлённые таблицы
- §6.1 User API — recycle, renew, economy-info
- §6.2 Admin API — finalize, economy-config CRUD
- §10 Implementation History — Phase 3 (V3)

---

## Обновления Memory Bank после реализации

- `activeContext.md` — текущий фокус V3
- `progress.md` — добавить реализованное
- `systemPatterns.md` — паттерн economy config, card lifecycle
- `productContext.md` — обновить UX с контрактами
