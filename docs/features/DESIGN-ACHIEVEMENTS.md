# Система пользовательских достижений

> **Статус:** Draft - продуктовая рамка согласована
> **Файл:** [`docs/features/DESIGN-ACHIEVEMENTS.md`](./DESIGN-ACHIEVEMENTS.md)
> **Связанные документы:** [`DESIGN.md`](../architecture/DESIGN.md), [`DESIGN-CARD-VALUE-AND-LEAGUES.md`](./DESIGN-CARD-VALUE-AND-LEAGUES.md), [`DESIGN-NOTIFICATIONS.md`](./DESIGN-NOTIFICATIONS.md), [`DESIGN-MARKETPLACE.md`](./DESIGN-MARKETPLACE.md), [`DESIGN-ACHIEVEMENTS-REWARD-REWORK.md`](./DESIGN-ACHIEVEMENTS-REWARD-REWORK.md)

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
7. **Launch baseline.** Достижения V1 стартуют с момента релиза: старые действия не дают completed/unclaimed и не создают разовую выдачу фантиков.

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

### 4.2 Экономика, косметика и карточные награды

Разрешенные награды:

- фантики;
- профильные рамки;
- бейджи и cosmetic-стили бейджей;
- профильная косметика: title, accent/background, декоративные элементы витрины;
- другие визуальные unlock'и профиля;
- игровые карты как контролируемые achievement rewards.

Запрещенные награды:

- бонусы к `total_score`;
- множители редкости;
- дополнительные перки;
- extra uses;
- обход `value_cap`, `max_team_size`, `max_legendary_count`;
- скидки/привилегии, которые прямо усиливают команду в серии.

Карточная награда считается допустимой, если она выдает обычный `user_card` в рамках существующей карточной экономики. Она может быть ценной, продаваться и использоваться в командах, но не должна обходить правила серий: `value_cap`, `max_team_size`, `max_legendary_count`, uses/renewal и обычные ограничения редкости продолжают применяться.

### 4.3 Фантики как небольшой бонус

Фантики не должны становиться главным источником валюты. Основная ценность достижений - статус, профиль, косметика и редкие карточные награды на сложных порогах.

Рекомендуемые вилки:

| Тип достижения | Награда |
|----------------|---------|
| Малое гринд-достижение | 10-25₣ |
| Средний порог | 30-75₣ |
| Длинная цепочка вроде 30 бюджетных серий | 100-200₣ |
| Очень редкое достижение | до 250₣, как исключение |

Перед включением достижений админка должна показывать dry-run потенциальной launch liability. Для V1 целевой результат dry-run по seed-каталогу - **0₣ мгновенной выдачи**, потому что все достижения считаются только от момента запуска.

### 4.4 Карточные награды и `card_template`

Future-награды достижений могут выдавать игровые карты, если достижение достаточно сложное или тематически ограниченное. Поддерживаемые направления:

- случайная карта заданной редкости;
- несколько карт, включая наборы разных редкостей;
- выбор одной карты из предложенного roll;
- выбор карты из каталога игроков, включая сценарий "любой `fantasy_player` нужной редкости";
- achievement-edition карта со специальным `card_skin_id`.

Доменный инвариант: **любой игровой путь, который создает `user_card`, должен иметь возможность создать новый `card_template`, если подходящего template еще нет**. Это относится не только к пакам, но и к future achievement rewards, выбору карты из каталога, специальным событиям и другим источникам карт. Выдача карты не должна зависеть от того, успел ли админ заранее создать `card_template` для пары `fantasy_player + rarity + perks policy`.

`card_template` не является источником доступности карты. Он материализует комбинацию `fantasy_player + rarity + perks`; доступность определяется правилами конкретного источника: активный пак, reward metadata, каталог игроков или специальное событие. Если achievement reward создала template, будущий pack opening или другая механика могут переиспользовать тот же template.

Правила выбора игроков:

- `RANDOM_CARD` выбирает eligible `fantasy_player` из правил активных паков, затем делает template find-or-create.
- `CARD_CHOICE_ROLL` генерирует N eligible вариантов из правил активных паков, после выбора делает template find-or-create.
- `CARD_CHOICE_CATALOG` показывает весь каталог `fantasy_player`; пользователь выбирает игрока, backend делает template find-or-create для заданной редкости.

