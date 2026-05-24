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

## 5. Seed-каталог V1

V1 должен стартовать с конкретным каталогом, а не с абстрактными категориями. Ниже - рекомендуемый seed на **42 основных достижения** и **2 optional secret-достижения**. Его можно скорректировать по названиям/иконкам, но коды и условия лучше считать продуктовым контрактом первого релиза.

Колонка `История` задает, как достижение относится к данным до релиза:

- `CURRENT_STATE` - считается по текущему состоянию БД на момент backfill и дальше. Если пользователь уже владеет 100 активными картами при включении фичи, `cards_total_100` сразу станет `COMPLETED_UNCLAIMED`.
- `RETROACTIVE_CUMULATIVE` - считается по надежным историческим фактам в БД. Если пользователь уже собрал 30 бюджетных команд до релиза достижений, `budget_team_30` сразу станет `COMPLETED_UNCLAIMED`.
- `FROM_ACHIEVEMENTS_LAUNCH` - считается только после релиза, потому что старых надежных событий нет или они были не предназначены для достижений. Например, share/click social.

### 5.1 Участие и дисциплина

Считать уникальные `fantasy_team` по пользователю и `series_league`. Update состава не должен увеличивать прогресс повторно.

| Code | Название | Условие | История | Награда |
|------|----------|---------|---------|---------|
| `team_submit_1` | Первый состав | Создать первую команду в любой лиге | `RETROACTIVE_CUMULATIVE` | 10₣ |
| `team_submit_5` | Регулярный участник | Создать команды в 5 лигах серий | `RETROACTIVE_CUMULATIVE` | 25₣ |
| `team_submit_15` | В расписании | Создать команды в 15 лигах серий | `RETROACTIVE_CUMULATIVE` | 50₣ |
| `team_submit_30` | Стабильный менеджер | Создать команды в 30 лигах серий | `RETROACTIVE_CUMULATIVE` | 100₣ + badge |
| `dual_league_1` | Двойная заявка | В одной серии создать команды в MAIN и BUDGET | `RETROACTIVE_CUMULATIVE` | 25₣ |
| `dual_league_10` | Две стратегии | В 10 разных сериях создать команды в MAIN и BUDGET | `RETROACTIVE_CUMULATIVE` | 75₣ + badge |

### 5.2 Бюджетная лига

Это главный гринд-трек V1. Он **не начинается с нуля** при релизе достижений: существующие `fantasy_team` в `series_league.league.code = 'BUDGET'` засчитываются backfill'ом.

| Code | Название | Условие | История | Награда |
|------|----------|---------|---------|---------|
| `budget_team_1` | Первый бюджет | Создать первую команду в BUDGET | `RETROACTIVE_CUMULATIVE` | 10₣ |
| `budget_team_5` | Бюджетный старт | Создать команду в BUDGET 5 раз | `RETROACTIVE_CUMULATIVE` | 25₣ |
| `budget_team_15` | Экономный стратег | Создать команду в BUDGET 15 раз | `RETROACTIVE_CUMULATIVE` | 75₣ |
| `budget_team_30` | Мастер бюджета | Создать команду в BUDGET 30 раз | `RETROACTIVE_CUMULATIVE` | 150₣ + profile frame |
| `budget_win_1` | Бюджетная победа | Победить в BUDGET-лиге завершенной серии | `RETROACTIVE_CUMULATIVE` | 75₣ + badge |
| `budget_top10_5` | Бюджетный топ | 5 раз попасть в топ-10 BUDGET | `RETROACTIVE_CUMULATIVE` | 50₣ |

### 5.3 Результаты

Достижения результатов должны поддерживать победителей и регулярных участников. Считать только `series.status = FINISHED`; для ничьих использовать тот же порядок leaderboard, что в UI/финализации.

