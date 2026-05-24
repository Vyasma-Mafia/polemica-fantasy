# Polemica Fantasy — V2 Changes Specification

> **Архив:** спецификация отражает этап V2. Актуальный SDD: [`../../architecture/DESIGN.md`](../../architecture/DESIGN.md).

Документ описывает 6 концептуальных изменений в системе и конкретные точки, которые затрагиваются в каждом слое (БД, бэкенд, TMA, админка).

> Миграция данных не нужна — база пересоздаётся.

---

## Принятые архитектурные решения

| # | Вопрос | Решение |
|---|---|---|
| 1 | Бонус за перк — глобальный или per-card? | **Системный default с override на карточке.** `CardTemplatePerk.bonus_points` nullable: NULL = `Perk.bonus_points`, иначе override |
| 2 | Автогенерация: новый CardTemplate или переиспользовать? | **Переиспользовать** существующий если совпадает (игрок + редкость + набор перков); иначе — создать новый |
| 3 | Убираем probability из CardPackRarityConfig? | **Да, убираем совсем.** Все паки задают точное количество карт по редкостям |
| 4 | Таблица детализации перков per-game? | **Да, обязательная** таблица `fantasy_team_card_game_perk` |
| 5 | Фильтрация по applicableRoles при генерации карт? | **Нет.** При генерации не фильтруем — любая перк с `canAppearOnRandomCards=true` может попасть. Проверка роли — только при скоринге |
| 6 | Таблица аудита транзакций фантиков? | **Да, обязательная** таблица `fantiki_transaction` |

---

## Изменение 1: Внутриигровая валюта «Фантики»

### Суть
Появляется баланс пользователя в «фантиках». Стартовый баланс — 1000. Баланс отображается постоянно в TMA. Админ может начислять фантики.

### Что меняется

#### БД
- **`telegram_user`**: новая колонка `fantiki BIGINT NOT NULL DEFAULT 1000`
- Таблица **`fantiki_transaction`** для аудита:

| Колонка | Тип | Описание |
|---|---|---|
| `id` | BIGSERIAL PK | |
| `telegram_user_id` | BIGINT FK → telegram_user | |
| `amount` | BIGINT NOT NULL | Положительный = начисление, отрицательный = списание |
| `reason` | VARCHAR NOT NULL | `INITIAL`, `ADMIN_GRANT`, `PACK_PURCHASE` |
| `created_at` | TIMESTAMP NOT NULL DEFAULT now() | |

#### Backend
| Файл / Область | Изменение |
|---|---|
| `TelegramUser` entity | Новое поле `fantiki: Long = 1000` |
| `UserService` | Методы `getBalance()`, `deductBalance()`, `addBalance()` с оптимистичной блокировкой |
| User DTO (`/me`) | Добавить `fantiki` в ответ `GET /me` |
| Admin API | Новый endpoint `POST /api/v1/admin/users/{telegramUserId}/give-fantiki` (body: `amount`) |
| `TournamentAdminController` / new controller | Обработка нового эндпоинта |
| Flyway | Новая миграция: `ALTER TABLE telegram_user ADD COLUMN fantiki ...` |

#### TMA (polemica-fantasy-webapp)
| Область | Изменение |
|---|---|
| Глобальный layout | Постоянный индикатор баланса фантиков в углу экрана (хедер/навбар) |
| API client | Расширить тип `/me` ответа полем `fantiki` |
| Состояние | Баланс обновляется при покупке пака и при загрузке профиля |

#### Admin (polemica-fantasy-admin)
| Область | Изменение |
|---|---|
| User Tools | Новая кнопка/форма «Начислить фантики» (поле: telegramUserId, amount) |
| API client | Новый вызов `POST /admin/users/{id}/give-fantiki` |

---

## Изменение 2: Расширение системы перков

### Суть
Перки становятся полноценными системными сущностями с метаданными: бонус, тип повторяемости, допустимые роли, возможность появления на случайных картах.

