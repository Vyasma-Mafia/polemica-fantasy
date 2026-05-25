# Achievement Reward Rework Draft

> Статус: draft для продуктового редактирования.
> Основан на `DESIGN-ACHIEVEMENTS.md`.
> Ретро-оценка: production DB, read-only snapshot от 2026-05-25.

## Как читать `Ретро игроков`

`Ретро игроков` показывает, сколько пользователей уже выполнили бы условие, если бы достижение считалось ретроспективно по текущим production-данным. Это не означает, что достижение нужно запускать retroactive: базовая политика остается `FROM_ACHIEVEMENTS_LAUNCH`.

Ограничения подсчета:

- Составы считаются по историческим `fantasy_team`; для production в этом snapshot использовался `submitted_at`/наличие строки, потому что `fantasy_team.created_at` появится миграцией достижений.
- Результаты считаются по `series.status = 'FINISHED'`, `fantasy_team.total_score` и ранжированию `total_score DESC, fantasy_team.id ASC`.
- Коллекционные условия считаются по текущим активным картам (`user_card.deleted_at IS NULL`).
- Открытия паков считаются по `user_card_pack_opens.open_count`.
- Social-события `SHARE_PROFILE`, `SHARE_TEAM`, `COMPARE_OPEN`, `PUBLIC_PROFILE_VIEW` сейчас дают 0, потому что achievement-specific event tracking еще не был включен в production на момент snapshot.

## Баланс по ретро-частоте

Эта таблица использует `Ретро игроков` как proxy сложности:

| Ретро игроков | Интерпретация | Тип награды |
|---:|---|---|
| 300+ | массовое/онбординговое | 10-25₣ или `CARD_CHOICE_ROLL` 1 из 3 COMMON |
| 150-299 | очень доступное | `CARD_CHOICE_ROLL` 1 из 3 или 2 из 5 COMMON, badge style + реальный довесок |
| 50-149 | регулярный прогресс | `CARD_CHOICE_ROLL` 2 из 5 COMMON/RARE, badge + карта |
| 10-49 | заметное достижение | `CARD_CHOICE_ROLL` 2 из 5 RARE или 1 из 3 EPIC для тематичных условий |
| 1-9 | редкое достижение | `CARD_CHOICE_ROLL` 1 из 3 EPIC или 2 из 5 RARE, strong profile cosmetic + card, иногда frame |
| 0 | aspirational/future goal | frame + `CARD_CHOICE_ROLL` 2 из 5 RARE/EPIC, styled card, challenge-награда; LEGENDARY только для самых тяжелых целей |

Это не жесткая формула. Тематичность важна: редкий BUDGET/common-only challenge может получить styled-карту, а массовое получение LEGENDARY не должно получать frame. Косметические награды не должны идти в одиночку: вместе с `BADGE_STYLE`, `PROFILE_FRAME` или `PROFILE_COSMETIC` нужно давать "реальную" награду: фантики, `RANDOM_CARD` или `CARD_CHOICE_ROLL`.

## Принципы наград

- Верхние пороги расширяются добавлением новых достижений в существующие `chain_group`, а не изменением уже выданных порогов.
- Фантики остаются ранней наградой и небольшим довеском, но не главным мотиватором длинного гринда.
- `PROFILE_FRAME` дается только за достаточно сложные достижения: длинный гринд, значимые результаты, редкие challenge-линии.
- Отдельной награды `CARD_SKIN_UNLOCK` нет. Скин карты существует только как часть сгенерированной achievement-edition карты через `user_card.card_skin_id`.
- Styled cards должны быть редкими. Предлагаемые дизайн-семейства: `budget_edition`, `common_challenge_edition`, `winner_edition`, `crafter_edition`, `pack_hunter_edition`.
- `RANDOM_CARD` и `CARD_CHOICE_ROLL` выбирают eligible `fantasy_player` из правил активных паков, затем используют общий find-or-create `card_template`.
- `CARD_CHOICE_CATALOG` позволяет выбрать любого `fantasy_player` из каталога для заданной редкости.
- `CARD_CHOICE_ROLL` предпочтительнее `RANDOM_CARD` для многих массовых/средних достижений: ранние уровни могут давать выбор 1 из 3, а средние/сложные уровни чаще должны давать 2 из 5.
- `CARD_CHOICE_ROLL` 2 из 5 означает выбор двух разных карт из пяти вариантов, сгенерированных по тем же правилам eligibility и редкости.
- Получение первой LEGENDARY-карты не должно давать `PROFILE_FRAME`, потому что LEGENDARY пользователи могут получить относительно быстро.