| Code | Название | Условие | История | Награда |
|------|----------|---------|---------|---------|
| `series_win_1` | Первая победа | Победить в любой лиге завершенной серии | `RETROACTIVE_CUMULATIVE` | 75₣ + badge |
| `series_win_3` | Серийный победитель | Победить в 3 лигах серий | `RETROACTIVE_CUMULATIVE` | 100₣ |
| `series_win_10` | Династия | Победить в 10 лигах серий | `RETROACTIVE_CUMULATIVE` | 200₣ + profile frame |
| `top3_5` | На пьедестале | 5 раз попасть в топ-3 | `RETROACTIVE_CUMULATIVE` | 75₣ |
| `top10_10` | В верхней группе | 10 раз попасть в топ-10 или верхнюю половину, если участников меньше 10 | `RETROACTIVE_CUMULATIVE` | 75₣ |
| `top_quarter_10` | Стабильный результат | 10 раз попасть в верхние 25% leaderboard | `RETROACTIVE_CUMULATIVE` | 100₣ + badge |

### 5.4 Коллекция

Коллекционные достижения V1 считаются по **активной текущей коллекции**. Soft-deleted карты не считаются. Если пользователь выполнил условие до релиза и до сих пор владеет нужными картами, достижение выполнится автоматически.

| Code | Название | Условие | История | Награда |
|------|----------|---------|---------|---------|
| `cards_total_10` | Первая полка | Владеть 10 активными картами | `CURRENT_STATE` | 25₣ |
| `cards_total_30` | Коллекционер | Владеть 30 активными картами | `CURRENT_STATE` | 75₣ |
| `cards_total_100` | Большая коллекция | Владеть 100 активными картами | `CURRENT_STATE` | 150₣ + badge |
| `first_epic` | Эпический дроп | Владеть активной EPIC-картой | `CURRENT_STATE` | 25₣ |
| `first_legendary` | Легенда в коллекции | Владеть активной LEGENDARY-картой | `CURRENT_STATE` | 75₣ + badge |
| `first_skin_card` | Особый выпуск | Владеть активной картой со скином | `CURRENT_STATE` | 50₣ |
| `same_player_3_rarities` | Любимый игрок | Владеть активными картами одного `fantasy_player` в 3 редкостях | `CURRENT_STATE` | 100₣ + badge |

После `completed_at` достижение не откатывается, даже если пользователь позже продал или переработал карты и текущий счетчик снизился. До `completed_at` прогресс может уменьшаться, потому что это current-state метрика.

### 5.5 Marketplace

Считать только SOLD-листинги без записи в `marketplace_listing_sanction`. Если сделка позже признана нерыночной, V1 не отзывает уже claimed-награду, но backfill и новые пересчеты должны исключать такую сделку.

| Code | Название | Условие | История | Награда |
|------|----------|---------|---------|---------|
| `market_buy_1` | Первая покупка | Купить первую карту на marketplace | `RETROACTIVE_CUMULATIVE` | 10₣ |
| `market_buy_5` | Охотник за картами | Купить 5 карт на marketplace | `RETROACTIVE_CUMULATIVE` | 50₣ |
| `market_sell_1` | Первая продажа | Продать первую карту на marketplace | `RETROACTIVE_CUMULATIVE` | 10₣ |
| `market_sell_5` | Продавец | Продать 5 карт на marketplace | `RETROACTIVE_CUMULATIVE` | 50₣ |
| `market_watch_1` | На наблюдении | Создать первый marketplace watch-фильтр | `CURRENT_STATE` | 10₣ |
| `market_unique_counterparties_5` | Широкий рынок | Провести SOLD-сделки с 5 разными контрагентами | `RETROACTIVE_CUMULATIVE` | 75₣ + badge |

Запрещено давать достижения за жалобы, частое снятие/перевыставление листингов или сделки с одним и тем же контрагентом.

### 5.6 Паки и апгрейды

Открытия паков считаются по уже существующей статистике открытий. Для редких дропов и crafted LEGENDARY backfill использует текущие `user_card` и исторические поля, где они надежны; будущие события считаются через event-driven обновление.

