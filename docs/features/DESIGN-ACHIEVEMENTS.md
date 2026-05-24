# Система пользовательских достижений

> **Статус:** Draft - продуктовая рамка согласована
> **Файл:** [`docs/features/DESIGN-ACHIEVEMENTS.md`](./DESIGN-ACHIEVEMENTS.md)
> **Связанные документы:** [`DESIGN.md`](../architecture/DESIGN.md), [`DESIGN-CARD-VALUE-AND-LEAGUES.md`](./DESIGN-CARD-VALUE-AND-LEAGUES.md), [`DESIGN-NOTIFICATIONS.md`](./DESIGN-NOTIFICATIONS.md), [`DESIGN-MARKETPLACE.md`](./DESIGN-MARKETPLACE.md)

---

## 1. Контекст

Пользовательский комментарий:

> "Чтоб профиль был красивый. Причем я буду рад даже поэтапным гринд достижениям вроде "выставите команду в бюджетную серию 5/15/30 раз""

В проекте уже есть публичный профиль пользователя: рейтинг, победы в сериях, история серий, коллекция, marketplace-статистика и последние сделки. Есть социальный слой с шарингом профиля, команды, места, карточки и compare-страниц. Достижения должны усилить именно этот слой: сделать профиль выразительным, дать понятный долгий прогресс и добавить небольшие экономические/cosmetic награды.

Важно развести термины:

| Термин | Значение |
|--------|----------|
| **Перк** / `perk` | Механика карты и скоринга. Перки живут на `card_template`, срабатывают по данным игры Polemica и дают `perk_bonus`. |
| **Достижение** / `achievement` | Мета-прогресс пользователя. Не влияет на скоринг, силу карт или правила серий. |

Старое техническое имя `achievement` недавно освобождено от смысла "перк", поэтому новую систему можно называть `achievement`, но в UI и документации нужно явно писать "достижения пользователя".

---

## 2. Цели

1. **Красивый публичный профиль.** Достижения должны быть первым заметным сигналом в профиле: рамка, избранные бейджи, счетчик и короткий прогресс.
2. **Долгий гринд.** Пользователь должен видеть понятные цепочки 1/5/15/30 и возвращаться к сериям, бюджетной лиге, коллекции, пакам и marketplace.
3. **Небольшие награды.** Достижения дают фантики и косметику, но не становятся основным источником валюты.
4. **Честность серий.** Никаких прямых бонусов к очкам, лимитам команд, uses, силе карт или результатам leaderboard.
5. **Автоматичность.** Пользователь не заявляет достижения вручную. Система считает прогресс по фактам из БД и доменным событиям.
6. **Управляемость.** Условия выдачи системные, а тексты, иконки, видимость, награды и порядок редактируются через админку.
7. **Backfill.** Старые действия пользователей должны засчитаться после релиза через пересчет прогресса.

## 3. Non-goals V1

- Сезонные кампании с reset-логикой.
- Наборы/альбомы с наградой за закрытие группы достижений.
- Достижения, которые дают соревновательное преимущество.
- Ручная модераторская выдача произвольных достижений пользователям.
- Отдельные Telegram-уведомления на каждое достижение.
- Сложный apply-flow для скинов карт, если он не нужен первому релизу витрины профиля.

---

## 4. Продуктовые принципы

### 4.1 Витрина профиля

Выбранное направление UI: **витрина профиля**.

В публичной шапке профиля показываем:

- выбранную рамку профиля;
- 3-5 избранных бейджей достижений;
- счетчик полученных достижений;
- короткий прогресс до следующего заметного достижения.

Полный список достижений находится ниже отдельным разделом. В своем профиле пользователь может выбрать рамку и featured-бейджи.

### 4.2 Только экономика и косметика

Разрешенные награды:

- фантики;
- профильные рамки;
- бейджи и cosmetic-стили бейджей;
- cosmetic skin unlock для карт;
- другие визуальные unlock'и профиля.

Запрещенные награды:

- бонусы к `total_score`;
- множители редкости;
- дополнительные перки;
- extra uses;
- обход `value_cap`, `max_team_size`, `max_legendary_count`;
- скидки/привилегии, которые прямо усиливают команду в серии.

### 4.3 Фантики как небольшой бонус

Фантики не должны становиться главным источником валюты. Основная ценность достижений - статус, профиль и косметика.

Рекомендуемые вилки:

| Тип достижения | Награда |
|----------------|---------|
| Малое гринд-достижение | 10-25₣ |
| Средний порог | 30-75₣ |
| Длинная цепочка вроде 30 бюджетных серий | 100-200₣ |
| Очень редкое достижение | до 250₣, как исключение |

Перед включением backfill админка должна показывать dry-run: сколько пользователей получит completed/unclaimed и какой максимальный объем фантиков может быть выдан при claim.

---

## 5. Категории V1

Стартовый каталог: 25-40 достижений. Большинство - пороговые цепочки. Примеры ниже не являются финальным seed-списком, но задают рамку.

### 5.1 Участие и дисциплина

| Code | Название | Условие | Награда |
|------|----------|---------|---------|
| `team_submit_1` | Первый состав | Собрать команду в любой лиге | 10₣ |
| `team_submit_5` | Регулярный участник | Собрать команды в 5 сериях/лигах | 25₣ |
| `team_submit_15` | В расписании | Собрать команды в 15 сериях/лигах | 50₣ |
| `team_submit_30` | Стабильный менеджер | Собрать команды в 30 сериях/лигах | 100₣ + badge |
| `dual_league_1` | Двойная заявка | В одной серии собрать MAIN и BUDGET | 25₣ |

Считать уникальные `fantasy_team` по пользователю и `series_league`. Update состава не должен увеличивать прогресс повторно.

### 5.2 Бюджетная лига

Главная цепочка из пользовательского комментария.

| Code | Название | Условие | Награда |
|------|----------|---------|---------|
| `budget_team_5` | Бюджетный старт | Собрать команду в BUDGET 5 раз | 25₣ |
| `budget_team_15` | Экономный стратег | Собрать команду в BUDGET 15 раз | 75₣ |
| `budget_team_30` | Мастер бюджета | Собрать команду в BUDGET 30 раз | 150₣ + profile frame |
| `budget_win_1` | Бюджетная победа | Победить в BUDGET-лиге серии | 75₣ + badge |
| `budget_top10_5` | Бюджетный топ | 5 раз попасть в топ-10 BUDGET | 50₣ |

### 5.3 Результаты

Достижения результатов должны поддерживать как победителей, так и регулярных участников.

| Code | Условие | Комментарий |
|------|---------|-------------|
| `series_win_1/3/10` | Победы в завершенных сериях по любой лиге | Использует ту же логику, что `seriesWins` профиля. |
| `top3_5/15` | Попадания в топ-3 | Только `series.status = FINISHED`. |
| `top10_10` | 10 попаданий в топ-10 | Если участников меньше 10, считать попадание в верхнюю половину. |
| `top_quarter_streak_5` | 5 завершенных участий подряд не ниже топ-25% | V1 optional: сложнее backfill и объяснение. |

### 5.4 Коллекция

| Code | Условие | Комментарий |
|------|---------|-------------|
| `cards_total_10/30/100` | Владеть 10/30/100 активными картами | Soft-deleted карты не считаются. |
| `first_epic` | Получить первую EPIC | Источник не важен. |
| `first_legendary` | Получить первую LEGENDARY | Включает legendary upgrade и admin-issued. |
| `first_skin_card` | Получить карту со скином | Использует `user_card.card_skin_id`. |
| `same_player_3_rarities` | Владеть картами одного `fantasy_player` в 3 редкостях | Хорошо для коллекционирования. |

Не поощрять переработку как гринд ради наград. Если нужны recycle-достижения, они должны быть малонаградными и одноразовыми.

### 5.5 Marketplace

