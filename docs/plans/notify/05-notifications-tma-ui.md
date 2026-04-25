# План 5: TMA UI — экраны настроек уведомлений

> **Предусловия:** План 2 (User API настроек и подписок), План 4 (API marketplace watches)  
> **Результат:** экраны настроек категорий, подписок на турниры, фильтров маркетплейса; кнопка-колокольчик в top bar; контекстная кнопка «Отслеживать» на маркетплейсе  
> **Дизайн-документ:** §16 (TMA UI)

---

## Шаги

### 1. API-клиент: хуки для настроек уведомлений

**Файл:** `src/api/notifications.ts` (новый)  
**Проект:** `polemica-fantasy-webapp`

```typescript
// GET /api/v1/settings/notifications
export function useNotificationSettings() { ... }

// PUT /api/v1/settings/notifications
export function useUpdateNotificationSettings() { ... }

// GET /api/v1/settings/tournament-subscriptions
export function useTournamentSubscriptions() { ... }

// PUT /api/v1/settings/tournament-subscriptions
export function useUpdateTournamentSubscriptions() { ... }

// GET /api/v1/settings/marketplace-watches
export function useMarketplaceWatches() { ... }

// POST /api/v1/settings/marketplace-watches
export function useCreateMarketplaceWatch() { ... }

// DELETE /api/v1/settings/marketplace-watches/:id
export function useDeleteMarketplaceWatch() { ... }
```

Все хуки — через `@tanstack/react-query` (`useQuery` / `useMutation`), как в остальных API-хуках проекта.

### 2. Типы

**Файл:** `src/types/notifications.ts` (новый)

```typescript
interface NotificationCategoryDto {
  category: string
  enabled: boolean
  toggleable: boolean
  description: string
}

interface TournamentSubscriptionEntry {
  tournamentId: number
  tournamentName: string
  subscribed: boolean
}

interface MarketplaceWatchDto {
  id: number
  fantasyPlayer: { id: number; nickname: string } | null
  tournament: { tournamentId: number; tournamentName: string } | null
  rarity: string | null
  maxPrice: number | null
  createdAt: string
}
```

### 3. Кнопка-колокольчик в top bar

**Файл:** `src/App.tsx`

Добавить иконку 🔔 в `<div className="top__bar">`, справа от `<FantikiBalance />`:

```tsx
<div className="top__bar">
  <NavLink to="/" className="top__brand">Polemica Fantasy</NavLink>
  <TopBarDisplayName />
  <div className="top__balance">
    <FantikiBalance />
  </div>
  <NavLink to="/notifications" className="top__notifications" aria-label="Уведомления">
    🔔
  </NavLink>
</div>
```

**Файл:** `src/App.css` (или аналогичный файл стилей)

```css
.top__notifications {
  font-size: 1.2rem;
  text-decoration: none;
  padding: 4px 8px;
  border-radius: 8px;
  transition: background 0.15s;
}
.top__notifications:hover,
.top__notifications.active {
  background: var(--tg-theme-secondary-bg-color, rgba(0,0,0,0.05));
}
```

### 4. Роутинг

**Файл:** `src/App.tsx`

Добавить маршруты в `<Routes>`:

```tsx
<Route path="/notifications" element={<NotificationSettingsPage />} />
<Route path="/notifications/tournaments" element={<TournamentSubscriptionsPage />} />
<Route path="/notifications/marketplace-watches" element={<MarketplaceWatchesPage />} />
```

Импорты:

```tsx
import { NotificationSettingsPage } from './pages/NotificationSettingsPage'
import { TournamentSubscriptionsPage } from './pages/TournamentSubscriptionsPage'
import { MarketplaceWatchesPage } from './pages/MarketplaceWatchesPage'
```

### 5. Страница настроек категорий (`NotificationSettingsPage`)

**Файл:** `src/pages/NotificationSettingsPage.tsx` (новый)

Макет (§16.3):

```
🔔 Уведомления

━━ Турниры и серии ━━━━━━━━━━━━━━━━━━━
[toggle] Старт серии                         ✅
         Подписки на турниры: ...             [>]
[toggle] Напоминание о дедлайне команды      ✅
[toggle] Результаты серии                     ✅
[toggle] Замена карт в составе                ✅

━━ Маркетплейс ━━━━━━━━━━━━━━━━━━━━━━
[toggle] Продажа вашей карты                  ✅
[toggle] Отслеживание карт                    ✅
         Фильтры отслеживания (N)  [>]

━━ Системные ━━━━━━━━━━━━━━━━━━━━━━━━
         Сообщения от администрации            🔒 Всегда вкл
         Уведомления о санкциях                🔒 Всегда вкл
```

Группировка категорий — захардкодить в компоненте:

```typescript
const groups = [
  {
    title: 'Турниры и серии',
    categories: ['SERIES_START', 'TEAM_DEADLINE_REMINDER', 'SERIES_FINALIZED', 'SERIES_ROSTER_CHANGE'],
  },
  {
    title: 'Маркетплейс',
    categories: ['MARKETPLACE_SALE', 'MARKETPLACE_WATCH'],
  },
  {
    title: 'Системные',
    categories: ['ADMIN_BROADCAST', 'PAIR_BAN'],
  },
]
```