Achievement-edition не требует отдельного `card_template`. Специальный стиль применяется через `user_card.card_skin_id` и provenance/audit snapshot награды. Такие карты можно продавать на marketplace, если обычные правила карты и marketplace это разрешают.

Отдельного `CARD_SKIN_UNLOCK` как пользовательской награды быть не должно. Игроки не управляют скинами карт отдельно; скин появляется только как часть сгенерированной achievement-edition карты.

При claim или финальном выборе нужно сохранить audit snapshot: achievement code, reward type, rarity, выбранный или сгенерированный `fantasy_player_id`, `card_template_id`, `user_card_id`, skin/provenance и timestamp.

### 4.5 Длинные цепочки достижений

V1 seed оставляет верхние пороги короткими, но каталог должен расширяться за счет новых уровней в существующих `chain_group`, не за счет изменения уже полученных достижений. Например:

- `team_submit`: 1 / 5 / 15 / 30 / 50 / 100 / 150;
- `budget_team`: 1 / 5 / 15 / 30 / 50 / 100 / 150;
- `pack_open`: 1 / 5 / 15 / 30 / 50 / 100 / 150;
- marketplace-треки: 1 / 5 / 15 / 30 / 50+ там, где это не стимулирует абьюз.

Новые верхние уровни должны использовать тот же `condition_type`, больший `target_value` и следующий `chain_level`. Для длинных уровней предпочтительнее карточные и косметические награды, а не рост фантиков: так достижение остается желанным, но не становится главным источником валюты.

---

## 5. Seed-каталог V1

V1 должен стартовать с конкретным каталогом, а не с абстрактными категориями. Ниже - рекомендуемый seed на **42 основных достижения** и **2 optional secret-достижения**. Его можно скорректировать по названиям/иконкам, но коды и условия лучше считать продуктовым контрактом первого релиза.

Колонка `История` задает, как достижение относится к данным до релиза.

**Решение V1:** все seed-достижения должны иметь `FROM_ACHIEVEMENTS_LAUNCH`, а не `RETROACTIVE_CUMULATIVE` или `CURRENT_STATE`. Раздел 16 показывает, что retroactive/current-state запуск создает слишком большую мгновенную выдачу фантиков, поэтому старые действия и текущее состояние коллекции не завершают достижения на старте.

- `FROM_ACHIEVEMENTS_LAUNCH` - считается только после релиза достижений. Старые команды, победы, сделки, паки, апгрейды и текущая коллекция не дают progress/completed при запуске.
- `RETROACTIVE_CUMULATIVE` и `CURRENT_STATE` остаются допустимыми техническими policy для будущих специальных кейсов, но **не используются в V1 seed-каталоге**.

### 5.1 Участие и дисциплина

Считать уникальные `fantasy_team` по пользователю и `series_league`. Update состава не должен увеличивать прогресс повторно.

| Code | Название | Условие | История | Награда |
|------|----------|---------|---------|---------|
| `team_submit_1` | Первый состав | Создать первую команду в любой лиге | `FROM_ACHIEVEMENTS_LAUNCH` | 10₣ |
| `team_submit_5` | Регулярный участник | Создать команды в 5 лигах серий | `FROM_ACHIEVEMENTS_LAUNCH` | 25₣ |
| `team_submit_15` | В расписании | Создать команды в 15 лигах серий | `FROM_ACHIEVEMENTS_LAUNCH` | 50₣ |
| `team_submit_30` | Стабильный менеджер | Создать команды в 30 лигах серий | `FROM_ACHIEVEMENTS_LAUNCH` | 100₣ + badge |
| `dual_league_1` | Двойная заявка | В одной серии создать команды в MAIN и BUDGET | `FROM_ACHIEVEMENTS_LAUNCH` | 25₣ |
| `dual_league_10` | Две стратегии | В 10 разных сериях создать команды в MAIN и BUDGET | `FROM_ACHIEVEMENTS_LAUNCH` | 75₣ + badge |

### 5.2 Бюджетная лига

Это главный гринд-трек V1. Он **начинается с нуля** при релизе достижений: существующие `fantasy_team` в `series_league.league.code = 'BUDGET'` используются только для аналитического dry-run из раздела 16, но не засчитываются пользователям как прогресс.

