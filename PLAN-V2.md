# Polemica Fantasy V2 — План работ

## Порядок выполнения

```
  B1 (DB + Entities)
       │
       ├──────────────┬──────────────┐
       │              │              │
       ▼              ▼              ▼
  B2 (Achievement  B3 (Fantiki    B4 (Auto-gen
   Refactor +       + Store        Packs +
   Scoring V2)      Backend)       Backend)
       │              │              │
       │              ├──────────────┤
       │              │              │
       ▼              ▼              ▼
  B5 (Admin Frontend)    B6 (TMA Frontend)
                              │
                              ▼
                    B7 (Pack Opening Animation)
```

**Параллельные пары после B1:**
- B2 ‖ B3 ‖ B4 — разные сервисы, минимальное пересечение
- B5 ‖ B6 — разные модули (после зависимостей)
- B7 — чисто фронтенд, может идти параллельно с B5

---

## Agent B1: Schema & Entities (Foundation V2)

**Scope:** Flyway-миграции, обновление JPA entities, обновление enum `Rarity`.

**Deliverables:**
- `V5__fantiki.sql` — `telegram_user.fantiki BIGINT NOT NULL DEFAULT 1000`, таблица `fantiki_transaction` (id, telegram_user_id FK, amount, reason, created_at)
- `V6__achievement_system.sql`:
  - Таблица `achievement` (id VARCHAR PK, name, description, bonus_points DEFAULT 1, occurrence_type, can_appear_on_random_cards)
  - Таблица `achievement_applicable_role` (achievement_id FK, role VARCHAR, PK composite)
  - Seed-данные для всех 9 текущих типов достижений (бонус = 1, occurrence и roles — заглушки)
  - Изменение `card_template_achievement`: `bonus_points` → nullable (NULL = системный, иначе override), переименовать `achievement_type` → `achievement_id` FK
- `V7__auto_packs.sql`:
  - `card_pack`: + `auto_generated BOOLEAN DEFAULT false`, `price_fantiki BIGINT DEFAULT 0`, `use_all_tournament_players BOOLEAN DEFAULT false`
  - Таблица `card_pack_player` (card_pack_id FK, fantasy_player_id FK)
  - `card_pack_rarity_config`: drop `probability`
- `V8__game_score_details.sql`:
  - Таблица `fantasy_team_card_game_score` (fantasy_team_card_id FK, series_game_id FK, base_points, achievement_bonus, rarity_modifier, total_score; UNIQUE)
  - Таблица `fantasy_team_card_game_achievement` (card_game_score_id FK, achievement_id FK, bonus_points)
- Обновлённые JPA entities:
  - `TelegramUser` — поле `fantiki`
  - `FantikiTransaction` (новый entity — аудит начислений/списаний)
  - `Achievement` (новый entity)
  - `AchievementApplicableRole` (новый entity или `@ElementCollection`)
  - `CardTemplateAchievement` — FK на `Achievement` вместо enum; `bonusPoints` nullable (NULL = системный default, иначе override)
  - `CardPack` — новые поля
  - `CardPackPlayer` (новый entity)
  - `CardPackRarityConfig` — убрать `probability`
  - `FantasyTeamCardGameScore` (новый entity)
  - `FantasyTeamCardGameAchievement` (новый entity — обязательная таблица)
  - `Rarity` enum — добавить `scoreModifier` (1.0, 1.1, 1.15, 1.25)
- Удалить `AchievementType` enum (заменяется entity)
- Обновить `AchievementRepository` (или создать новый)

**Outputs:** проект компилируется, миграции проходят, тесты контекста зелёные.

**Dependencies:** нет (стартует первым).

**Estimated effort:** средний.

---

## Agent B2: Achievement Refactor + Scoring V2

**Scope:** переработка системы достижений, новая формула скоринга с модификатором редкости и per-game детализацией.

**Deliverables:**

### Achievement Refactor
- `AchievementDetector` интерфейс: добавить проверку `applicableRoles` **при скоринге** — детектор срабатывает только если роль игрока в данной игре входит в список допустимых ролей достижения (при генерации карт роль не проверяется)
- `AchievementDetectorRegistry`: загружать конфигурацию из БД (`Achievement` entity), а не из enum
- Обновить все 9 детекторов: учёт `occurrenceType`:
  - `ONCE_PER_GAME`: достижение засчитывается максимум 1 раз за игру
  - `MULTIPLE_PER_GAME`: сколько раз сработало, столько раз начисляется бонус