| Code | Условие | Guardrail |
|------|---------|-----------|
| `market_buy_1/5/15` | Купить 1/5/15 карт | Считать только SOLD без санкций. |
| `market_sell_1/5/15` | Продать 1/5/15 карт | Считать только SOLD без санкций. |
| `market_watch_1` | Создать первый watch-фильтр | Награда минимальная. |
| `market_unique_counterparties_5` | Сделки с 5 разными контрагентами | Снижает риск перелива. |

Запрещено давать достижения за жалобы, частое снятие/перевыставление листингов или сделки с одним и тем же контрагентом. Если сделка позже признана нерыночной, V1 не обязан отзывать уже выданное достижение, но backfill и новые прогрессы должны исключать санкционированные листинги.

### 5.6 Паки и апгрейды

| Code | Условие | Награда |
|------|---------|---------|
| `pack_open_1/5/15/30` | Открыть 1/5/15/30 паков | Небольшие фантики, badge на 30. |
| `pack_epic_drop_1` | Получить EPIC из пака | 25₣. |
| `legendary_upgrade_1` | Сделать первый legendary upgrade | badge/profile frame candidate. |
| `crafted_legendary_3` | Сделать 3 legendary upgrade | cosmetic reward. |

### 5.7 Социальность

| Code | Условие | Комментарий |
|------|---------|-------------|
| `share_profile_1` | Нажать share профиля | Маленькая награда, не повторяемая. |
| `share_team_1` | Нажать share команды | Маленькая награда. |
| `compare_open_1` | Открыть compare-view | Без сильной награды. |
| `view_public_profile_5` | Посмотреть 5 чужих профилей | Без спам-стимула. |

Важно: клиентское нажатие share не гарантирует фактическую отправку в Telegram. Поэтому эти достижения должны быть одноразовыми и малонаградными.

### 5.8 Особые и секретные

Секретные достижения скрыты до получения или показываются как "???". Они должны давать в основном косметику. Не использовать секретные достижения для критичных пользовательских целей, иначе пользователь не сможет понять, что делать.

---

## 6. Прогресс и состояния

### 6.1 Состояния

| State | Условие |
|-------|---------|
| `LOCKED` | Прогресса нет или достижение скрыто. |
| `IN_PROGRESS` | Есть прогресс, но порог не достигнут. |
| `COMPLETED_UNCLAIMED` | Условие выполнено, награда еще не забрана. |
| `CLAIMED` | Награда выдана, достижение закрыто. |

`COMPLETED_UNCLAIMED` нужен намеренно: пользователь видит кнопку "Забрать", а backfill не меняет баланс без явного действия пользователя.

### 6.2 Claim-based награды

Все награды в фантиках выдаются только через `POST /achievements/{code}/claim`.

Claim должен быть идемпотентным:

- повторный claim не создает вторую транзакцию;
- при гонке двух запросов награда выдается один раз;
- cosmetic unlock имеет unique constraint по пользователю и коду косметики;
- аудит фантиков идет через `FantikiTransactionReason.ACHIEVEMENT_REWARD`.

### 6.3 Backfill

Backfill обязателен для релиза.

Режимы:

- dry-run по всем пользователям;
- dry-run по одному пользователю;
- apply по всем пользователям;
- apply по одному пользователю;
- пересчет конкретного `condition_type`.

Backfill выставляет progress и `completed_at`, но не `claimed_at`. Награды остаются unclaimed.

Для больших пересчетов:

- батчи пользователей;
- короткие транзакции;
- лог результата;
- не блокировать основную работу TMA;
- возможность повторного безопасного запуска.

---

## 7. Модель данных

### 7.1 `achievement_definition`

Справочник достижений. Условия системные, упаковка и награды управляемые.