| Code | Название | Условие | История | Награда |
|------|----------|---------|---------|---------|
| `budget_team_1` | Первый бюджет | Создать первую команду в BUDGET | `FROM_ACHIEVEMENTS_LAUNCH` | 10₣ |
| `budget_team_5` | Бюджетный старт | Создать команду в BUDGET 5 раз | `FROM_ACHIEVEMENTS_LAUNCH` | 25₣ |
| `budget_team_15` | Экономный стратег | Создать команду в BUDGET 15 раз | `FROM_ACHIEVEMENTS_LAUNCH` | 75₣ |
| `budget_team_30` | Мастер бюджета | Создать команду в BUDGET 30 раз | `FROM_ACHIEVEMENTS_LAUNCH` | 150₣ + profile frame |
| `budget_win_1` | Бюджетная победа | Победить в BUDGET-лиге завершенной серии | `FROM_ACHIEVEMENTS_LAUNCH` | 75₣ + badge |
| `budget_top10_5` | Бюджетный топ | 5 раз попасть в топ-10 BUDGET | `FROM_ACHIEVEMENTS_LAUNCH` | 50₣ |

### 5.3 Результаты

Достижения результатов должны поддерживать победителей и регулярных участников. Считать только `series.status = FINISHED`; для ничьих использовать тот же порядок leaderboard, что в UI/финализации.

| Code | Название | Условие | История | Награда |
|------|----------|---------|---------|---------|
| `series_win_1` | Первая победа | Победить в любой лиге завершенной серии | `FROM_ACHIEVEMENTS_LAUNCH` | 75₣ + badge |
| `series_win_3` | Серийный победитель | Победить в 3 лигах серий | `FROM_ACHIEVEMENTS_LAUNCH` | 100₣ |
| `series_win_10` | Династия | Победить в 10 лигах серий | `FROM_ACHIEVEMENTS_LAUNCH` | 200₣ + profile frame |
| `top3_5` | На пьедестале | 5 раз попасть в топ-3 | `FROM_ACHIEVEMENTS_LAUNCH` | 75₣ |
| `top10_10` | В верхней группе | 10 раз попасть в топ-10 или верхнюю половину, если участников меньше 10 | `FROM_ACHIEVEMENTS_LAUNCH` | 75₣ |
| `top_quarter_10` | Стабильный результат | 10 раз попасть в верхние 25% leaderboard | `FROM_ACHIEVEMENTS_LAUNCH` | 100₣ + badge |

### 5.4 Коллекция

Коллекционные достижения V1 считаются от launch baseline: карты, которыми пользователь владел до релиза, не завершают достижение автоматически. Soft-deleted карты не считаются; для условий владения после запуска учитываются только карты/состояния, появившиеся или изменившиеся после включения достижений.

| Code | Название | Условие | История | Награда |
|------|----------|---------|---------|---------|
| `cards_total_10` | Первая полка | Владеть 10 активными картами, полученными после запуска достижений | `FROM_ACHIEVEMENTS_LAUNCH` | 25₣ |
| `cards_total_30` | Коллекционер | Владеть 30 активными картами, полученными после запуска достижений | `FROM_ACHIEVEMENTS_LAUNCH` | 75₣ |
| `cards_total_100` | Большая коллекция | Владеть 100 активными картами, полученными после запуска достижений | `FROM_ACHIEVEMENTS_LAUNCH` | 150₣ + badge |
| `first_epic` | Эпический дроп | Получить и владеть активной EPIC-картой после запуска достижений | `FROM_ACHIEVEMENTS_LAUNCH` | 25₣ |
| `first_legendary` | Легенда в коллекции | Получить и владеть активной LEGENDARY-картой после запуска достижений | `FROM_ACHIEVEMENTS_LAUNCH` | 75₣ + badge |
| `first_skin_card` | Особый выпуск | Получить или применить активную карту со скином после запуска достижений | `FROM_ACHIEVEMENTS_LAUNCH` | 50₣ |
| `same_player_3_rarities` | Любимый игрок | После запуска достижений собрать активные карты одного `fantasy_player` в 3 редкостях | `FROM_ACHIEVEMENTS_LAUNCH` | 100₣ + badge |

После `completed_at` достижение не откатывается, даже если пользователь позже продал или переработал карты и текущий счетчик снизился. До `completed_at` прогресс может уменьшаться, если пользователь теряет карту, которая была получена после запуска достижений и входила в launch-baseline счетчик.

### 5.5 Marketplace

Считать только SOLD-листинги без записи в `marketplace_listing_sanction` и только сделки после запуска достижений. Если сделка позже признана нерыночной, V1 не отзывает уже claimed-награду, но новые пересчеты должны исключать такую сделку.