### Что меняется

#### БД
- Новая таблица **`perk`** (справочник перков):

| Колонка | Тип | Описание |
|---|---|---|
| `id` | VARCHAR PK | Ключ (`SHERIFF_FOUND_BLACK`, `WON_GAME`, …) |
| `name` | VARCHAR | Отображаемое имя |
| `description` | TEXT | Описание |
| `bonus_points` | DOUBLE NOT NULL DEFAULT 1 | Системный бонус (задаётся вручную позже) |
| `occurrence_type` | VARCHAR NOT NULL | `ONCE_PER_GAME` / `MULTIPLE_PER_GAME` |
| `can_appear_on_random_cards` | BOOLEAN NOT NULL DEFAULT false | Может ли выпасть на автосгенерированной карте |

- Новая таблица **`perk_applicable_role`**:

| Колонка | Тип | Описание |
|---|---|---|
| `perk_id` | VARCHAR FK → perk | |
| `role` | VARCHAR | `DON`, `MAFIA`, `PEACE`, `SHERIFF` |
| PK | (perk_id, role) | |

- **`card_template_perk`**: 
  - `bonus_points` становится **nullable** (NULL = используется `perk.bonus_points`; если задан — override системного значения)
  - Переименовать `perk_type` → `perk_id` (FK → `perk`)

#### Backend
| Файл / Область | Изменение |
|---|---|
| `PerkType` enum | Заменяется на JPA entity `Perk` со всеми полями |
| Новый entity `Perk` | Поля: id, name, description, bonusPoints, occurrenceType, canAppearOnRandomCards |
| Новый entity `PerkApplicableRole` | Или embedded collection в Perk |
| `CardTemplatePerk` entity | FK на `Perk` вместо enum; `bonusPoints` nullable (NULL = системный, иначе override) |
| `PerkRepository` | CRUD для справочника |
| `PerkDetector` interface | Добавить проверку `applicableRoles` — детектор срабатывает только если роль игрока в списке |
| `DefaultScoringService` | `bonusPoints` = `CardTemplatePerk.bonusPoints ?? Perk.bonusPoints` (override или системный); учитывать `occurrenceType` (ONCE — максимум 1 раз за игру) |
| Admin API | `GET /api/v1/admin/perks` — список всех перков; `PUT /api/v1/admin/perks/{id}` — редактирование бонуса, ролей и т.д. |
| Flyway | Миграция: таблицы `perk`, `perk_applicable_role`; заполнение seed-данными; изменение `card_template_perk` |

#### TMA
| Область | Изменение |
|---|---|
| Карточка | Отображение перков с названием (вместо enum-ключа) |

#### Admin
| Область | Изменение |
|---|---|
| Новая страница | Справочник перков: таблица с редактированием бонуса, типа, ролей, флага random |
| Создание карточки | При добавлении perk выбор из справочника (без ввода bonus — он в справочнике) |

---

## Изменение 3: Паки с автогенерацией карт

### Суть
Можно создать пак, при открытии которого карточки генерируются автоматически. Задаётся пул игроков (конкретные или «все игроки турнира»), количество карт каждого тира. На Rare — 1 случайное перк, Epic — 2, Legendary не генерируются.

### Что меняется

#### БД
- **`card_pack`**: новые колонки:

| Колонка | Тип | Описание |
|---|---|---|
| `auto_generated` | BOOLEAN NOT NULL DEFAULT false | Автогенерация карт при открытии |
| `price_fantiki` | BIGINT DEFAULT 0 | Стоимость в фантиках (0 = бесплатно / выдаётся админом) |
| `use_all_tournament_players` | BOOLEAN DEFAULT false | Генерировать из всех игроков турнира |

- Новая таблица **`card_pack_player`** (пул игроков, если `use_all_tournament_players = false`):

| Колонка | Тип | Описание |
|---|---|---|
| `id` | BIGSERIAL PK | |
| `card_pack_id` | BIGINT FK → card_pack | |
| `fantasy_player_id` | BIGINT FK → fantasy_player | |

