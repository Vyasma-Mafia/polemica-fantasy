# Active Context

## Текущий фокус
**V2 полностью реализована.** Все 6 концептуальных изменений внедрены (агенты B1–B7). `DESIGN.md` обновлён под актуальное состояние кода. Спецификация: [`CHANGES-V2.md`](../CHANGES-V2.md); план: [`PLAN-V2.md`](../PLAN-V2.md).

**Деплой:** TMA `https://fantasy.maftourbot.ru`, админка `https://admin.fantasy.maftourbot.ru`, бэкенд в Docker (`docker-compose.prod.yml`). На сервере **`~/polemica-fantasy`** — **git clone** ветки **`master`** (`git@github.com:Vyasma-Mafia/polemica-fantasy.git`, SSH).

## Реализованные изменения V2
1. **Фантики** — внутриигровая валюта (стартовый баланс 1000, отображение в TMA, начисление через админку, `fantiki_transaction` для аудита)
2. **Достижения** — справочник `achievement` в БД (бонус, occurrence_type, applicable_roles, can_appear_on_random_cards); `AchievementType` enum удалён; `CardTemplateAchievement.bonus_points` nullable (override системного)
3. **Автогенерация паков** — `card_pack.auto_generated`, пул игроков (`card_pack_player` / all tournament), генерация при открытии (Rare +1 ачивка, Epic +2, Legendary не участвует), переиспользование CardTemplate при совпадении
4. **Магазин паков** — `GET /store/packs`, `POST /store/packs/{id}/buy`, анимация открытия (PackOpening component)
5. **Модификатор редкости** — `Rarity.scoreModifier` (COMMON 1.0, RARE 1.1, EPIC 1.15, LEGENDARY 1.25)
6. **Per-game детализация** — `fantasy_team_card_game_score`, `fantasy_team_card_game_achievement`, endpoint `GET /me/fantasy-teams/{seriesId}/details`

## Текущие решения
- Карточки привязаны к `fantasy_player` (глобальный), не к турниру
- Achievement — справочник (не enum); бонус: `CardTemplateAchievement.bonusPoints ?? Achievement.bonusPoints`
- Паки: `probability` убрана, только `cards_count`; auto-gen: `applicableRoles` не фильтруется при генерации (только при скоринге)
- Язык бэкенда: Kotlin; Flyway V1–V9
- Скоринг: `(base + Σachievement) × rarity_modifier`, per-game breakdown хранится в БД; **базовые очки** — `GamePointsService.fetchPlayerStats(polemicaGameId)` (публичная страница `/match/{id}`, поле `points` по позиции за столом), не `PolemicaPlayer.award`
- S3: AWS SDK Java v2, MinIO в dev
- Образ backend: GHCR; на VPS — `docker compose -f docker-compose.prod.yml up -d --build`

## Следующие шаги
1. Опционально: серверный агрегат лидерборда по турниру (сейчас TMA суммирует на клиенте)
2. Опционально: доп. методы в polemica-library (оптимизация `getPlayerGames`)
3. Уточнить конкретные бонусы, occurrence_type и applicable_roles для каждого достижения (сейчас всё = 1, ONCE_PER_GAME, все роли)

## Открытые вопросы
- Финальная настройка бонусов достижений (через админку)

## Блокеры
- Нет критичных блокеров

## Недавние правки интеграции
- **polemica-library 1.8.2:** исправлен sync games — ответ `get-games` иногда отдаёт `mmr` объектом; в DTO добавлен `ProfileGameMmrDeserializer`. Сборка бэкенда: `../polemica-library` → `./gradlew publishToMavenLocal`, затем fantasy-backend подтянет 1.8.2 из `mavenLocal()` до публикации в Central.