### Scoring V2
- `DefaultScoringService`:
  - Новая формула: `card_game_score = (base_points + Σ(achievement_bonus)) × rarity_modifier`
  - `achievement_bonus` = `CardTemplateAchievement.bonusPoints ?? Achievement.bonusPoints` (per-card override или системный default)
  - При расчёте создавать записи `FantasyTeamCardGameScore` (per-game breakdown) и `FantasyTeamCardGameAchievement` (какие ачивки сработали)
  - `FantasyTeamCard.score` = сумма `total_score` по всем играм
  - Перед повторным расчётом — очищать старые per-game записи

### Тесты
- Unit-тесты на новую формулу с модификатором
- Тесты на `ONCE_PER_GAME` vs `MULTIPLE_PER_GAME`
- Тесты на `applicableRoles` фильтрацию

**Dependencies:** B1 (entities, миграции).

**Estimated effort:** высокий.

---

## Agent B3: Фантики + Store Backend

**Scope:** балансы пользователей, начисление/списание, магазин паков.

**Deliverables:**
- `UserService`:
  - `getBalance(userId)`, `addBalance(userId, amount)`, `deductBalance(userId, amount)` с оптимистичной блокировкой (`@Version` или `UPDATE ... WHERE fantiki >= :amount`)
  - При каждой операции с балансом — запись в `fantiki_transaction` (amount, reason)
  - При создании пользователя: `fantiki = 1000` + транзакция с reason `INITIAL`
- `FantikiTransactionRepository` — для аудита и истории
- Обновить `GET /api/v1/me` DTO: добавить поле `fantiki`
- Admin API:
  - `POST /api/v1/admin/users/{telegramUserId}/give-fantiki` — body: `{ amount: Long }` — начислить фантики
- User API — Store:
  - `GET /api/v1/store/packs` — список доступных паков (active=true, price >= 0); DTO: id, name, priceFantiki, раскладка по редкостям
  - `POST /api/v1/store/packs/{id}/buy` — атомарная транзакция: проверка баланса → списание → открытие пака → возврат выпавших карт
- `UserStoreService`:
  - Логика покупки: `@Transactional`, проверить `fantiki >= price`, вызвать `deductBalance`, вызвать `CardPackService.openPack`
  - Вернуть `PackOpeningResultDto`: список выпавших карт с полной информацией (для анимации на фронте)
- DTO: `StorePackItemDto`, `BuyPackResponseDto`, `GiveFantikiRequest`

**Dependencies:** B1 (entity TelegramUser.fantiki, CardPack.priceFantiki).

**Estimated effort:** средний.

---

## Agent B4: Автогенерация паков Backend

**Scope:** логика создания карточек на лету при открытии автогенерированных паков.

**Deliverables:**
- `CardPackService.openPack()` — ветвление по `autoGenerated`:
  - **false (legacy):** текущая логика — случайный выбор существующих `CardTemplate` по редкости
  - **true (новая):**
    1. Определить пул игроков: `useAllTournamentPlayers` → все `TournamentPlayer` по `tournament_id` пака; иначе → `CardPackPlayer` записи
    2. Для каждого слота из `CardPackRarityConfig`:
       - Выбрать случайного игрока из пула
       - Определить набор достижений: COMMON — без ачивок; RARE — 1 случайная из `canAppearOnRandomCards=true`; EPIC — 2 случайных различных из `canAppearOnRandomCards=true`
       - **Не фильтровать по `applicableRoles` при генерации** — проверка роли происходит только при скоринге
       - **Переиспользовать** существующий `CardTemplate` если найден точный match (тот же игрок + редкость + идентичный набор ачивок); иначе — создать новый
       - LEGENDARY: не участвует (валидация при создании пака — нельзя добавить LEGENDARY в `CardPackRarityConfig` для auto-generated пака)
    3. Создать `UserCard` для каждого шаблона (найденного или созданного)
    4. Вернуть список созданных карт
- Валидация при создании/обновлении пака:
  - `autoGenerated = true` + LEGENDARY в конфиге → ошибка 400
  - `autoGenerated = true` + пустой пул игроков + `useAllTournamentPlayers = false` → ошибка 400
- Admin API (расширение):
  - `POST /api/v1/admin/card-packs` — новые поля: `autoGenerated`, `priceFantiki`, `useAllTournamentPlayers`, `playerIds: List<Long>`
  - `GET /api/v1/admin/card-packs/{id}` — возвращать player pool
  - `PUT /api/v1/admin/card-packs/{id}/players` — обновить пул игроков
- `CardPackPlayerRepository`

**Dependencies:** B1 (entities), B2 (achievement system для random attachment).

**Estimated effort:** высокий.

---

## Agent B5: Admin Frontend V2

**Scope:** обновления админки под новые фичи.

**Deliverables:**