- **`card_pack_rarity_config`**: 
  - Убрать `probability` (вероятность больше не нужна — задаётся точное количество карт каждого тира)
  - Оставить `cards_count` — количество карт данной редкости в паке
  - Для автогенерированных паков LEGENDARY не должен быть в конфигурации (валидация)

#### Backend
| Файл / Область | Изменение |
|---|---|
| `CardPack` entity | Новые поля: `autoGenerated`, `priceFantiki`, `useAllTournamentPlayers` |
| Новый entity `CardPackPlayer` | Связь пак ↔ fantasy_player |
| `CardPackRarityConfig` entity | Убрать `probability`, оставить `cardsCount` |
| `CardPackService` | Новая логика открытия автогенерированного пака: 1) Определить пул игроков 2) Для каждого слота редкости: выбрать случайного игрока → определить набор перков (RARE: 1, EPIC: 2 из `canAppearOnRandomCards=true`, без фильтрации по `applicableRoles` — роль проверяется при скоринге) → найти существующий `CardTemplate` с совпадением (игрок + редкость + набор перков) или создать новый → создать `UserCard` |
| Admin API | Расширить `POST /card-packs`: поля `autoGenerated`, `priceFantiki`, `playerIds`, `useAllTournamentPlayers` |
| Admin API | `POST /card-packs/{id}/players` — назначить игроков в пул пака |
| Flyway | Миграция: новые колонки и таблица |

#### Admin
| Область | Изменение |
|---|---|
| Создание/редактирование пака | Чекбокс «Автогенерация», поле цены, выбор игроков или «все игроки турнира», количество карт по тирам |

---

## Изменение 4: Магазин паков

### Суть
Пользователь покупает паки за фантики через TMA. Паки красиво открываются с анимацией.

### Что меняется

#### Backend
| Файл / Область | Изменение |
|---|---|
| User API | `GET /api/v1/store/packs` — доступные паки (active + price > 0 или free + отфильтрованные); в ответе: id, name, price, rarity breakdown |
| User API | `POST /api/v1/store/packs/{id}/buy` — покупка: проверить баланс → списать фантики → открыть пак → вернуть список выпавших карт |
| `UserStoreService` | Логика покупки, атомарная транзакция (списание + открытие) |
| DTO | `StorePackDto`, `PackOpeningResultDto` (список карт с полной информацией: имя игрока, фото, редкость, перки) |

#### TMA
| Область | Изменение |
|---|---|
| Новая страница «Магазин» | Список доступных паков с ценами и визуализацией содержимого (сколько карт какой редкости) |
| Кнопка «Купить» | Подтверждение покупки, проверка баланса |
| **Анимация открытия пака** | Ключевой UX-элемент: карточки появляются одна за другой с эффектами (glow по редкости, flip, slide-in). Стек анимаций: CSS transitions / Framer Motion / Lottie |
| Обновление баланса | После покупки баланс в хедере обновляется |
| Навигация | Добавить пункт «Магазин» в навигацию |

---

## Изменение 5: Модификатор редкости в формуле очков

### Суть
Добавляется множитель очков по редкости карточки.

### Формула (было → стало)

**Было:**
```
card_game_score = player_base_points + Σ(perk_bonus)
```

**Стало:**
```
card_game_score = (player_base_points + Σ(perk_bonus)) × rarity_modifier
```

| Rarity | Modifier |
|---|---|
| COMMON | 1.00 |
| RARE | 1.10 |
| EPIC | 1.15 |
| LEGENDARY | 1.25 |

### Что меняется

#### Backend
| Файл / Область | Изменение |
|---|---|
| `Rarity` enum | Добавить поле `scoreModifier: Double` (`COMMON(1.0), RARE(1.1), EPIC(1.15), LEGENDARY(1.25)`) |
| `DefaultScoringService` | Умножать `card_game_score` на `rarity.scoreModifier` |
| DESIGN.md §5.3 | Обновить формулу |