## Участие и дисциплина

| Условие достижения | Ретро игроков | Новая награда |
|---|---:|---|
| Создать первую команду в любой лиге | 439 | 10₣ |
| Создать команды в 5 лигах серий | 315 | `CARD_CHOICE_ROLL` 1 из 3 COMMON |
| Создать команды в 15 лигах серий | 243 | `CARD_CHOICE_ROLL` 2 из 5 COMMON |
| Создать команды в 30 лигах серий | 170 | `BADGE_STYLE` `stable_manager` + `CARD_CHOICE_ROLL` 2 из 5 COMMON |
| Создать команды в 50 лигах серий | 116 | `CARD_CHOICE_ROLL` 2 из 5 RARE |
| Создать команды в 100 лигах серий | 7 | `CARD_CHOICE_ROLL` 1 из 3 EPIC + `PROFILE_COSMETIC` title |
| Создать команды в 150 лигах серий | 0 | `PROFILE_FRAME` + `PROFILE_COSMETIC` title + `CARD_CHOICE_ROLL` 2 из 5 EPIC |
| В одной серии создать команды в MAIN и BUDGET | 287 | 25₣ |
| В 10 разных сериях создать команды в MAIN и BUDGET | 135 | `BADGE_STYLE` `dual_strategy` + `CARD_CHOICE_ROLL` 2 из 5 COMMON |
| В 25 разных сериях создать команды в MAIN и BUDGET | 53 | `CARD_CHOICE_ROLL` 2 из 5 RARE |
| В 50 разных сериях создать команды в MAIN и BUDGET | 0 | `PROFILE_COSMETIC` background/accent + `CARD_CHOICE_ROLL` 1 из 3 EPIC |

## Бюджетная лига

| Условие достижения | Ретро игроков | Новая награда |
|---|---:|---|
| Создать первую команду в BUDGET | 291 | 10₣ |
| Создать команду в BUDGET 5 раз | 195 | `CARD_CHOICE_ROLL` 1 из 3 COMMON |
| Создать команду в BUDGET 15 раз | 110 | `CARD_CHOICE_ROLL` 2 из 5 COMMON |
| Создать команду в BUDGET 30 раз | 26 | `budget_edition` `CARD_CHOICE_ROLL` 1 из 3 COMMON + `BADGE_STYLE` |
| Создать команду в BUDGET 50 раз | 0 | `CARD_CHOICE_ROLL` 2 из 5 RARE |
| Создать команду в BUDGET 100 раз | 0 | `PROFILE_COSMETIC` title + `CARD_CHOICE_ROLL` 1 из 3 EPIC |
| Создать команду в BUDGET 150 раз | 0 | `PROFILE_FRAME` `budget_master_elite` + `CARD_CHOICE_ROLL` 2 из 5 EPIC |
| Победить в BUDGET-лиге завершенной серии | 31 | `BADGE_STYLE` `budget_winner` + `CARD_CHOICE_ROLL` 2 из 5 RARE |
| Победить в BUDGET 3 раза | 1 | `CARD_CHOICE_ROLL` 1 из 3 EPIC + `PROFILE_COSMETIC` title |
| Победить в BUDGET 10 раз | 0 | `PROFILE_FRAME` `budget_winner` + `CARD_CHOICE_ROLL` 2 из 5 EPIC |
| Победить в BUDGET 25 раз | 0 | `CARD_CHOICE_ROLL` 2 из 5 EPIC + `PROFILE_COSMETIC` title |
| 5 раз попасть в топ-10 BUDGET | 19 | `CARD_CHOICE_ROLL` 1 из 3 RARE |
| 15 раз попасть в топ-10 BUDGET | 0 | `CARD_CHOICE_ROLL` 2 из 5 RARE |
| 30 раз попасть в топ-10 BUDGET | 0 | `BADGE_STYLE` + `CARD_CHOICE_ROLL` 1 из 3 EPIC |
| 50 раз попасть в топ-10 BUDGET | 0 | `PROFILE_COSMETIC` background/accent + `CARD_CHOICE_ROLL` 2 из 5 EPIC |
| Попасть в топ-10 BUDGET только с COMMON-картами | 5 | `common_challenge_edition` `CARD_CHOICE_CATALOG` 1 COMMON |