```sql
CREATE TABLE achievement_definition (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(96) NOT NULL UNIQUE,
    category VARCHAR(48) NOT NULL,
    condition_type VARCHAR(96) NOT NULL,
    target_value BIGINT NOT NULL,
    chain_group VARCHAR(96),
    chain_level INT,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    icon_url VARCHAR(2048),
    accent_color VARCHAR(32),
    rarity VARCHAR(32) NOT NULL DEFAULT 'COMMON',
    visibility VARCHAR(32) NOT NULL DEFAULT 'PUBLIC',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    display_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

Рекомендуемые enum-like значения:

- `category`: `PARTICIPATION`, `BUDGET`, `RESULTS`, `COLLECTION`, `MARKETPLACE`, `PACKS`, `SOCIAL`, `SPECIAL`;
- `visibility`: `PUBLIC`, `SECRET_UNTIL_COMPLETED`, `HIDDEN`;
- `rarity`: `COMMON`, `RARE`, `EPIC`, `LEGENDARY`.

`condition_type` - ключ системного evaluator'а, например:

- `TEAMS_SUBMITTED`;
- `BUDGET_TEAMS_SUBMITTED`;
- `BUDGET_WINS`;
- `SERIES_WINS`;
- `CARDS_OWNED_TOTAL`;
- `PACKS_OPENED`;
- `MARKETPLACE_PURCHASES`;
- `LEGENDARY_UPGRADES`;
- `PROFILE_SHARED`.

### 7.2 `achievement_reward`

Награды лучше хранить отдельно, чтобы одно достижение могло дать фантики и косметику.

```sql
CREATE TABLE achievement_reward (
    id BIGSERIAL PRIMARY KEY,
    achievement_id BIGINT NOT NULL REFERENCES achievement_definition(id) ON DELETE CASCADE,
    reward_type VARCHAR(48) NOT NULL,
    amount BIGINT,
    reward_code VARCHAR(96),
    metadata JSONB,
    display_order INT NOT NULL DEFAULT 0
);
```

Типы V1:

- `FANTIKI`;
- `PROFILE_FRAME`;
- `CARD_SKIN_UNLOCK`;
- `BADGE_STYLE`;
- `COSMETIC_UNLOCK`.

### 7.3 `user_achievement`

```sql
CREATE TABLE user_achievement (
    telegram_user_id BIGINT NOT NULL REFERENCES telegram_user(id) ON DELETE CASCADE,
    achievement_id BIGINT NOT NULL REFERENCES achievement_definition(id) ON DELETE CASCADE,
    progress_value BIGINT NOT NULL DEFAULT 0,
    completed_at TIMESTAMPTZ,
    claimed_at TIMESTAMPTZ,
    reward_snapshot JSONB,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (telegram_user_id, achievement_id)
);
```

`reward_snapshot` фиксирует, что именно получил пользователь при claim. Если админ позже изменит награду, уже забранные достижения остаются исторически корректными.

### 7.4 `user_cosmetic_unlock`

```sql
CREATE TABLE user_cosmetic_unlock (
    telegram_user_id BIGINT NOT NULL REFERENCES telegram_user(id) ON DELETE CASCADE,
    cosmetic_type VARCHAR(48) NOT NULL,
    cosmetic_code VARCHAR(96) NOT NULL,
    source_type VARCHAR(48) NOT NULL DEFAULT 'ACHIEVEMENT',
    source_code VARCHAR(96),
    unlocked_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (telegram_user_id, cosmetic_type, cosmetic_code)
);
```

Для `CARD_SKIN_UNLOCK` это право на cosmetic skin. Применение скина к конкретной карте можно сделать отдельным flow, потому что текущая модель `card_skin` привязана к экземпляру `user_card`.

### 7.5 Витрина профиля

```sql
CREATE TABLE user_profile_customization (
    telegram_user_id BIGINT PRIMARY KEY REFERENCES telegram_user(id) ON DELETE CASCADE,
    profile_frame_code VARCHAR(96),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE user_profile_featured_achievement (
    telegram_user_id BIGINT NOT NULL REFERENCES telegram_user(id) ON DELETE CASCADE,
    achievement_id BIGINT NOT NULL REFERENCES achievement_definition(id) ON DELETE CASCADE,
    display_order INT NOT NULL,
    PRIMARY KEY (telegram_user_id, achievement_id)
);
```

Service-level правила:

- максимум 5 featured-достижений;
- featured можно выбрать только из completed/claimed достижений пользователя;
- frame можно выбрать только из `user_cosmetic_unlock`;
- если достижение disabled после получения, оно не исчезает из профиля пользователя.

---

## 8. Backend architecture

### 8.1 Основные сервисы

| Service | Ответственность |
|---------|-----------------|
| `AchievementCatalogService` | Чтение definitions, rewards, DTO для TMA/admin. |
| `AchievementProgressService` | Обновление progress, completed state, idempotent upsert. |
| `AchievementClaimService` | Claim награды, `fantiki_transaction`, cosmetic unlocks. |
| `AchievementBackfillService` | Пересчет из БД, dry-run/apply. |
| `ProfileCustomizationService` | Выбранная рамка и featured-бейджи. |
| `AchievementAdminService` | Admin CRUD для текстов, наград, enabled/visibility/order. |

### 8.2 Evaluator pattern

Условия выдачи должны быть системными, а не произвольным SQL из админки.

```kotlin
interface AchievementConditionEvaluator {
    val conditionType: String
    fun currentProgress(userId: Long, definition: AchievementDefinition): Long
    fun affectedUsers(event: AchievementDomainEvent): Set<Long>
}
```

Для event-driven обновлений можно использовать более узкий интерфейс:

```kotlin
interface AchievementEventHandler {
    fun supports(event: AchievementDomainEvent): Boolean
    fun update(event: AchievementDomainEvent)
}
```

Backfill вызывает `currentProgress` и ставит `progress_value = max(existing, calculated)` или точное значение, если условие является агрегатом текущего состояния.

### 8.3 Источники событий

| Действие | Источник |
|----------|----------|
| Команда собрана/обновлена | `UserFantasyTeamService` после успешного сохранения. |
| Серия финализирована | `SeriesFinalizationService` после расчета leaderboard/rewards. |
| Пак открыт | `UserStoreService` после выдачи карт. |
| Legendary upgrade | `LegendaryUpgradeService` после успешного апгрейда. |
| Marketplace sale/purchase | `MarketplaceService.buyCard`. |
| Watch-фильтр создан | Marketplace watch settings endpoint. |
| Share/click social | `ProductEventService` или отдельный lightweight endpoint. |
| Профиль/compare открыт | `ProductEventService`, если решено считать social achievements. |

Обновление достижений должно происходить после основной операции. Если используется Spring event, listener должен работать `AFTER_COMMIT`, чтобы не выдать прогресс за откатившуюся транзакцию.

### 8.4 Прогресс как aggregate, не append-only счетчик

Для большинства достижений безопаснее хранить текущий aggregate:

- число уникальных команд пользователя;
- число BUDGET-команд;
- число побед;
- число активных карт;
- число открытых паков;
- число несанкционированных SOLD-сделок.

Это снижает риск двойного инкремента при retry. Event-driven путь может просто запускать пересчет релевантных condition types для пользователя.

---

## 9. API

### 9.1 User API

#### `GET /api/v1/achievements`

Возвращает каталог и прогресс текущего пользователя.

```json
{
  "categories": [
    {
      "code": "BUDGET",
      "name": "Бюджетная лига",
      "achievements": [
        {
          "code": "budget_team_30",
          "title": "Мастер бюджета",
          "description": "Соберите команду в бюджетной лиге 30 раз",
          "state": "IN_PROGRESS",
          "progressValue": 17,
          "targetValue": 30,
          "rarity": "EPIC",
          "visibility": "PUBLIC",
          "rewards": [
            { "type": "FANTIKI", "amount": 150 },
            { "type": "PROFILE_FRAME", "code": "budget_master" }
          ]
        }
      ]
    }
  ],
  "summary": {
    "completed": 12,
    "claimed": 10,
    "totalVisible": 34,
    "unclaimedRewards": 2
  }
}
```

#### `POST /api/v1/achievements/{code}/claim`

Выдает награду, если достижение completed и еще не claimed.

Ответ:

```json
{
  "achievementCode": "budget_team_30",
  "claimedAt": "2026-05-25T10:00:00Z",
  "fantikiDelta": 150,
  "newFantikiBalance": 1250,
  "cosmeticUnlocks": [
    { "type": "PROFILE_FRAME", "code": "budget_master" }
  ]
}
```

Повторный claim возвращает текущее claimed-состояние без повторной выдачи.

#### `GET /api/v1/me/profile-customization`

Возвращает доступные рамки и достижения, которые можно поставить в витрину.

#### `PUT /api/v1/me/profile-customization`

```json
{
  "profileFrameCode": "budget_master",
  "featuredAchievementCodes": ["budget_team_30", "series_win_1", "first_legendary"]
}
```

Валидация:

- frame разблокирован;
- featured achievements принадлежат пользователю и completed/claimed;
- максимум 5 бейджей;
- порядок сохраняется.

#### Расширение `GET /api/v1/players/{telegramId}/profile`

Добавить:

```json
{
  "achievementSummary": {
    "completed": 12,
    "claimed": 10,
    "totalVisible": 34
  },
  "profileFrame": {
    "code": "budget_master",
    "name": "Мастер бюджета",
    "assetUrl": null
  },
  "featuredAchievements": [
    {
      "code": "budget_team_30",
      "title": "Мастер бюджета",
      "iconUrl": null,
      "rarity": "EPIC"
    }
  ],
  "nextAchievement": {
    "code": "budget_team_30",
    "title": "Мастер бюджета",
    "progressValue": 17,
    "targetValue": 30
  }
}
```

### 9.2 Admin API

| Method | Path | Назначение |
|--------|------|------------|
| `GET` | `/api/v1/admin/achievements` | Definitions + rewards + aggregate stats. |
| `PUT` | `/api/v1/admin/achievements/{code}` | Тексты, иконка, цвет, видимость, enabled, order, rewards. |
| `POST` | `/api/v1/admin/achievements/backfill/dry-run` | Оценить изменения и потенциальную сумму rewards. |
| `POST` | `/api/v1/admin/achievements/backfill` | Запустить пересчет. |
| `GET` | `/api/v1/admin/achievements/backfill/{jobId}` | Статус job. |
| `POST` | `/api/v1/admin/users/{telegramId}/achievements/recalculate` | Repair для одного пользователя. |

Admin update не должен менять `code` и `condition_type` без миграции/релиза. Это системный контракт.

---

## 10. TMA UX

### 10.1 Публичный профиль

Верхний блок профиля:

- имя и username;
- выбранная рамка;
- featured achievements;
- рейтинг/победы остаются рядом, но достижения становятся визуальным акцентом;
- кнопка share сохраняется.

Разделы ниже:

1. Рейтинг.
2. Победы в сериях.
3. Витрина достижений.
4. Серии.
5. Коллекция.
6. Marketplace.
7. Последние сделки.

Если профиль чужой:

- показываем completed public достижения;
- hidden/secret не раскрываем до получения;
- кнопку настройки витрины не показываем.

### 10.2 Свой профиль

В своем профиле:

- "Настроить витрину";
- "Достижения" с категориями;
- фильтры: все, в прогрессе, готово к получению, получено;
- карточка достижения: иконка, title, description, progress, rewards, claim button.

После выполнения достижения:

- toast или компактная модалка "Достижение открыто";
- primary action: "Забрать";
- secondary action: "К достижениям".

Не показывать тяжелую модалку на каждое мелкое достижение, если пользователь активно выполняет сценарий вроде открытия паков.

### 10.3 Настройка витрины

Экран настройки:

- выбор рамки из unlocked frames;
- сетка completed achievements;
- drag/order или простые up/down controls для 3-5 featured;
- preview шапки профиля.

### 10.4 Пустые состояния

Для нового пользователя:

- показывать 3-5 ближайших простых достижения;
- первый состав, первый пак, первая бюджетная команда;
- не показывать огромный locked-каталог как первый экран.

---

## 11. Admin UX

Страница `Achievements`:

- таблица definitions;
- фильтры category, enabled, visibility, rarity;
- редактирование title, description, icon, accent, order;
- редактирование rewards;
- toggle enabled.

Аналитика по achievement:

- started users;
- completed users;
- claimed users;
- claim rate;
- total claimed ₣;
- last completed at.

Backfill UI:

- dry-run перед запуском;
- estimate: affected users, new completed, potential ₣ liability;
- запуск job;
- статус и ошибки;
- repair для одного пользователя.

---

## 12. Notifications and product events

В V1 не отправляем Telegram-сообщение на каждое достижение. Причины:

- достижения могут открываться пачкой после backfill;
- частые гринд-достижения будут шуметь;
- уже есть TMA surface для claim.

Можно использовать существующий `ProductEvent` для social/click-based achievement signals:

- `PROFILE_SHARED`;
- `TEAM_SHARED`;
- `COMPARE_OPENED`;
- `PUBLIC_PROFILE_VIEWED`.

Если позже появятся сезонные или редкие достижения, можно добавить отдельную notification category, например `ACHIEVEMENT_UNLOCKED`, выключаемую пользователем.

---

## 13. Защита от фарма и плохих стимулов

1. **Не награждать жалобы.** Жалобы нужны для модерации, не для гринда.
2. **Не награждать снятие/перевыставление листингов.** Это создает шум на marketplace.
3. **Marketplace-счетчики считают только SOLD без санкций.**
4. **Для высоких marketplace tiers использовать distinct counterparties.**
5. **Update команды не увеличивает счетчик.** Считается уникальная команда по `series_league`.
6. **Социальные достижения одноразовые и малонаградные.** Click share не равен реальному внешнему share.
7. **Backfill dry-run обязателен перед включением claim.**
8. **Disabled не удаляет историю.** Если достижение отключено, уже полученные бейджи остаются у пользователя.

---

## 14. Rollout

### Этап 1: Core и API

- Flyway schema.
- Seed V1 definitions.
- Backend services.
- Condition evaluators для участия, BUDGET, результатов, коллекции, паков.
- `GET /achievements`.
- `POST /achievements/{code}/claim`.
- Backfill CLI/admin endpoint с dry-run.
- Минимальный TMA список достижений и claim.

### Этап 2: Витрина профиля

- Расширить public profile DTO.
- Добавить profile frame и featured badges.
- Экран настройки витрины.
- TMA визуал шапки профиля.

### Этап 3: Admin

- Achievements page.
- Редактирование metadata/rewards.
- Analytics.
- Backfill job UI.

### V2

- Сезонные достижения.
- Альбомы/sets.
- Более сложные cosmetic skin flows.
- Achievement notifications.
- Персональные рекомендации "следующее достижение".

---

## 15. Тестирование

Backend:

- unit tests для каждого evaluator;
- integration tests для:
  - submit team -> progress;
  - BUDGET teams 5/15/30;
  - finalization -> wins/top achievements;
  - pack opening -> pack/card achievements;
  - marketplace buy/sell -> eligible progress;
  - legendary upgrade -> progress;
  - claim idempotency;
  - backfill does not duplicate claim.

Frontend:

- `npm run build` для TMA/admin;
- ручная TMA проверка:
  - список достижений;
  - claim;
  - профиль с featured badges;
  - настройка рамки и бейджей;
  - чужой профиль.

Admin:

- build;
- dry-run отображает potential ₣ liability;
- disabled achievement не исчезает из уже полученного профиля.

---

## 16. Открытые вопросы

1. Финальный seed-список V1 достижений и exact rewards.
2. Нужен ли отдельный каталог `profile_frame` или достаточно `user_cosmetic_unlock` с metadata в `achievement_reward`.
3. Делать ли card skin unlock применяемым к конкретной карте в V1 или отложить до V2.
4. Показывать ли `COMPLETED_UNCLAIMED` достижения в публичном профиле до claim. Рекомендация: не показывать в featured до claim, но считать completed в личной статистике.
5. Нужно ли ограничить общий payout backfill на пользователя на старте.