#### TMA
| Область | Изменение |
|---|---|
| Отображение очков | Показывать модификатор редкости в детализации (опционально) |

---

## Изменение 6: Детализация очков карточки по играм

### Суть
Пользователь видит, сколько каждая карточка набрала за каждую игру серии с разбивкой.

### Что меняется

#### БД
- Новая таблица **`fantasy_team_card_game_score`**:

| Колонка | Тип | Описание |
|---|---|---|
| `id` | BIGSERIAL PK | |
| `fantasy_team_card_id` | BIGINT FK → fantasy_team_card | |
| `series_game_id` | BIGINT FK → series_game | |
| `base_points` | DOUBLE | Базовые очки игрока |
| `perk_bonus` | DOUBLE | Суммарный бонус за перки |
| `rarity_modifier` | DOUBLE | Множитель редкости |
| `total_score` | DOUBLE | Итого за игру |
| UNIQUE | (fantasy_team_card_id, series_game_id) | |

- Таблица **`fantasy_team_card_game_perk`** для детализации каких именно перков сработало:

| Колонка | Тип | Описание |
|---|---|---|
| `id` | BIGSERIAL PK | |
| `card_game_score_id` | BIGINT FK → fantasy_team_card_game_score | |
| `perk_id` | VARCHAR FK → perk | |
| `bonus_points` | DOUBLE | Бонус за конкретное перк |

#### Backend
| Файл / Область | Изменение |
|---|---|
| Новый entity `FantasyTeamCardGameScore` | Хранение per-game breakdown |
| Новый entity `FantasyTeamCardGamePerk` | Какие перки сработали в каждой игре |
| `DefaultScoringService` | При скоринге сохранять не только total в `FantasyTeamCard.score`, но и per-game записи |
| User API | `GET /api/v1/me/fantasy-teams/{seriesId}/details` — полная детализация: по каждой карте → по каждой игре → base/perks/modifier/total |
| DTO | `FantasyTeamDetailDto`, `CardGameScoreDto` |

#### TMA
| Область | Изменение |
|---|---|
| История фэнтези / детализация | Экран с таблицей: строки = игры серии, колонки = карточки; в каждой ячейке — очки с возможностью раскрытия (base + perks + modifier) |
| Карточка в команде | При тапе — модалка с per-game breakdown |

---

## Сводная таблица затронутых файлов DESIGN.md

| Секция DESIGN.md | Затронутые изменения |
|---|---|
| §2 Glossary | Добавить: Фантики, Perk (расширить), CardPack (расширить), Store |
| §4.1 ERD | Новые связи: telegram_user.fantiki, perk, card_pack_player, fantasy_team_card_game_score |
| §4.2 Core Entities | Изменить: TelegramUser, CardPack, CardPackRarityConfig, CardTemplatePerk. Добавить: Perk, PerkApplicableRole, CardPackPlayer, FantasyTeamCardGameScore |
| §5 Perk System | Полная переработка: справочник, occurrence_type, applicable_roles |
| §5.3 Score Formula | Новая формула с rarity_modifier |
| §6.1 User API | Новые эндпоинты: store/packs, buy, fantasy-team details; fantiki в /me |
| §6.2 Admin API | Новые: perks CRUD, give-fantiki, pack player pool |
| §10 Agent Work Split | Обновить план агентов |

---

## Затронутые Flyway-миграции (новые)

| Миграция | Содержание |
|---|---|
| `V5__fantiki.sql` | `telegram_user.fantiki`, таблица `fantiki_transaction` |
| `V6__perk_system.sql` | Таблицы `perk`, `perk_applicable_role`; изменение `card_template_perk` |
| `V7__auto_packs.sql` | `card_pack` новые колонки, таблица `card_pack_player`, изменение `card_pack_rarity_config` |
| `V8__game_score_details.sql` | `fantasy_team_card_game_score`, `fantasy_team_card_game_perk` |
