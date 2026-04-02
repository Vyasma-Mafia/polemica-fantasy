# Active Context

## Текущий фокус
**Ростер серии и фэнтези:** при смене игроков серии до дедлайна `FantasyTeamRosterPruningService` убирает из `fantasy_team_card` карты игроков, которых больше нет в `series_player` (вызов из `assignPlayers` и при GET команды); Flyway **V17** — разовая чистка для `series_id = 5`.

**Бесплатные паки:** лимит задаётся на паке (`card_pack.free_opens_per_user`), учёт в `user_card_pack_free_usage`; админка — Card packs; TMA магазин показывает остаток и не требует баланса, пока есть квота.

**Планировщик серий:** `@EnableScheduling` на `FantasyApplication`; `schedule/ActiveSeriesSyncScheduler` — cron `0 0/10 * * * *` (каждые 10 мин), для серий `ACTIVE`/`SCORING` с `finalized = false` — `syncGames` + `calculateScores`; `SeriesRepository.findAllByStatusInAndFinalizedIsFalse`.

**Отображаемое имя (TMA):** колонка `telegram_user.display_name`, `PATCH /api/v1/me` с телом `{"displayName":…}` (null/`""` — сброс); `first_name`/`username` по-прежнему синхронизируются из Telegram initData, кастомный ник не затирается. Лидерборд и публичная команда отдают `displayName` в `UserPublicDto`. Гонка при первом `INSERT` одного `telegram_id`: вставка + INITIAL фантиков в `TelegramUserBootstrapService.insertNewUserWithInitialFantiki` с `REQUIRES_NEW`, при `23505` — догрузка строки и обновление полей Telegram в основной транзакции. Flyway **V14**.

**Неполная команда на серию:** можно выставить 1–3 карты; награда за место при финализации масштабируется ⌈⅓⌉ / ⌈⅔⌉ / 100% от суммы из `economy_config` (см. `SeriesFinalizationService.scaleSeriesRewardByRosterSize`).

**Автофинализация при FINISHED:** при `PUT /admin/series/{id}` (и при создании серии со статусом FINISHED), если после сохранения `status == FINISHED` и `finalized == false`, в том же транзакционном потоке вызывается `SeriesFinalizationService.finalizeSeries` (награды + декремент uses + `finalized = true`). Раньше награды начислялись только по кнопке `POST .../finalize`, поэтому смена статуса на FINISHED без отдельного вызова оставляла серию без фантиков.

**V3 (PLAN-V3) реализована по цепочке C1→C5:** контракты карт (`uses_remaining`, `times_renewed`), `series.finalized`, таблица `economy_config` + сиды; сервисы `EconomyConfigService`, `CardLifecycleService`, `SeriesFinalizationService`; user API recycle/renew/economy-info; admin finalize series + CRUD экономики; админка `/economy` и кнопка финализации серии; TMA — бейджи использований, коллекция (фильтры/сортировка, переработка/продление), **экран «Справка»** `/help` (механика очков, каталог достижений `GET /api/v1/achievements`, блок экономики из `economy-info`), редирект `/economy` → `/help`; сборка команды (истёкшие недоступны, предупреждение «последнее использование»). План: [`PLAN-V3.md`](../PLAN-V3.md). Обновление `DESIGN.md` по V3 — по желанию (см. конец PLAN-V3).

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
- Язык бэкенда: Kotlin; Flyway V1–V14 (`V14__telegram_user_display_name`)
- Скоринг: `(base + Σachievement) × rarity_modifier`, per-game breakdown хранится в БД; **базовые очки** — `GamePointsService.fetchPlayerStats(polemicaGameId)` (публичная страница `/match/{id}`, поле `points` по позиции за столом), не `PolemicaPlayer.award`; в расчёт попадают **только завершённые** игры (`PolemicaGame.result != null`); в `scored = true` помечаются только они
- Имена игр без заголовка из API: при sync — `Игра {num}` / `Игра #{polemicaGameId}`; для API — `formatSeriesGameDisplayName`
- S3: AWS SDK Java v2, MinIO в dev
- Образ backend: GHCR; на VPS — `docker compose -f docker-compose.prod.yml up -d --build`

