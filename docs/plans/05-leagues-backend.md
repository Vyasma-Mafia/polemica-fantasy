# План 5: Лиги (backend)

> **Предусловия:** План 1 (CardValueService), План 3 (economy_config ключи)  
> **Результат:** модель лиг, API лиг, валидация команд по правилам лиги, финализация per-league  
> **Дизайн-документ:** §4 (Система лиг), §5 (Взаимодействие), §6 (Модель данных), §7 (API), §10 (Миграция)

---

## Шаги

### 1. Flyway-миграция: таблицы + данные

**Файл:** `V34__leagues.sql`  
**Путь:** `src/main/resources/db/migration/`

#### 1.1 economy_config: ключи лиг

```sql
INSERT INTO economy_config (key, value, description) VALUES
    ('league.reward_scale.MAIN',   '100', 'Масштаб наград основной лиги (%)'),
    ('league.reward_scale.BUDGET', '50',  'Масштаб наград бюджетной лиги (%)'),
    ('league.budget.value_cap',    '175', 'Максимальная суммарная ценность команды в бюджетной лиге');
```

#### 1.2 Таблица `league`

```sql
CREATE TABLE league (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(32) NOT NULL UNIQUE,
    name        VARCHAR(128) NOT NULL,
    description TEXT,
    league_type VARCHAR(32) NOT NULL,
    value_cap           BIGINT,
    max_legendary_count INT,
    min_team_size       INT NOT NULL DEFAULT 1,
    max_team_size       INT NOT NULL DEFAULT 3,
    created_by_telegram_user_id BIGINT REFERENCES telegram_user(id),
    visibility  VARCHAR(32) DEFAULT 'PUBLIC',
    entry_fee   BIGINT DEFAULT 0,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

INSERT INTO league (code, name, description, league_type)
VALUES
    ('MAIN',   'Основная лига',    'Без ограничений по ценности и редкости',  'SYSTEM'),
    ('BUDGET', 'Бюджетная лига',   'Суммарная ценность команды ограничена',   'SYSTEM');
```

#### 1.3 Таблица `series_league`

```sql
CREATE TABLE series_league (
    id          BIGSERIAL PRIMARY KEY,
    series_id   BIGINT NOT NULL REFERENCES series(id),
    league_id   BIGINT NOT NULL REFERENCES league(id),
    value_cap_override    BIGINT,
    reward_scale_override INT,
    enabled     BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE (series_id, league_id)
);
```

#### 1.4 Бутстрап series_league для существующих серий

```sql
INSERT INTO series_league (series_id, league_id)
SELECT s.id, l.id
FROM series s
CROSS JOIN league l
WHERE l.league_type = 'SYSTEM';
```

#### 1.5 Миграция `fantasy_team`

```sql
ALTER TABLE fantasy_team
    ADD COLUMN series_league_id BIGINT REFERENCES series_league(id);

-- Все существующие команды → основная лига
UPDATE fantasy_team ft
SET series_league_id = sl.id
FROM series_league sl
JOIN league l ON l.id = sl.league_id
WHERE sl.series_id = ft.series_id AND l.code = 'MAIN';

ALTER TABLE fantasy_team ALTER COLUMN series_league_id SET NOT NULL;

-- Обновить UNIQUE constraint
ALTER TABLE fantasy_team DROP CONSTRAINT fantasy_team_telegram_user_id_series_id_key;
ALTER TABLE fantasy_team ADD CONSTRAINT fantasy_team_user_series_league_key
    UNIQUE (telegram_user_id, series_id, series_league_id);
```

#### 1.6 Убрать legacy-ограничение

```sql
-- Пометить deprecated (не удалять, чтобы не ломать кэш EconomyConfigService)
UPDATE economy_config
SET description = '[DEPRECATED — replaced by league.max_legendary_count] ' || COALESCE(description, '')
WHERE key = 'legendary.team.max_per_series';
```

### 2. Entity: `League`

**Файл:** `entity/League.kt` (новый)