| Code | Название | Условие | История | Награда |
|------|----------|---------|---------|---------|
| `pack_open_1` | Первый пак | Открыть 1 пак | `RETROACTIVE_CUMULATIVE` | 10₣ |
| `pack_open_5` | Пять попыток | Открыть 5 паков | `RETROACTIVE_CUMULATIVE` | 25₣ |
| `pack_open_15` | Охота за редкостью | Открыть 15 паков | `RETROACTIVE_CUMULATIVE` | 75₣ |
| `pack_open_30` | Большое вскрытие | Открыть 30 паков | `RETROACTIVE_CUMULATIVE` | 150₣ + badge |
| `pack_epic_drop_1` | Эпик из пака | Получить EPIC из пака и владеть этой картой или ее traceable экземпляром | `CURRENT_STATE` | 25₣ |
| `legendary_upgrade_1` | Своими руками | Сделать первый legendary upgrade | `RETROACTIVE_CUMULATIVE` | 75₣ + badge |
| `crafted_legendary_3` | Мастер апгрейда | Сделать 3 legendary upgrade | `RETROACTIVE_CUMULATIVE` | cosmetic unlock |

### 5.7 Социальность

Социальные достижения V1 считаются только после релиза, потому что до них не было отдельного надежного события достижения. Клиентское нажатие share не гарантирует фактическую отправку в Telegram, поэтому награды минимальные и одноразовые.

| Code | Название | Условие | История | Награда |
|------|----------|---------|---------|---------|
| `share_profile_1` | Витрина открыта | Нажать share профиля | `FROM_ACHIEVEMENTS_LAUNCH` | 10₣ |
| `share_team_1` | Команда наружу | Нажать share команды | `FROM_ACHIEVEMENTS_LAUNCH` | 10₣ |
| `compare_open_1` | Сравнили составы | Открыть compare-view | `FROM_ACHIEVEMENTS_LAUNCH` | 10₣ |
| `view_public_profile_5` | Скаут | Открыть 5 чужих профилей | `FROM_ACHIEVEMENTS_LAUNCH` | badge |

### 5.8 Особые и секретные

Секретные достижения скрыты до получения или показываются как "???". В V1 их лучше держать выключенными или seed'ить 1-2 cosmetic-only достижения после основного запуска, чтобы не усложнять первый backfill.

| Code | Название | Условие | История | Награда | Статус V1 |
|------|----------|---------|---------|---------|-----------|
| `secret_first_frame` | ??? | Впервые выбрать профильную рамку | `FROM_ACHIEVEMENTS_LAUNCH` | badge style | optional |
| `secret_full_showcase` | ??? | Заполнить все featured achievement slots | `FROM_ACHIEVEMENTS_LAUNCH` | cosmetic unlock | optional |

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

### 6.3 History policy

Каждое достижение должно иметь явную `history_policy`. Это не админская вкусовая настройка, а часть системного условия.

| Policy | Семантика | Пример |
|--------|-----------|--------|
| `CURRENT_STATE` | Прогресс равен текущему состоянию. Backfill может сразу завершить достижение, если состояние уже удовлетворяет условию. До `completed_at` прогресс может уменьшаться; после completion достижение не откатывается. | `cards_total_100`: если на релизе у пользователя 100 активных карт, достижение сразу completed/unclaimed. |
| `RETROACTIVE_CUMULATIVE` | Прогресс равен количеству исторических фактов в БД за все время. Backfill засчитывает действия, сделанные до релиза достижений. | `budget_team_30`: если пользователь уже собрал 30 BUDGET-команд, достижение сразу completed/unclaimed. |
| `FROM_ACHIEVEMENTS_LAUNCH` | Прогресс начинается с момента включения достижения. Старые действия не засчитываются, потому что не было надежного события или оно не было продуктово определено. | `share_profile_1`: старые share-клики не засчитываются. |

Базовое правило V1: если данные в БД надежно отражают прошлое действие, достижение retroactive. Исключения должны быть явно названы в seed-каталоге.

### 6.4 Backfill

Backfill обязателен для релиза.

Режимы:

- dry-run по всем пользователям;
- dry-run по одному пользователю;
- apply по всем пользователям;
- apply по одному пользователю;
- пересчет конкретного `condition_type`.

Backfill выставляет progress и `completed_at`, но не `claimed_at`. Награды остаются unclaimed.

Конкретные примеры:

- `cards_total_100`: `CURRENT_STATE`. Пользователь с 100 активными картами на дату релиза сразу получает `COMPLETED_UNCLAIMED`.
- `budget_team_30`: `RETROACTIVE_CUMULATIVE`. Пользователь с 30 уже созданными BUDGET-командами сразу получает `COMPLETED_UNCLAIMED`; пользователь с 12 командами получает `IN_PROGRESS 12/30`.
- `share_profile_1`: `FROM_ACHIEVEMENTS_LAUNCH`. Старые шаринги не считаются; прогресс начнется с первого события после включения достижения.
- `market_sell_5`: `RETROACTIVE_CUMULATIVE`, но только SOLD-листинги без санкции. Санкционированные сделки исключаются из пересчета.

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
    history_policy VARCHAR(48) NOT NULL,
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
- `history_policy`: `CURRENT_STATE`, `RETROACTIVE_CUMULATIVE`, `FROM_ACHIEVEMENTS_LAUNCH`.

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

Backfill вызывает `currentProgress` и применяет `history_policy`: для `RETROACTIVE_CUMULATIVE` хранит накопленный исторический счетчик, для `CURRENT_STATE` хранит текущий счетчик до completion, для `FROM_ACHIEVEMENTS_LAUNCH` не пытается восстановить старые события.

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
          "historyPolicy": "RETROACTIVE_CUMULATIVE",
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

## 16. Production backfill estimate

Снимок ниже посчитан по production DB на **2026-05-25** read-only запросами через `polemica-prod-db-readonly`. Это не статичная продуктовая истина: перед релизом достижений dry-run нужно повторить.

Важный вывод: текущий V1 seed с полными retroactive fantiki-наградами создает слишком большую потенциальную разовую выдачу. Если все пользователи заберут уже выполненные награды, верхняя оценка по текущему production snapshot: **343 695₣**.

Разбивка потенциальной мгновенной выдачи:

| Блок | Potential ₣ liability |
|------|-----------------------|
| Участие + бюджет | 77 505₣ |
| Результаты | 45 725₣ |
| Коллекция | 123 200₣ |
| Marketplace | 30 435₣ |
| Паки и апгрейды | 66 830₣ |
| Social/from-launch | 0₣ |
| **Итого** | **343 695₣** |

### 16.1 Instant completion by achievement