Toggleable категории — `<input type="checkbox">` (или Telegram-стилизованный switch). Неотключаемые — текст «Всегда вкл» с замком.

После `SERIES_START` — ссылка на `/notifications/tournaments` с текстом «Подписки на турниры: ...».  
После `MARKETPLACE_WATCH` — ссылка на `/notifications/marketplace-watches` с текстом «Фильтры отслеживания (N)».

При переключении toggle — `PUT /api/v1/settings/notifications` с optimistic update через react-query.

### 6. Страница подписок на турниры (`TournamentSubscriptionsPage`)

**Файл:** `src/pages/TournamentSubscriptionsPage.tsx` (новый)

Макет (§16.4):

```
🏆 Подписки на турниры

Если не выбран ни один — приходят уведомления обо всех турнирах.

[checkbox] Кубок Полемики        ☑️
[checkbox] Тренировочный         ☐
[checkbox] Летний турнир         ☑️
```

Список турниров — из `GET /api/v1/settings/tournament-subscriptions`.
При изменении чекбокса — `PUT /api/v1/settings/tournament-subscriptions` с полным списком `tournamentIds`.

Подсказка сверху: «Если не выбран ни один — приходят уведомления обо всех турнирах.»

### 7. Страница фильтров маркетплейса (`MarketplaceWatchesPage`)

**Файл:** `src/pages/MarketplaceWatchesPage.tsx` (новый)

Макет (§16.5):

```
🔔 Отслеживание карт (N из 10)

┌─────────────────────────────────────────┐
│ Иванов Иван · EPIC · до 200 ₣    [✕]  │
├─────────────────────────────────────────┤
│ Любой · LEGENDARY · любая цена    [✕]  │
└─────────────────────────────────────────┘

[+ Добавить фильтр]
```

Список фильтров — из `GET /api/v1/settings/marketplace-watches`.
Удаление — `DELETE /api/v1/settings/marketplace-watches/{id}` по кнопке `[✕]`.
Кнопка «Добавить фильтр» — открывает форму добавления.

### 8. Форма добавления фильтра

**Файл:** `src/pages/MarketplaceWatchesPage.tsx` (или отдельный компонент)

Макет (§16.5):

```
🔎 Новый фильтр отслеживания

Игрок:    [▾ Выберите (необязательно)  ]
Турнир:   [▾ Выберите (необязательно)  ]
Редкость: [▾ Любая | COMMON | RARE | EPIC | LEGENDARY ]
Макс. цена: [__________] ₣ (необязательно)

                          [Сохранить]
```

Поле «Игрок» — поиск/autocomplete по имени (GET API для поиска fantasy players или из существующего API).  
Поле «Турнир» — select из списка турниров.  
Поле «Редкость» — select из enum.  
Поле «Макс. цена» — числовой input.

Валидация на клиенте: хотя бы одно поле (кроме maxPrice) заполнено.

`POST /api/v1/settings/marketplace-watches` при сабмите.

### 9. Контекстная кнопка «Отслеживать фильтр» на маркетплейсе

**Файл:** `src/pages/MarketplacePage.tsx`

Когда пользователь задал фильтры поиска (fantasyPlayerId, tournamentId, rarity, maxPrice) — показать кнопку:

```tsx
<button onClick={handleCreateWatch}>
  🔔 Отслеживать этот фильтр
</button>
```

Логика:
1. Кнопка видна только если хотя бы один фильтр (кроме maxPrice) задан.
2. При нажатии — `POST /api/v1/settings/marketplace-watches` с текущими параметрами поиска.
3. При успехе — кнопка меняется на «✓ Отслеживается» (disabled) или показывает toast.
4. При ошибке `409 Conflict` (дубликат) — текст «Уже отслеживается».
5. При ошибке `400` (лимит 10) — текст «Достигнут лимит фильтров».

### 10. Стилизация

Все экраны стилизовать в общем стиле приложения:
- Использовать CSS-переменные Telegram (`--tg-theme-*`)
- Toggle/switch — по аналогии с существующими интерактивными элементами
- Списки — карточки с разделителями
- Кнопки — accent-цвет из Telegram theme

---

## Проверка готовности

- [ ] Колокольчик 🔔 отображается в top bar, ведёт на `/notifications`
- [ ] Экран настроек: все 8 категорий сгруппированы, toggleable переключаются, non-toggleable показывают «Всегда вкл»
- [ ] Переключение toggle — PUT запрос, optimistic update, данные обновляются
- [ ] Ссылка «Подписки на турниры» ведёт на `/notifications/tournaments`
- [ ] Экран турниров: чекбоксы, сохранение подписок через PUT
- [ ] Пустой выбор → подсказка «уведомления обо всех турнирах»
- [ ] Ссылка «Фильтры отслеживания (N)» ведёт на `/notifications/marketplace-watches`
- [ ] Экран фильтров: список, удаление, счётчик «N из 10»
- [ ] Форма добавления: валидация, POST, обновление списка
- [ ] Контекстная кнопка на маркетплейсе: создаёт фильтр из текущих параметров поиска
- [ ] Дубликат фильтра → «Уже отслеживается»
- [ ] Лимит 10 → «Достигнут лимит фильтров»