```kotlin
@Entity
@Table(name = "league")
class League(
    @Id @GeneratedValue(strategy = IDENTITY) var id: Long? = null,
    @Column(unique = true) var code: String,
    var name: String,
    var description: String?,
    @Enumerated(STRING) var leagueType: LeagueType,
    var valueCap: Long?,
    var maxLegendaryCount: Int?,
    var minTeamSize: Int,
    var maxTeamSize: Int,
    // V2+ fields
    @ManyToOne var createdByTelegramUser: TelegramUser? = null,
    var visibility: String? = "PUBLIC",
    var entryFee: Long? = 0,
    var createdAt: Instant = Instant.now(),
)

enum class LeagueType { SYSTEM, USER }
```

### 3. Entity: `SeriesLeague`

**Файл:** `entity/SeriesLeague.kt` (новый)

```kotlin
@Entity
@Table(name = "series_league", uniqueConstraints = [UniqueConstraint(columnNames = ["series_id", "league_id"])])
class SeriesLeague(
    @Id @GeneratedValue(strategy = IDENTITY) var id: Long? = null,
    @ManyToOne var series: Series,
    @ManyToOne var league: League,
    var valueCapOverride: Long? = null,
    var rewardScaleOverride: Int? = null,
    var enabled: Boolean = true,
)
```

### 4. Entity: обновить `FantasyTeam`

**Файл:** `entity/FantasyTeam.kt`

Добавить:

```kotlin
@ManyToOne(fetch = LAZY)
@JoinColumn(name = "series_league_id", nullable = false)
var seriesLeague: SeriesLeague? = null,
```

Обновить `@Table(uniqueConstraints = ...)` на `(telegram_user_id, series_id, series_league_id)`.

### 5. Repository: `LeagueRepository`, `SeriesLeagueRepository`

**Файлы:** `repository/LeagueRepository.kt`, `repository/SeriesLeagueRepository.kt` (новые)

```kotlin
interface LeagueRepository : JpaRepository<League, Long> {
    fun findByCode(code: String): League?
    fun findAllByLeagueType(type: LeagueType): List<League>
}

interface SeriesLeagueRepository : JpaRepository<SeriesLeague, Long> {
    fun findAllBySeries_IdAndEnabledTrue(seriesId: Long): List<SeriesLeague>
    fun findBySeries_IdAndLeague_Code(seriesId: Long, leagueCode: String): SeriesLeague?
    fun findBySeries_IdAndLeague_Id(seriesId: Long, leagueId: Long): SeriesLeague?
}
```

### 6. Обновить `FantasyTeamRepository`

**Файл:** `repository/FantasyTeamRepository.kt`

Обновить запросы:
- `findByTelegramUser_IdAndSeries_Id` → добавить вариант `findByTelegramUser_IdAndSeriesLeague_Id`
- `findLeaderboardForSeries` → добавить `findLeaderboardForSeriesLeague(seriesLeagueId)`
- `findAllWithCardsForScoring` → с join на `series_league`

### 7. `LeagueService` — правила лиги

**Файл:** `service/LeagueService.kt` (новый)

```kotlin
@Service
class LeagueService(
    private val leagueRepository: LeagueRepository,
    private val seriesLeagueRepository: SeriesLeagueRepository,
    private val economyConfigService: EconomyConfigService,
) {
    fun getEffectiveValueCap(seriesLeague: SeriesLeague): Long? {
        seriesLeague.valueCapOverride?.let { return it }
        val league = seriesLeague.league
        if (league.code == "BUDGET") {
            return economyConfigService.getLong("league.budget.value_cap")
        }
        return league.valueCap
    }

    fun getEffectiveRewardScale(seriesLeague: SeriesLeague): Int {
        seriesLeague.rewardScaleOverride?.let { return it }
        return economyConfigService.getInt("league.reward_scale.${seriesLeague.league.code}")
    }

    fun getEffectiveMaxLegendary(seriesLeague: SeriesLeague): Int? {
        return seriesLeague.league.maxLegendaryCount
    }
}
```

### 8. Валидация команды по правилам лиги

**Файл:** `service/UserFantasyTeamService.kt`

В `attachCards` заменить текущую валидацию `getLegendaryTeamMaxPerSeries` → валидацией по правилам лиги:

1. **value_cap**: `Σ card_value ≤ effectiveValueCap` (если не null)
2. **max_legendary_count**: `legendaryCount ≤ effectiveMaxLegendary` (если не null; null = без ограничения)
3. **team_size**: `cards.size in minTeamSize..maxTeamSize`
4. **uses across leagues** (§4.3): подсчитать, в скольких лигах **этой серии** карта уже стоит; `existing_league_count + 1 > uses_remaining` → отклонить

Для проверки uses — новый запрос в `FantasyTeamCardRepository`:

```kotlin
@Query("""
    SELECT COUNT(DISTINCT ft.seriesLeague.id)
    FROM FantasyTeamCard ftc
    JOIN ftc.fantasyTeam ft
    WHERE ftc.userCard.id = :userCardId
      AND ft.series.id = :seriesId
      AND ft.seriesLeague.id != :excludeSeriesLeagueId
""")
fun countLeaguesInSeriesForCard(userCardId: Long, seriesId: Long, excludeSeriesLeagueId: Long): Int
```

### 9. Автоматическое создание `series_league` при создании серии

**Файл:** `service/SeriesService.kt` (или `SeriesAdminService`)

При создании серии — автоматически создать `series_league` для всех SYSTEM-лиг:

```kotlin
val systemLeagues = leagueRepository.findAllByLeagueType(LeagueType.SYSTEM)
for (league in systemLeagues) {
    seriesLeagueRepository.save(SeriesLeague(series = series, league = league))
}
```

### 10. Обратная совместимость: старые endpoint'ы → MAIN лига

**Файлы:** `controller/user/SeriesController.kt`, `service/UserFantasyTeamService.kt`

Старые endpoint'ы (`POST /series/{id}/fantasy-team`, `GET /series/{id}/leaderboard` и т.д.) продолжают работать, неявно используя лигу `MAIN`:

```kotlin
val seriesLeague = seriesLeagueRepository.findBySeries_IdAndLeague_Code(seriesId, "MAIN")
    ?: throw ResponseStatusException(NOT_FOUND, "Main league not found for series")
```

### 11. Новые User API endpoint'ы

**Файл:** `controller/user/LeagueController.kt` (новый)

| Method | Path | Описание |
|--------|------|----------|
| GET | `/api/v1/series/{id}/leagues` | Доступные лиги серии (правила, наличие команды у текущего юзера) |
| GET | `/api/v1/series/{id}/leagues/{leagueCode}/leaderboard` | Лидерборд лиги |
| POST | `/api/v1/series/{id}/leagues/{leagueCode}/fantasy-team` | Создать команду в лиге |
| PUT | `/api/v1/series/{id}/leagues/{leagueCode}/fantasy-team` | Обновить команду в лиге |
| GET | `/api/v1/series/{id}/leagues/{leagueCode}/users/{tgId}/fantasy-team` | Чужая команда в лиге |

### 12. DTO лиг

**Файл:** `dto/user/response/LeagueDtos.kt` (новый)

```kotlin
data class SeriesLeagueDto(
    val code: String,
    val name: String,
    val description: String?,
    val valueCap: Long?,
    val maxLegendaryCount: Int?,
    val minTeamSize: Int,
    val maxTeamSize: Int,
    val rewardScale: Int,
    val hasTeam: Boolean,
)
```

### 13. Обновить `UserSeriesDetailDto`

**Файл:** `dto/user/response/UserResponses.kt`

Добавить в `UserSeriesDetailDto` (или `UserSeriesSummaryDto`):

```kotlin
val leagues: List<SeriesLeagueBriefDto>,
```

Где `SeriesLeagueBriefDto`:

```kotlin
data class SeriesLeagueBriefDto(
    val code: String,
    val name: String,
    val hasTeam: Boolean,
    val valueCap: Long?,
)
```

### 14. Обновить `UserCardItemDto` — аннотации per-league

**Файл:** `dto/user/response/UserResponses.kt`

При запросе `/me/cards?seriesId=...` добавить:

```kotlin
val leaguesInSeries: List<String>?,  // nullable — без seriesId = null
val canJoinMoreLeagues: Boolean?,
```

Логика: `leaguesInSeries` — коды лиг, в которых карта стоит в команде; `canJoinMoreLeagues` — `usesRemaining > leaguesInSeries.size`.

### 15. Обновить `FantasyTeamDto`

**Файл:** `dto/user/response/UserResponses.kt`

