# Active Context

## Текущий фокус
**V2 полностью реализована.** Все 6 концептуальных изменений внедрены (агенты B1–B7). `DESIGN.md` обновлён под актуальное состояние кода. Спецификация: [`CHANGES-V2.md`](../CHANGES-V2.md); план: [`PLAN-V2.md`](../PLAN-V2.md).

**Деплой:** TMA `https://fantasy.maftourbot.ru`, админка `https://admin.fantasy.maftourbot.ru`, бэкенд в Docker (`docker-compose.prod.yml`). На сервере **`~/polemica-fantasy`** — **git clone** ветки **`master`** (`git@github.com:Vyasma-Mafia/polemica-fantasy.git`, SSH).

## Реализованные изменения V2
1. **Фантики** — внутриигровая валюта (стартовый баланс 1000, отображение в TMA, начисление через админку, `fantiki_transaction` для аудита)
2. **Достижения** — справочник `achievement` в БД (8 типов по эталону polemica-achievement-service, V10); `voteForBlack` — `MULTIPLE_PER_GAME`; остальные — `ONCE_PER_GAME`; роли по смыслу ачивки; `can_appear_on_random_cards` — после V11 **все** записи каталога `TRUE` (random cards в паках); `AchievementType` enum удалён; `CardTemplateAchievement.bonus_points` nullable
3. **Автогенерация паков** — `card_pack.auto_generated`, пул игроков (`card_pack_player` / all tournament), генерация при открытии (Rare +1 ачивка, Epic +2, Legendary не участвует), переиспользование CardTemplate при совпадении
4. **Магазин паков** — `GET /store/packs`, `POST /store/packs/{id}/buy`, анимация открытия (PackOpening component)
5. **Модификатор редкости** — `Rarity.scoreModifier` (COMMON 1.0, RARE 1.1, EPIC 1.15, LEGENDARY 1.25)
6. **Per-game детализация** — `fantasy_team_card_game_score`, `fantasy_team_card_game_achievement`, endpoint `GET /me/fantasy-teams/{seriesId}/details`

## Текущие решения
- Карточки привязаны к `fantasy_player` (глобальный), не к турниру
- Achievement — справочник (не enum); бонус: `CardTemplateAchievement.bonusPoints ?? Achievement.bonusPoints`
- Паки: `probability` убрана, только `cards_count`; auto-gen: `applicableRoles` не фильтруется при генерации (только при скоринге)
- Язык бэкенда: Kotlin; Flyway V1–V11
- Скоринг: `(base + Σachievement) × rarity_modifier`, per-game breakdown хранится в БД; **базовые очки** — `GamePointsService.fetchPlayerStats(polemicaGameId)` (публичная страница `/match/{id}`, поле `points` по позиции за столом), не `PolemicaPlayer.award`
- S3: AWS SDK Java v2, MinIO в dev
- Образ backend: GHCR; на VPS — `docker compose -f docker-compose.prod.yml up -d --build`

## Следующие шаги
1. Опционально: серверный агрегат лидерборда по турниру (сейчас TMA суммирует на клиенте)
2. Опционально: доп. методы в polemica-library (оптимизация `getPlayerGames`)
3. Опционально: подкрутить бонусы по достижениям через админку (базово в V10 всё = 1)

## Открытые вопросы
- Финальная настройка бонусов достижений (через админку)

## Блокеры
- Нет критичных блокеров

## Недавние правки UI (админка)
- **Series → Assign players:** у `Select` включён поиск (`showSearch`) и фильтрация опций по подстроке (без учёта регистра), чтобы быстро находить игрока по нику в длинном списке.

## Недавние правки TMA (карты и коллекция)
- **Карты:** общая палитра редкости — common матовый серый (`--pf-card-common`), rare ледяной cyan с лёгким свечением (`--pf-card-rare`); достижения в виде чипов на оверлее фото (`CardAchievementChips`) на коллекции, сборке команды, раскрытии пака и сводке пака; увеличены шрифты в подписи к карте.
- **Покупка пака:** ответ `POST …/store/packs/{id}/buy` мапится в `UserCardItemDto` через шаблоны из `CardTemplateRepository.findAllByIdWithAchievementsLoaded`, чтобы список достижений не терялся при сборке DTO; на экране раскрытия частицы EPIC/LEGENDARY под текстом (`z-index` у `pf-pack-open__card-cap` выше, чем у `pf-pack-open__particles`).

## Недавние правки TMA (лидерборд и чужие команды)
- **Просмотр команды из лидерборда серии:** `GET /api/v1/series/{id}/users/{telegramId}/fantasy-team` и `.../fantasy-team/details`; в TMA маршрут `/series/:seriesId/leaderboard/player/:telegramId` (`LeaderboardPlayerTeamPage`), переход по строке лидерборда (серия и вкладка серии на лидерборде турнира; вкладка «Общий» без ссылки на команду).

## Недавние правки TMA (сборка команды на серию)
- **`GET /api/v1/me/cards`:** опциональный `seriesId` — только карты игроков из `series_player` (как при валидации fantasy-team); неизвестная серия → 404.
- **`SeriesPlayerEntryDto` / TMA types:** поле `fantasyPlayerId` для фильтра «игрок серии» на экране сборки.
- **`TeamPage`:** запрос карт с `tournamentId` + `seriesId`; вкладки редкости, селект игрока серии, сортировка сетки по убыванию редкости; синхронизация слотов с командой из API и `setQueryData` после сохранения.
- **Тест:** `UserApiIntegrationTest` — `GET me cards with seriesId returns only cards for players on series roster`.

## Недавние правки интеграции
- **polemica-library 1.8.2:** исправлен sync games — ответ `get-games` иногда отдаёт `mmr` объектом; в DTO добавлен `ProfileGameMmrDeserializer`. Сборка бэкенда: `../polemica-library` → `./gradlew publishToMavenLocal`, затем fantasy-backend подтянет 1.8.2 из `mavenLocal()` до публикации в Central.
- **polemica-library (локально):** `Invalid enum value: 0` при `GET /v1/matches/{id}` — в JSON голосов встречается `candidate: 0` (не место за столом 1–10). Исправление: `PolemicaVote.candidate` как `Position?`, десериализация `0` → `null`, доработка `GameUtils` (игнор таких строк в подсчётах). После правки — снова `publishToMavenLocal` / bump версии библиотеки.
- **polemica-library:** `MissingKotlinParameterException` на `referee` — в части ответов API поле отсутствует или `null`. Исправление: `PolemicaGame.referee` → `PolemicaUser?`.