## Результаты

| Условие достижения | Ретро игроков | Новая награда |
|---|---:|---|
| Победить в любой лиге завершенной серии | 78 | `BADGE_STYLE` `series_winner` + `CARD_CHOICE_ROLL` 2 из 5 RARE |
| Победить в 3 лигах серий | 9 | `PROFILE_COSMETIC` title + `CARD_CHOICE_ROLL` 1 из 3 EPIC |
| Победить в 10 лигах серий | 0 | `PROFILE_FRAME` `dynasty` + `winner_edition` `CARD_CHOICE_ROLL` 1 из 3 EPIC |
| Победить в 25 лигах серий | 0 | `PROFILE_COSMETIC` title + `CARD_CHOICE_ROLL` 2 из 5 EPIC |
| Победить в 50 лигах серий | 0 | `PROFILE_FRAME` `dynasty_elite` + `CARD_CHOICE_ROLL` 1 из 3 LEGENDARY |
| 5 раз попасть в топ-3 | 14 | `CARD_CHOICE_ROLL` 2 из 5 RARE |
| 15 раз попасть в топ-3 | 0 | `CARD_CHOICE_ROLL` 2 из 5 RARE |
| 30 раз попасть в топ-3 | 0 | `PROFILE_COSMETIC` title + `CARD_CHOICE_ROLL` 1 из 3 EPIC |
| 50 раз попасть в топ-3 | 0 | `CARD_CHOICE_ROLL` 2 из 5 EPIC |
| 10 раз попасть в топ-10 или верхнюю половину | 29 | `CARD_CHOICE_ROLL` 1 из 3 RARE |
| 25 раз попасть в топ-10 или верхнюю половину | 0 | `CARD_CHOICE_ROLL` 2 из 5 RARE |
| 50 раз попасть в топ-10 или верхнюю половину | 0 | `PROFILE_COSMETIC` background/accent + `CARD_CHOICE_ROLL` 1 из 3 EPIC |
| 100 раз попасть в топ-10 или верхнюю половину | 0 | `CARD_CHOICE_ROLL` 2 из 5 EPIC |
| 10 раз попасть в верхние 25% leaderboard | 134 | `BADGE_STYLE` `steady_result` + `CARD_CHOICE_ROLL` 2 из 5 COMMON |
| 25 раз попасть в верхние 25% leaderboard | 24 | `CARD_CHOICE_ROLL` 2 из 5 RARE |
| 50 раз попасть в верхние 25% leaderboard | 0 | `PROFILE_FRAME` `steady_result` + `CARD_CHOICE_ROLL` 2 из 5 RARE |
| 100 раз попасть в верхние 25% leaderboard | 0 | `CARD_CHOICE_ROLL` 2 из 5 EPIC |

## Коллекция