| Code | История | Instant users | Potential ₣ |
|------|---------|---------------|-------------|
| `team_submit_1` | `RETROACTIVE_CUMULATIVE` | 438 | 4 380 |
| `team_submit_5` | `RETROACTIVE_CUMULATIVE` | 313 | 7 825 |
| `team_submit_15` | `RETROACTIVE_CUMULATIVE` | 240 | 12 000 |
| `team_submit_30` | `RETROACTIVE_CUMULATIVE` | 169 | 16 900 |
| `dual_league_1` | `RETROACTIVE_CUMULATIVE` | 285 | 7 125 |
| `dual_league_10` | `RETROACTIVE_CUMULATIVE` | 134 | 10 050 |
| `budget_team_1` | `RETROACTIVE_CUMULATIVE` | 290 | 2 900 |
| `budget_team_5` | `RETROACTIVE_CUMULATIVE` | 191 | 4 775 |
| `budget_team_15` | `RETROACTIVE_CUMULATIVE` | 108 | 8 100 |
| `budget_team_30` | `RETROACTIVE_CUMULATIVE` | 23 | 3 450 |
| `budget_win_1` | `RETROACTIVE_CUMULATIVE` | 31 | 2 325 |
| `budget_top10_5` | `RETROACTIVE_CUMULATIVE` | 135 | 6 750 |
| `series_win_1` | `RETROACTIVE_CUMULATIVE` | 78 | 5 850 |
| `series_win_3` | `RETROACTIVE_CUMULATIVE` | 9 | 900 |
| `series_win_10` | `RETROACTIVE_CUMULATIVE` | 0 | 0 |
| `top3_5` | `RETROACTIVE_CUMULATIVE` | 14 | 1 050 |
| `top10_10` | `RETROACTIVE_CUMULATIVE` | 206 | 15 450 |
| `top_quarter_10` | `RETROACTIVE_CUMULATIVE` | 134 | 13 400 |
| `cards_total_10` | `CURRENT_STATE` | 540 | 13 500 |
| `cards_total_30` | `CURRENT_STATE` | 453 | 33 975 |
| `cards_total_100` | `CURRENT_STATE` | 104 | 15 600 |
| `first_epic` | `CURRENT_STATE` | 554 | 13 850 |
| `first_legendary` | `CURRENT_STATE` | 189 | 14 175 |
| `first_skin_card` | `CURRENT_STATE` | 182 | 9 100 |
| `same_player_3_rarities` | `CURRENT_STATE` | 230 | 23 000 |
| `market_buy_1` | `RETROACTIVE_CUMULATIVE` | 259 | 2 590 |
| `market_buy_5` | `RETROACTIVE_CUMULATIVE` | 134 | 6 700 |
| `market_sell_1` | `RETROACTIVE_CUMULATIVE` | 215 | 2 150 |
| `market_sell_5` | `RETROACTIVE_CUMULATIVE` | 110 | 5 500 |
| `market_watch_1` | `CURRENT_STATE` | 37 | 370 |
| `market_unique_counterparties_5` | `RETROACTIVE_CUMULATIVE` | 175 | 13 125 |
| `pack_open_1` | `RETROACTIVE_CUMULATIVE` | 558 | 5 580 |
| `pack_open_5` | `RETROACTIVE_CUMULATIVE` | 484 | 12 100 |
| `pack_open_15` | `RETROACTIVE_CUMULATIVE` | 212 | 15 900 |
| `pack_open_30` | `RETROACTIVE_CUMULATIVE` | 34 | 5 100 |
| `pack_epic_drop_1` | `CURRENT_STATE` | 553 | 13 825 |
| `legendary_upgrade_1` | `RETROACTIVE_CUMULATIVE` | 191 | 14 325 |
| `crafted_legendary_3` | `RETROACTIVE_CUMULATIVE` | 55 | 0 |
| `share_profile_1` | `FROM_ACHIEVEMENTS_LAUNCH` | 0 | 0 |
| `share_team_1` | `FROM_ACHIEVEMENTS_LAUNCH` | 0 | 0 |
| `compare_open_1` | `FROM_ACHIEVEMENTS_LAUNCH` | 0 | 0 |
| `view_public_profile_5` | `FROM_ACHIEVEMENTS_LAUNCH` | 0 | 0 |
| `secret_first_frame` | `FROM_ACHIEVEMENTS_LAUNCH` | 0 | 0 |
| `secret_full_showcase` | `FROM_ACHIEVEMENTS_LAUNCH` | 0 | 0 |

### 16.2 Launch payout recommendation

Do not ship the current seed with full retroactive fantiki claim enabled. Recommended policy for V1 launch:

1. Retroactive completion should unlock achievement status, badges and profile frames immediately.
2. Fantiki for retroactive completions should be either disabled, capped, or moved to future post-launch completions.
3. If retroactive fantiki remain enabled, add a hard per-user launch cap and show the total potential liability in admin dry-run before enabling claim.
4. High-volume `CURRENT_STATE` achievements (`cards_total_*`, `first_epic`, `pack_epic_drop_1`) are the first candidates to become cosmetic-only for retroactive completion.
5. Keep `FROM_ACHIEVEMENTS_LAUNCH` social achievements unchanged: they already have zero launch liability.

## 17. Открытые вопросы

1. Финальные названия, иконки, цвета и точные cosmetic rewards для seed-каталога V1.
2. Нужен ли отдельный каталог `profile_frame` или достаточно `user_cosmetic_unlock` с metadata в `achievement_reward`.
3. Делать ли card skin unlock применяемым к конкретной карте в V1 или отложить до V2.
4. Показывать ли `COMPLETED_UNCLAIMED` достижения в публичном профиле до claim. Рекомендация: не показывать в featured до claim, но считать completed в личной статистике.
5. Какую launch payout policy выбрать: no retroactive fantiki, per-user cap, category cap или отдельные награды только за будущие completions.