Добавить `leagueCode: String` в `FantasyTeamDto`, чтобы клиент различал команды разных лиг.

### 16. Обновить `EconomyInfoDto`

**Файл:** `dto/user/response/EconomyLifecycleDtos.kt`

Добавить информацию о лигах:

```kotlin
data class LeagueEconomyInfoDto(
    val valueCap: Long?,
    val rewardScale: Int,
)

// в EconomyInfoDto:
val leagues: Map<String, LeagueEconomyInfoDto>,
```

### 17. Admin API: управление лигами

**Файл:** `controller/admin/LeagueAdminController.kt` (новый)

| Method | Path | Описание |
|--------|------|----------|
| GET | `/api/v1/admin/leagues` | Все лиги |
| PUT | `/api/v1/admin/leagues/{id}` | Обновить правила лиги |
| POST | `/api/v1/admin/series/{id}/leagues` | Добавить/обновить лигу серии (override) |
| DELETE | `/api/v1/admin/series/{id}/leagues/{leagueId}` | Деактивировать (`enabled = false`), блок если есть команды |

### 18. Обновить `SeriesFinalizationService`

**Файл:** `service/SeriesFinalizationService.kt`

Ключевые изменения (§5.7):

1. Для каждой `series_league`:
   - Построить лидерборд по `total_score`
   - `reward = baseReward × rewardScale / 100`, далее `scaleSeriesRewardByRosterSize`
   - Начислить награды
   - Запомнить `Map<telegramId, LeagueResult>`

2. Для каждой `user_card`: `uses_remaining -= COUNT(DISTINCT series_league_id)`

3. Модель уведомлений — per-league `LeagueResult`:

```kotlin
data class LeagueResult(
    val leagueName: String,
    val winnerPublicName: String?,
    val place: Int,
    val total: Int,
    val reward: Long,
)
```

### 19. Обновить уведомления

**Файлы:**
- `event/SeriesFinalizedNotificationEvent.kt` — новая модель `SeriesFinalizedRecipient` с `leagueResults: List<LeagueResult>`, `totalReward`, `balanceAfter`
- `event/SeriesFinalizationTelegramMessage.kt` — `buildSeriesFinalizedTelegramMessage` по §5.8 (одна лига → компактный формат; несколько → per-league блоки)

### 20. Обновить блокировки: marketplace, recycle, legendary upgrade

**Файлы:** `service/MarketplaceService.kt`, `service/CardLifecycleService.kt`, `service/LegendaryUpgradeService.kt`

Текущая проверка `FantasyTeamCardRepository.countInNonFinalizedSeries(userCardId)` уже покрывает все лиги (FantasyTeamCard → FantasyTeam), т.к. одна карта может быть в нескольких FantasyTeam разных лиг. **Проверить**, что запрос корректно считает — если да, изменения не нужны.

### 21. Обновить `FantasyTeamRosterPruningService`

**Файл:** `service/FantasyTeamRosterPruningService.kt`

Pruning применяется к командам **всех лиг** серии (§5.5). Текущая логика работает по `fantasy_team` → автоматически покрывает все лиги.

### 22. Обновить `GET /me/fantasy-teams`

**Файл:** `service/UserFantasyTeamService.kt`

`listAllTeams` возвращает команды всех лиг с `leagueCode`. `getTeamForSeries` принимает опциональный `leagueCode` (по умолчанию `MAIN`).

---

## Проверка готовности

- [ ] Миграция V34 применяется, seed-данные корректны
- [ ] Все существующие команды мигрированы в MAIN лигу
- [ ] Старые API (`POST /series/{id}/fantasy-team`, `GET /series/{id}/leaderboard`) работают как раньше
- [ ] `GET /series/{id}/leagues` возвращает MAIN и BUDGET с правилами
- [ ] Создание команды в BUDGET с Σ value > cap → 400
- [ ] Одна карта в двух лигах → `uses_remaining` достаточно, иначе 400
- [ ] Финализация: rewards per-league, uses -= число лиг
- [ ] Уведомления — per-league формат
- [ ] Admin: override cap/rewards для серии, деактивация лиги
- [ ] Карта в команде нефинализированной лиги → маркетплейс/recycle/upgrade заблокированы