## Следующие шаги
1. Опционально: серверный агрегат лидерборда по турниру (сейчас TMA суммирует на клиенте)
2. Опционально: доп. методы в polemica-library (оптимизация `getPlayerGames`)
3. Этап 2 баланса достижений: по отчёту этапа 1 и prior ролей 6/2/1/1 задать формулу `bonus_points` (отдельная задача)

## Статистика достижений (этап 1 баланса)
- **POST** `/api/v1/admin/achievement-statistics/collect` (Basic Auth) — для каждого `fantasy_player` первая страница профиля Polemica (100 игр), дедуп `match_id`, `getMatch`, прогон тех же `AchievementDetector` + `isRoleApplicable`, что в скоринге; ответ — агрегаты по каждому `achievement.id` (`applicableSlots`, `sumRawMatchCount`, `slotsWithPositiveRaw`, `sumAppliedOccurrences`), метаданные и до 100 ошибок загрузки матча.
- `PolemicaIntegrationService.fetchProfileGamesFirstPageForStatistics` — page=1, limit=100.

## Открытые вопросы
- Финальная настройка бонусов достижений (через админку)

## Блокеры
- Нет критичных блокеров

## Недавние правки UI (админка)
- **Tournament detail (`/tournaments/:id`):** над таблицей игроков — сетка карточек (`List` + `Card`) с аватаром (фото или первая буква ника), ником и внутренним id; в колонке Photo таблицы — миниатюра `Avatar` + ссылка «open» на полное изображение.
- **Users (`/users`):** список всех `telegram_user` с колонками username, Telegram ID, displayName; фильтры **Tournament** + **Series**; столбец **Cards (series)** — число экземпляров `user_card`, чья `card_template` относится к игроку из ростера серии (как `GET /me/cards?seriesId`). API: **GET** `/api/v1/admin/users` (без query — `cardsInSeries: null`) и с `tournamentId` + `seriesId` (оба обязательны вместе для счётчика). Реализация: `AdminUserListService`, нативный запрос в `TelegramUserRepository.findAllWithCardsInSeriesCount`.
- **Series → Assign players:** у `Select` включён поиск (`showSearch`) и фильтрация опций по подстроке (без учёта регистра), чтобы быстро находить игрока по нику в длинном списке.

## Недавние правки TMA (шапка приложения)
- **`App.tsx` / `index.css`:** шапка `header.top` — две логические строки: **`top__bar`** (бренд + `FantikiBalance` в одну линию), ниже **`nav`**; убран вложенный `flex-wrap`, из‑за которого на узкой колонке баланс уезжал на третью строку под «Магазин».

## Недавние правки TMA (карты и коллекция)
- **Коллекция (`CardsPage`):** фильтр по турниру — `<select>` с названиями из `GET /api/v1/tournaments` (кэш-ключ `['tournaments', initData]`, как на главной); опция «Все карты»; если в URL есть `tournamentId`, которого нет в списке активных турниров, показывается доп. опция «Турнир №{id}».
- **Карты:** общая палитра редкости — common матовый серый (`--pf-card-common`), rare ледяной cyan с лёгким свечением (`--pf-card-rare`); достижения в виде чипов на оверлее фото (`CardAchievementChips`) на коллекции, сборке команды, раскрытии пака и сводке пака; увеличены шрифты в подписи к карте.
- **Покупка пака:** ответ `POST …/store/packs/{id}/buy` мапится в `UserCardItemDto` через шаблоны из `CardTemplateRepository.findAllByIdWithAchievementsLoaded`, чтобы список достижений не терялся при сборке DTO; на экране раскрытия частицы EPIC/LEGENDARY под текстом (`z-index` у `pf-pack-open__card-cap` выше, чем у `pf-pack-open__particles`).

## Недавние правки (экономика TMA)
- **`EconomyConfigService.buildEconomyInfo`:** строки наград за лидерборд серии формируются из БД (`economy_config`: `description` как подпись, `value` как сумма через тот же кэш, что и финализация); порядок ключей `series.reward.*` зафиксирован списком в сервисе (как и в `getSeriesReward`). Интеграционный тест: `UserApiIntegrationTest` — `GET economy-info returns…`.

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