| Code | Название | Условие | История | Награда |
|------|----------|---------|---------|---------|
| `market_buy_1` | Первая покупка | Купить первую карту на marketplace после запуска достижений | `FROM_ACHIEVEMENTS_LAUNCH` | 10₣ |
| `market_buy_5` | Охотник за картами | Купить 5 карт на marketplace после запуска достижений | `FROM_ACHIEVEMENTS_LAUNCH` | 50₣ |
| `market_sell_1` | Первая продажа | Продать первую карту на marketplace после запуска достижений | `FROM_ACHIEVEMENTS_LAUNCH` | 10₣ |
| `market_sell_5` | Продавец | Продать 5 карт на marketplace после запуска достижений | `FROM_ACHIEVEMENTS_LAUNCH` | 50₣ |
| `market_watch_1` | На наблюдении | Создать первый marketplace watch-фильтр после запуска достижений | `FROM_ACHIEVEMENTS_LAUNCH` | 10₣ |
| `market_unique_counterparties_5` | Широкий рынок | Провести после запуска достижений SOLD-сделки с 5 разными контрагентами | `FROM_ACHIEVEMENTS_LAUNCH` | 75₣ + badge |

Запрещено давать достижения за жалобы, частое снятие/перевыставление листингов или сделки с одним и тем же контрагентом.

### 5.6 Паки и апгрейды

Открытия паков, редкие дропы и crafted LEGENDARY считаются только по событиям после запуска достижений. Историческая статистика открытий и текущие `user_card` используются только для оценки launch liability из раздела 16, но не для completion на старте.

| Code | Название | Условие | История | Награда |
|------|----------|---------|---------|---------|
| `pack_open_1` | Первый пак | Открыть 1 пак после запуска достижений | `FROM_ACHIEVEMENTS_LAUNCH` | 10₣ |
| `pack_open_5` | Пять попыток | Открыть 5 паков после запуска достижений | `FROM_ACHIEVEMENTS_LAUNCH` | 25₣ |
| `pack_open_15` | Охота за редкостью | Открыть 15 паков после запуска достижений | `FROM_ACHIEVEMENTS_LAUNCH` | 75₣ |
| `pack_open_30` | Большое вскрытие | Открыть 30 паков после запуска достижений | `FROM_ACHIEVEMENTS_LAUNCH` | 150₣ + badge |
| `pack_epic_drop_1` | Эпик из пака | После запуска достижений получить EPIC из пака и владеть этой картой или ее traceable экземпляром | `FROM_ACHIEVEMENTS_LAUNCH` | 25₣ |
| `legendary_upgrade_1` | Своими руками | Сделать первый legendary upgrade после запуска достижений | `FROM_ACHIEVEMENTS_LAUNCH` | 75₣ + badge |
| `crafted_legendary_3` | Мастер апгрейда | Сделать 3 legendary upgrade после запуска достижений | `FROM_ACHIEVEMENTS_LAUNCH` | cosmetic unlock |

### 5.7 Социальность

Социальные достижения V1 считаются только после релиза, потому что до них не было отдельного надежного события достижения. Клиентское нажатие share не гарантирует фактическую отправку в Telegram, поэтому награды минимальные и одноразовые.

| Code | Название | Условие | История | Награда |
|------|----------|---------|---------|---------|
| `share_profile_1` | Витрина открыта | Нажать share профиля | `FROM_ACHIEVEMENTS_LAUNCH` | 10₣ |
| `share_team_1` | Команда наружу | Нажать share команды | `FROM_ACHIEVEMENTS_LAUNCH` | 10₣ |
| `compare_open_1` | Сравнили составы | Открыть compare-view | `FROM_ACHIEVEMENTS_LAUNCH` | 10₣ |
| `view_public_profile_5` | Скаут | Открыть 5 чужих профилей | `FROM_ACHIEVEMENTS_LAUNCH` | badge |

### 5.8 Особые и секретные

Секретные достижения скрыты до получения или показываются как "???". В V1 их лучше держать выключенными или seed'ить 1-2 cosmetic-only достижения после основного запуска, чтобы не усложнять первый launch baseline.

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