| Условие достижения | Ретро игроков | Новая награда |
|---|---:|---|
| Владеть 10 активными картами | 539 | 10₣ |
| Владеть 30 активными картами | 454 | `CARD_CHOICE_ROLL` 1 из 3 COMMON |
| Владеть 100 активными картами | 101 | `BADGE_STYLE` `big_collection` + `CARD_CHOICE_ROLL` 2 из 5 COMMON |
| Владеть 200 активными картами | 6 | `PROFILE_COSMETIC` collection title + `CARD_CHOICE_ROLL` 2 из 5 RARE |
| Владеть 350 активными картами | 2 | `PROFILE_FRAME` `collector` + `CARD_CHOICE_ROLL` 2 из 5 EPIC |
| Получить и владеть активной EPIC-картой | 553 | `BADGE_STYLE` + `CARD_CHOICE_ROLL` 1 из 3 COMMON |
| Получить и владеть активной LEGENDARY-картой | 190 | `BADGE_STYLE` `legendary_collection` + `CARD_CHOICE_ROLL` 2 из 5 COMMON |
| Получить или применить активную карту со скином | 182 | `BADGE_STYLE` + `CARD_CHOICE_ROLL` 2 из 5 COMMON |
| Собрать активные карты одного игрока в 3 редкостях | 230 | `BADGE_STYLE` `favorite_player` + `CARD_CHOICE_ROLL` 2 из 5 COMMON |

## Паки и апгрейды

| Условие достижения | Ретро игроков | Новая награда |
|---|---:|---|
| Открыть 1 пак | 559 | 10₣ |
| Открыть 5 паков | 484 | `CARD_CHOICE_ROLL` 1 из 3 COMMON |
| Открыть 15 паков | 216 | `CARD_CHOICE_ROLL` 2 из 5 COMMON |
| Открыть 30 паков | 34 | `BADGE_STYLE` `pack_opener` + `CARD_CHOICE_ROLL` 2 из 5 RARE |
| Открыть 50 паков | 6 | `CARD_CHOICE_ROLL` 1 из 3 EPIC |
| Открыть 100 паков | 1 | `PROFILE_FRAME` `pack_hunter` + `CARD_CHOICE_ROLL` 2 из 5 EPIC |
| Открыть 150 паков | 0 | `pack_hunter_edition` `CARD_CHOICE_ROLL` 1 из 3 EPIC |
| Получить EPIC из пака и владеть этой картой | 552 | `BADGE_STYLE` + `CARD_CHOICE_ROLL` 1 из 3 COMMON |
| Сделать первый legendary upgrade | 192 | `BADGE_STYLE` `crafted_legendary` + `CARD_CHOICE_ROLL` 2 из 5 COMMON |
| Сделать 3 legendary upgrade | 56 | `PROFILE_COSMETIC` crafter title + `CARD_CHOICE_ROLL` 2 из 5 RARE |
| Сделать 10 legendary upgrade | 1 | `PROFILE_FRAME` `legendary_crafter` + `crafter_edition` `CARD_CHOICE_ROLL` 1 из 3 EPIC |

## Marketplace

| Условие достижения | Ретро игроков | Новая награда |
|---|---:|---|
| Купить первую карту на marketplace | 259 | 10₣ |
| Купить 5 карт на marketplace | 134 | `CARD_CHOICE_ROLL` 2 из 5 COMMON |
| Купить 15 карт на marketplace | 44 | `CARD_CHOICE_ROLL` 2 из 5 RARE |
| Купить 30 карт на marketplace | 12 | `CARD_CHOICE_ROLL` 2 из 5 RARE |
| Продать первую карту на marketplace | 218 | 10₣ |
| Продать 5 карт на marketplace | 115 | `CARD_CHOICE_ROLL` 2 из 5 COMMON |
| Продать 15 карт на marketplace | 38 | `CARD_CHOICE_ROLL` 2 из 5 RARE |
| Продать 30 карт на marketplace | 17 | `PROFILE_COSMETIC` market title/background + `CARD_CHOICE_ROLL` 2 из 5 RARE |
| Создать первый marketplace watch-фильтр | 35 | 25₣ |
| Создать 5 marketplace watch-фильтров | 4 | `BADGE_STYLE` + `CARD_CHOICE_ROLL` 1 из 3 RARE |
| Провести SOLD-сделки с 5 разными контрагентами | 177 | `BADGE_STYLE` `wide_market` + `CARD_CHOICE_ROLL` 2 из 5 COMMON |
| Провести SOLD-сделки с 15 разными контрагентами | 81 | `CARD_CHOICE_ROLL` 2 из 5 RARE |
| Провести SOLD-сделки с 30 разными контрагентами | 26 | `CARD_CHOICE_ROLL` 1 из 3 EPIC + `PROFILE_COSMETIC` title |

