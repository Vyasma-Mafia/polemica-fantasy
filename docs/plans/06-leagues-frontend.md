# План 6: Лиги (frontend)

> **Предусловия:** План 5 (API лиг доступен), План 2 (ценность на карточках уже отображается)  
> **Результат:** TMA показывает вкладки лиг, сборку команды с бюджетным кэпом, per-league лидерборд, обновлённую главную  
> **Дизайн-документ:** §8.3 (Лиги в серии), §8.4 (Справка), §8.5 (Главная), §8.6 (Турнирный лидерборд), §7.5

---

## Шаги

### 1. Типы

**Файл:** `polemica-fantasy-webapp/src/api/types.ts`

```typescript
interface SeriesLeagueInfo {
  code: string;
  name: string;
  description: string | null;
  valueCap: number | null;
  maxLegendaryCount: number | null;
  minTeamSize: number;
  maxTeamSize: number;
  rewardScale: number;
  hasTeam: boolean;
}

// Обновить UserCardItem:
interface UserCardItem {
  // ... существующие поля ...
  value: number;
  leaguesInSeries?: string[];      // nullable
  canJoinMoreLeagues?: boolean;     // nullable
}

// Обновить FantasyTeamDto:
interface FantasyTeamDto {
  // ... существующие поля ...
  leagueCode: string;
}

// Обновить UserSeriesDetail:
interface UserSeriesDetail {
  // ... существующие поля ...
  leagues: SeriesLeagueBrief[];
}

interface SeriesLeagueBrief {
  code: string;
  name: string;
  hasTeam: boolean;
  valueCap: number | null;
}

// Обновить EconomyInfo:
interface EconomyInfo {
  // ... существующие поля ...
  leagues: Record<string, { valueCap: number | null; rewardScale: number }>;
}
```

### 2. API-клиент: лиги

**Файл:** `polemica-fantasy-webapp/src/api/leagues.ts` (новый)

```typescript
export async function fetchSeriesLeagues(seriesId: number, initData: string): Promise<SeriesLeagueInfo[]>
export async function fetchLeagueLeaderboard(seriesId: number, leagueCode: string, initData: string): Promise<LeaderboardEntry[]>
export async function submitLeagueTeam(seriesId: number, leagueCode: string, userCardIds: number[], initData: string): Promise<FantasyTeamDto>
export async function updateLeagueTeam(seriesId: number, leagueCode: string, userCardIds: number[], initData: string): Promise<FantasyTeamDto>
```

### 3. Вкладки лиг на странице серии

**Файл:** `polemica-fantasy-webapp/src/pages/SeriesPage.tsx`

На странице серии — вкладки: «Основная» / «Бюджетная»:

```
[Основная ✓]  [Бюджетная ✗]
```

Каждая вкладка показывает:
- Лидерборд этой лиги
- Кнопка «Собрать команду» → переход на TeamPage с `leagueCode`
- Для бюджетной: подпись «Макс. ценность: 175₱»

Переключение вкладок — `useState` или URL-параметр `?league=BUDGET`.

### 4. Сборка команды с бюджетом

**Файл:** `polemica-fantasy-webapp/src/pages/TeamPage.tsx`

Основные изменения при `leagueCode=BUDGET`:

#### 4.1 Прогресс-бар бюджета

```
Бюджет: 100 / 175₱  ████████░░░░░
```

- Заполняется по мере добавления карт
- Зелёный при < 100%, жёлтый при 80–100%, красный при переполнении
- Показывает `Σ card.value` выбранных карт / `valueCap`

#### 4.2 Фильтрация карт

Карты, которые **не влезают** в оставшийся бюджет, визуально заглушены (opacity, серая рамка):
- `card.value > remainingBudget` → disabled
- Подсказка при тапе: «Эта карта стоит 100₱, осталось 75₱ бюджета»

#### 4.3 Аннотации per-league

Используя `card.leaguesInSeries` и `card.canJoinMoreLeagues` (из обновлённого `GET /me/cards?seriesId=...`):
- Карты, уже в основной лиге: бейдж «Основная»
- Карты, у которых не хватает uses для ещё одной лиги (`canJoinMoreLeagues = false`): disabled

#### 4.4 URL и маршрутизация

Текущий маршрут: `/series/:seriesId/team`  
С лигами: `/series/:seriesId/team?league=BUDGET` (или `/series/:seriesId/leagues/:leagueCode/team`)

**Рекомендация:** query-параметр `?league=`, по умолчанию `MAIN`.

### 5. Лидерборд per-league