`COMPLETED_UNCLAIMED` нужен намеренно: пользователь видит кнопку "Забрать", а baseline/backfill не меняет баланс без явного действия пользователя.

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
| `CURRENT_STATE` | Future-only policy: прогресс равен текущему состоянию. Если когда-нибудь включается, может сразу завершить достижение при удовлетворенном состоянии. | Не используется в V1 seed. |
| `RETROACTIVE_CUMULATIVE` | Future-only policy: прогресс равен количеству исторических фактов в БД за все время. Если когда-нибудь включается, старые действия входят в прогресс. | Не используется в V1 seed из-за launch liability. |
| `FROM_ACHIEVEMENTS_LAUNCH` | Прогресс начинается с момента включения достижения. Старые действия и текущее состояние на дату релиза не засчитываются. | `budget_team_30`: пользователь с 30 старыми BUDGET-командами стартует с `IN_PROGRESS 0/30`; прогресс начнется со следующей команды после включения достижений. |

Базовое правило V1: все seed-достижения используют `FROM_ACHIEVEMENTS_LAUNCH`. `RETROACTIVE_CUMULATIVE` и `CURRENT_STATE` можно добавить только отдельным осознанным решением после оценки экономики.

### 6.4 Launch baseline и backfill

Dry-run/backfill обязателен для релиза, но в V1 он не должен выдавать старые достижения пользователям.

Режимы:

- dry-run по всем пользователям;
- dry-run по одному пользователю;
- apply по всем пользователям;
- apply по одному пользователю;
- пересчет конкретного `condition_type`.

Для V1 seed с `FROM_ACHIEVEMENTS_LAUNCH` apply-режим фиксирует launch baseline и/или оставляет progress пустым до новых событий. Он не выставляет `completed_at` по действиям до релиза и не создает `COMPLETED_UNCLAIMED` на старте.

Конкретные примеры:

- `cards_total_100`: пользователь с 100 активными картами на дату релиза стартует с `IN_PROGRESS 0/100`; засчитываются только подходящие карты, полученные после запуска достижений.
- `budget_team_30`: пользователь с 30 уже созданными BUDGET-командами стартует с `IN_PROGRESS 0/30`; следующая BUDGET-команда после релиза даст первый прогресс.
- `share_profile_1`: старые шаринги не считаются; прогресс начнется с первого события после включения достижения.
- `market_sell_5`: старые SOLD-листинги не считаются; после запуска учитываются только сделки без санкции.

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

Награды лучше хранить отдельно, чтобы одно достижение могло дать фантики, косметику и future карточные rewards.

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
- `BADGE_STYLE`;
- `COSMETIC_UNLOCK`.

Полный список future reward types:

- `FANTIKI` - фиксированная сумма фантиков;
- `PROFILE_FRAME` - unlock рамки профиля;
- `BADGE_STYLE` - стиль/вариант achievement badge;
- `PROFILE_COSMETIC` - title, background/accent, эффект витрины или другой профильный unlock;
- `RANDOM_CARD`;
- `CARD_CHOICE_ROLL`;
- `CARD_CHOICE_CATALOG`;
- `FIXED_CARD`.

`RANDOM_CARD` покрывает как одну карту, так и набор карт. Для набора разных редкостей используется `items`, а не отдельный reward type:

```json
{
  "items": [
    { "rarity": "COMMON", "quantity": 2 },
    { "rarity": "RARE", "quantity": 1 }
  ],
  "pool": "ACTIVE_PACKS"
}
```

`CARD_CHOICE_ROLL` предлагает пользователю один или несколько вариантов из eligible игроков активных паков. `CARD_CHOICE_CATALOG` позволяет выбрать любого `fantasy_player` из каталога для заданной редкости. `FIXED_CARD` выдает конкретного игрока и редкость; это редкий инструмент для special/secret/promotional achievements. Во всех карточных режимах `card_template` ищется или создается после выбора/roll, а не используется как источник доступности.

Для карточных rewards `metadata` должен фиксировать как минимум `rarity` или `items`, `quantity`, `pool` / `choiceMode`, optional `skinCode`, optional `choiceCount` и правила find-or-create `card_template`. При claim или финальном выборе `reward_snapshot` должен сохранять фактически выданные `card_template_id`, `user_card_id`, выбранный `fantasy_player_id`, редкость, skin и provenance.

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

`user_cosmetic_unlock` используется для профильных рамок, badge styles и профильной косметики. Скины карт не выдаются как отдельное пользовательское право; они появляются только на конкретной сгенерированной achievement-edition карте через `user_card.card_skin_id`.

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
| `AchievementBackfillService` | Dry-run launch liability, фиксация launch baseline, repair-пересчет по событиям после запуска. |
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