## Социальность

| Условие достижения | Ретро игроков | Новая награда |
|---|---:|---|
| Нажать share профиля | 0 | `BADGE_STYLE` + 25₣ |
| Нажать share команды | 0 | 25₣ |
| Открыть compare-view | 0 | 25₣ |
| Открыть 5 чужих профилей | 0 | `BADGE_STYLE` `scout` + 25₣ |
| Открыть 25 чужих профилей | 0 | `PROFILE_COSMETIC` scout title/background + `CARD_CHOICE_ROLL` 2 из 5 COMMON |

## Новые challenge-достижения

| Условие достижения | Ретро игроков | Предлагаемая награда |
|---|---:|---|
| Попасть в топ-10 BUDGET только с COMMON-картами | 5 | `common_challenge_edition` `CARD_CHOICE_CATALOG` 1 COMMON |
| Победить в MAIN без LEGENDARY-карт в составе | 39 | `BADGE_STYLE` + `CARD_CHOICE_ROLL` 2 из 5 RARE |
| Попасть в топ-3 командой из одной карты | 19 | `PROFILE_COSMETIC` title + `CARD_CHOICE_ROLL` 2 из 5 RARE |
| В одной серии попасть в топ-10 и MAIN, и BUDGET | 64 | `BADGE_STYLE` + `CARD_CHOICE_ROLL` 2 из 5 COMMON |
| Собрать активные карты одного игрока во всех 4 редкостях | 3 | `PROFILE_FRAME` + `CARD_CHOICE_CATALOG` 1 EPIC |
| Владеть 3 активными LEGENDARY-картами | 56 | `BADGE_STYLE` + `PROFILE_COSMETIC` title + `CARD_CHOICE_ROLL` 1 из 3 RARE |
| Открыть 3 разных типа паков | 437 | low-tier `BADGE_STYLE` + 10₣ |
| Купить и продать хотя бы по одной карте на marketplace | 169 | `BADGE_STYLE` trader + `CARD_CHOICE_ROLL` 2 из 5 COMMON |
| Переработать 10 карт | 213 | 10₣ |
| Переработать 50 карт | 116 | 25₣ |
| Переработать 100 карт | 58 | `PROFILE_COSMETIC` title + `CARD_CHOICE_ROLL` 1 из 3 RARE |
| Переработать 250 карт | 4 | `PROFILE_FRAME` recycling master + `CARD_CHOICE_ROLL` 1 из 3 EPIC |
| Продлить контракт карты 1 раз | 123 | 10₣ |
| Продлить контракт карты 10 раз | 17 | `BADGE_STYLE` + `CARD_CHOICE_ROLL` 2 из 5 COMMON |
| Продлить контракт карты 25 раз | 0 | `PROFILE_COSMETIC` title + `CARD_CHOICE_ROLL` 2 из 5 RARE |
| Продлить контракт карты 50 раз | 0 | `PROFILE_FRAME` contract keeper + `CARD_CHOICE_ROLL` 1 из 3 EPIC |

## Secret / Easter Egg Candidates

Secret-достижения лучше делать скрытыми до выполнения. Они должны поощрять необычные паттерны игры, но не подталкивать к вредному поведению вроде намеренного проигрыша.