### Справочник достижений
- Новая страница `/achievements`:
  - Таблица: id, name, bonus_points, occurrence_type, applicable_roles, can_appear_on_random_cards
  - Inline editing бонуса, типа, ролей, флага random
  - `GET /api/v1/admin/achievements`, `PUT /api/v1/admin/achievements/{id}`

### Паки V2
- Форма создания/редактирования пака:
  - Чекбокс «Автогенерация»
  - Поле «Стоимость (фантики)»
  - Чекбокс «Все игроки турнира» vs multiselect игроков
  - Количество карт по тирам (без probability; для auto — без LEGENDARY)
  - Визуальная подсказка при auto: «Rare = +1 ачивка, Epic = +2 ачивки»

### Фантики
- На странице User Tools: новая секция «Фантики»
  - Поле telegramUserId + amount + кнопка «Начислить»

### Прочее
- Обновить создание achievement на карточке: select из справочника (без поля bonus — бонус из справочника)
- API client: все новые endpoints

**Dependencies:** B2 (achievement API), B3 (fantiki API), B4 (pack API).

**Estimated effort:** средний.

---

## Agent B6: TMA Frontend V2

**Scope:** фантики в интерфейсе, магазин паков, детализация очков.

**Deliverables:**

### Баланс фантиков
- Глобальный компонент `FantikiBalance` в хедере/углу: иконка + число
- Данные из `GET /me`, обновляются при покупке

### Магазин
- Новая страница `/store`:
  - Список паков: название, цена (фантики), визуализация содержимого (количество карт по редкостям с цветовой индикацией)
  - Кнопка «Купить» с подтверждением
  - При недостаточном балансе — кнопка серая + подсказка
- Навигация: добавить пункт «Магазин» (иконка корзины / монеты)

### Детализация очков (Изменение 6)
- На странице истории фэнтези-команды (или модалка при тапе на карточку):
  - Таблица: строки = игры серии, колонки = карты команды
  - В каждой ячейке: total score; при раскрытии: base_points + Σ achievements + ×modifier
  - Красивое оформление: цветовое кодирование по редкости, подсветка лучших результатов
- API: `GET /api/v1/me/fantasy-teams/{seriesId}/details`

### Обновление имеющихся компонентов
- Карточка в коллекции: отображение достижений по названию (из нового справочника)
- Модификатор редкости: показ бейджа «×1.10» на RARE и т.д. (опционально)

**Dependencies:** B3 (store API, /me с фантиками), B2 (scoring details API).

**Estimated effort:** высокий.

---

## Agent B7: Pack Opening Animation

**Scope:** анимация открытия пака — ключевой UX-элемент.

**Deliverables:**
- Компонент `PackOpening`:
  - Полноэкранный overlay
  - Пак «трясётся» → «разрывается»
  - Карты выпадают одна за другой с задержкой (0.5–1с):
    - COMMON: простой slide-in
    - RARE: flip + голубой glow
    - EPIC: flip + фиолетовый glow + particles
    - (LEGENDARY: если вручную выдан — золотой glow + shake + particles + звук)
  - После всех карт — сводка «Вы получили» с кнопкой «В коллекцию»
- Технологии: CSS animations + Framer Motion (или CSS-only для простоты)
- Переиспользование: работает и при покупке в магазине, и (потенциально) при выдаче админом

**Dependencies:** B6 (интеграция в TMA).

**Estimated effort:** средний (в основном CSS/анимации).

---

## Сводка

| Agent | Scope | Зависимости | Параллельность |
|---|---|---|---|
| **B1** | Schema + Entities | — | Стартует первым |
| **B2** | Achievement + Scoring | B1 | B2 ‖ B3 ‖ B4 |
| **B3** | Фантики + Store API | B1 | B2 ‖ B3 ‖ B4 |
| **B4** | Auto-gen Packs | B1, B2 | после B2 |
| **B5** | Admin UI | B2, B3, B4 | B5 ‖ B6 |
| **B6** | TMA UI | B2, B3 | B5 ‖ B6 |
| **B7** | Анимация паков | B6 | B7 ‖ B5 |

**Критический путь:** B1 → B2 → B4 → B5  
**Всего агентов:** 7  
**Полностью параллельные этапы:** 3 (B2‖B3; B4‖B3; B5‖B6‖B7)

---

## Обновления DESIGN.md

После реализации необходимо обновить следующие секции:
- §2 Glossary — новые термины
- §4.1 ERD — новые связи
- §4.2 Core Entities — все изменённые и новые таблицы
- §5 Achievement System — переработка
- §5.3 Score Calculation Formula — rarity modifier
- §6.1 User API — store, details, fantiki
- §6.2 Admin API — achievements, fantiki, packs V2
- §10 Agent Work Split — обновить под V2