**Файл:** `polemica-fantasy-webapp/src/pages/LeaderboardPage.tsx`

Обновить для поддержки лиг:
- Получение `leagueCode` из URL (query-параметр или сегмент)
- Запрос `fetchLeagueLeaderboard(seriesId, leagueCode, initData)`
- Если `leagueCode` не указан → `MAIN` (обратная совместимость)
- Вкладки лиг наверху лидерборда (аналогично SeriesPage)

### 6. Турнирный лидерборд — только MAIN

**Файл:** `polemica-fantasy-webapp/src/pages/TournamentLeaderboardPage.tsx`

Турнирный лидерборд (§8.6) строится **только по основной лиге**. Текущая логика запрашивает `GET /series/{id}/leaderboard` для каждой серии → по умолчанию возвращается MAIN → **изменений не нужно**. Убедиться, что не передаётся `leagueCode`.

### 7. Обновить «Состав на серию» на главной

**Файл:** `polemica-fantasy-webapp/src/pages/HomePage.tsx`

Текущая логика: карточка серии → «Собрать команду».

С лигами (§8.5):
- На карточке серии — per-league статус: «Основная ✓ / Бюджетная ✗»
- Серия показывается, пока хотя бы одна лига без команды
- Тап на карточку → переход на `/series/:id/team` (при наличии нескольких лиг без команды — на SeriesPage с вкладками)

Данные: `SeriesOpenForTeamDto` расширяется бэкендом (или запросить `GET /series/{id}/leagues` отдельно).

### 8. Обновить карточку серии в турнире

**Файл:** `polemica-fantasy-webapp/src/pages/TournamentPage.tsx`

В списке серий турнира — добавить per-league чекмарки (как на главной). Используя `UserSeriesSummaryDto.leagues`.

### 9. Обновить справку

**Файл:** `polemica-fantasy-webapp/src/pages/HelpPage.tsx`

Секция «Лиги»:
- Основная лига: без ограничений, полные награды
- Бюджетная лига: макс. ценность команды = 175₱, награды 50% от основной
- Одна карта может быть в нескольких лигах (1 use за лигу)
- Каждая лига — свой лидерборд и свои награды
- Награды суммируются

### 10. Компонент: `LeagueTabs`

**Файл:** `polemica-fantasy-webapp/src/components/LeagueTabs.tsx` (новый)

Переиспользуемый компонент вкладок лиг (используется на SeriesPage, LeaderboardPage):

```tsx
interface LeagueTabsProps {
  leagues: SeriesLeagueBrief[];
  activeCode: string;
  onChange: (code: string) => void;
}
```

Визуал:
- Горизонтальные вкладки
- Активная вкладка — подчёркнута / выделена фоном
- Чекмарк ✓ рядом с названием лиги, если `hasTeam = true`
- Для бюджетной — маленький бейдж с кэпом `175₱`

### 11. Компонент: `BudgetProgressBar`

**Файл:** `polemica-fantasy-webapp/src/components/BudgetProgressBar.tsx` (новый)

```tsx
interface BudgetProgressBarProps {
  currentValue: number;
  maxValue: number;
}
```

Используется на TeamPage при `leagueCode=BUDGET`.

### 12. CSS-стили

**Файл:** `polemica-fantasy-webapp/src/index.css`

```css
.pf-league-tabs { /* контейнер вкладок */ }
.pf-league-tab { /* вкладка */ }
.pf-league-tab--active { /* активная */ }
.pf-league-tab__check { /* чекмарк */ }
.pf-budget-bar { /* прогресс-бар */ }
.pf-budget-bar__fill { /* заполнение */ }
.pf-budget-bar__fill--warning { /* 80-100% */ }
.pf-budget-bar__fill--over { /* > 100% */ }
.pf-card--budget-disabled { /* карта не влезает в бюджет */ }
.pf-card-league-badge { /* бейдж «Основная» на карте */ }
```

---

## Проверка готовности

- [ ] На странице серии видны вкладки MAIN / BUDGET
- [ ] При сборке бюджетной команды — прогресс-бар и фильтрация карт по бюджету
- [ ] Карты без свободных uses для доп. лиги — заглушены
- [ ] Лидерборд per-league — работает для обеих лиг
- [ ] Турнирный лидерборд — по-прежнему только MAIN
- [ ] Главная: per-league статус (✓/✗) на карточке серии
- [ ] В справке — описание лиг
- [ ] Обратная совместимость: прямые ссылки без `?league=` работают (MAIN по умолчанию)
- [ ] UI корректен на мобильном экране (320–430px)