| Условие достижения | Ретро игроков | Предлагаемая награда |
|---|---:|---|
| Победить в BUDGET только с COMMON-картами | 0 | `common_challenge_edition` `CARD_CHOICE_CATALOG` 1 EPIC |
| Победить в MAIN только с COMMON-картами | 0 | `common_challenge_edition` `CARD_CHOICE_CATALOG` 1 EPIC |
| Попасть в топ-3 MAIN только с COMMON-картами | 0 | `common_challenge_edition` `CARD_CHOICE_CATALOG` 1 RARE |
| Попасть в топ-3 BUDGET только с COMMON-картами | 1 | `common_challenge_edition` `CARD_CHOICE_CATALOG` 1 RARE |
| Победить в MAIN без EPIC и LEGENDARY в составе | 10 | `BADGE_STYLE` + `CARD_CHOICE_ROLL` 2 из 5 RARE |
| Попасть в топ-3 MAIN без EPIC и LEGENDARY в составе | 26 | `BADGE_STYLE` + `CARD_CHOICE_ROLL` 1 из 3 RARE |
| Победить командой из одной карты | 8 | `PROFILE_COSMETIC` title + `CARD_CHOICE_ROLL` 1 из 3 EPIC |
| Попасть в топ-10 командой из одной карты | 52 | `BADGE_STYLE` + `CARD_CHOICE_ROLL` 2 из 5 COMMON |
| Занять ровно 10-е место | 84 | easter egg `BADGE_STYLE` + `CARD_CHOICE_ROLL` 1 из 3 COMMON |
| Попасть в топ-3 составом из карт одной редкости | 62 | `BADGE_STYLE` + `CARD_CHOICE_ROLL` 2 из 5 COMMON |
| Попасть в топ-3 составом из трех разных редкостей | 59 | `BADGE_STYLE` + `CARD_CHOICE_ROLL` 2 из 5 COMMON |
| Победить составом из трех разных редкостей | 25 | `PROFILE_COSMETIC` title + `CARD_CHOICE_ROLL` 2 из 5 RARE |
| Купить LEGENDARY-карту на marketplace | 32 | `BADGE_STYLE` + `CARD_CHOICE_ROLL` 2 из 5 RARE |
| Продать LEGENDARY-карту на marketplace | 22 | `BADGE_STYLE` + `CARD_CHOICE_ROLL` 2 из 5 RARE |
| Купить карту на marketplace за 500₣ или дороже | 35 | `BADGE_STYLE` + `CARD_CHOICE_ROLL` 2 из 5 RARE |
| Продать карту на marketplace за 500₣ или дороже | 20 | `PROFILE_COSMETIC` title + `CARD_CHOICE_ROLL` 2 из 5 RARE |
| Владеть 5 активными картами одного игрока | 61 | `BADGE_STYLE` + `CARD_CHOICE_ROLL` 2 из 5 COMMON |
| Владеть 10 активными картами одного игрока | 3 | `PROFILE_COSMETIC` title + `CARD_CHOICE_ROLL` 2 из 5 RARE |

## Styled Card Families

| Family | Где используется | Ограничение |
|---|---|---|
| `budget_edition` | 30 BUDGET-команд | `CARD_CHOICE_ROLL` 1 из 3 COMMON |
| `common_challenge_edition` | challenge только COMMON-картами | COMMON/RARE/EPIC на выбор из каталога |
| `winner_edition` | 10 побед в любой лиге | `CARD_CHOICE_ROLL` 1 из 3 EPIC |
| `crafter_edition` | 10 legendary upgrade | `CARD_CHOICE_ROLL` 1 из 3 EPIC |
| `pack_hunter_edition` | 150 открытых паков | `CARD_CHOICE_ROLL` 1 из 3 EPIC |

## Notes For Next Review

- `series_win_50` подтвержден как тяжелое aspirational достижение с `CARD_CHOICE_ROLL` 1 из 3 LEGENDARY.
- В новой версии таблицы многие средние/сложные награды подняты до `CARD_CHOICE_ROLL` 2 из 5; ранние массовые условия чаще остаются 1 из 3 COMMON или фантиками.
- `PROFILE_FRAME` намеренно убран из награды за первую LEGENDARY-карту.
- После rebalance по `Ретро игроков` frame остались только на редких/aspirational условиях: 150 participation, BUDGET 150, BUDGET win 10, series win 10/50, top quarter 50, collection 350, pack open 100, legendary upgrade 10 и некоторые новые challenge-линии.
- Все косметические награды должны иметь реальный довесок: фантики, `RANDOM_CARD` или чаще `CARD_CHOICE_ROLL`.
- Достижение "получить LEGENDARY из пака" исключено из списка, потому что LEGENDARY не выпадает из паков.
- Ретро-числа для collection/current-state условий высокие; для запуска нужно оставить `FROM_ACHIEVEMENTS_LAUNCH`, иначе будет большая мгновенная выдача.