Backfill вызывает `currentProgress` и применяет `history_policy`. Для V1 seed все определения `FROM_ACHIEVEMENTS_LAUNCH`: сервис не пытается восстановить старые события, а dry-run старых фактов используется только как экономическая оценка. `RETROACTIVE_CUMULATIVE` и `CURRENT_STATE` остаются технически возможными, но не должны попадать в V1 seed без отдельного решения.

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
          "historyPolicy": "FROM_ACHIEVEMENTS_LAUNCH",
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
| `POST` | `/api/v1/admin/achievements/backfill/dry-run` | Оценить изменения, launch liability и потенциальную сумму rewards. |
| `POST` | `/api/v1/admin/achievements/backfill` | Зафиксировать baseline или запустить repair-пересчет. |
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

Backfill / launch baseline UI:

- dry-run перед запуском;
- estimate: affected users, new completed, potential ₣ liability; для V1 seed ожидается `new completed = 0` и `potential ₣ liability = 0`;
- запуск job;
- статус и ошибки;
- repair для одного пользователя.

---

## 12. Notifications and product events

В V1 не отправляем Telegram-сообщение на каждое достижение. Причины:

- запуск через `FROM_ACHIEVEMENTS_LAUNCH` не должен открывать достижения пачкой в день релиза;
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
7. **Launch liability dry-run обязателен перед включением claim.** Для V1 seed ожидаемый instant payout - 0₣.
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
- Backfill/baseline CLI/admin endpoint с dry-run launch liability.
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
- Baseline/backfill job UI.

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
- launch baseline/backfill does not duplicate claim.

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

## 16. Production launch-liability estimate

Снимок ниже посчитан по production DB на **2026-05-25** read-only запросами через `polemica-prod-db-readonly`. Это не статичная продуктовая истина: перед релизом достижений dry-run нужно повторить.

Важный вывод: вариант V1 seed с retroactive/current-state fantiki-наградами создает слишком большую потенциальную разовую выдачу. Если все пользователи заберут уже выполненные награды, верхняя оценка по текущему production snapshot: **343 695₣**.

**Решение после оценки:** V1 seed запускается только с `FROM_ACHIEVEMENTS_LAUNCH` для всех достижений. Это означает, что старые действия не создают `COMPLETED_UNCLAIMED` на старте, а ожидаемая мгновенная выдача фантиков по seed-каталогу равна **0₣**.

Разбивка потенциальной мгновенной выдачи в отклоненном retroactive/current-state варианте:

| Блок | Potential ₣ liability |
|------|-----------------------|
| Участие + бюджет | 77 505₣ |
| Результаты | 45 725₣ |
| Коллекция | 123 200₣ |
| Marketplace | 30 435₣ |
| Паки и апгрейды | 66 830₣ |
| Social/from-launch | 0₣ |
| **Итого** | **343 695₣** |

### 16.1 Rejected instant completion by achievement

Таблица ниже сохраняется как аргумент против retroactive/current-state запуска. Это **не** policy для V1 seed после принятого решения.

| Code | Отклоненная история | Instant users | Potential ₣ |
|------|--------------------|---------------|-------------|
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

### 16.2 Launch payout decision

Do not ship the seed with retroactive/current-state fantiki claim enabled. Final policy for V1 launch:

1. Every V1 seed achievement uses `FROM_ACHIEVEMENTS_LAUNCH`.
2. Retroactive completion does not unlock achievement status, badges, profile frames or fantiki at launch.
3. Current-state collection/card achievements use launch baseline semantics, so existing cards do not complete achievements immediately.
4. Admin dry-run before enabling claim must show `0₣` instant payout for the V1 seed.
5. Future retroactive/current-state achievements require separate product approval and an explicit economy cap.

## 17. Открытые вопросы

1. Финальные названия, иконки, цвета и точные cosmetic rewards для seed-каталога V1.
2. Нужен ли отдельный каталог `profile_frame` или достаточно `user_cosmetic_unlock` с metadata в `achievement_reward`.
3. Показывать ли `COMPLETED_UNCLAIMED` достижения в публичном профиле до claim. Рекомендация: не показывать в featured до claim, но считать completed в личной статистике.
4. Перед релизом повторить production dry-run и подтвердить, что V1 seed с `FROM_ACHIEVEMENTS_LAUNCH` дает 0₣ instant payout.
