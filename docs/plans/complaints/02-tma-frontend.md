# План 2: TMA Frontend — страница сделки, жалобы, санкции в ленте (C9–C10)

> **Предусловия:** План 1 (backend C1–C8)  
> **Результат:** страница сделки `/marketplace/transactions/:id`, кнопка «Пожаловаться», пометки санкций в ленте и профиле, анонимизация каталога листингов (скрыт продавец и ценность)  
> **Дизайн-документ:** [`DESIGN-MARKETPLACE-COMPLAINTS.md`](../../features/DESIGN-MARKETPLACE-COMPLAINTS.md) — §3 (страница сделки), §15 (TMA UI)

---

## Шаги

### 1. TypeScript-типы

**Файл:** `src/api/types.ts` (расширить)

```typescript
// === Transaction Detail ===

export interface MarketplaceTransactionDetail {
  listingId: number;
  price: number;
  soldAt: string;
  commission: number;
  sellerReceived: number;
  seller: TransactionParticipant;
  buyer: TransactionParticipant;
  card: TransactionCard;
  complaint: TransactionComplaintInfo;
  sanction: TransactionSanctionInfo | null;
}

export interface TransactionParticipant {
  telegramId: number;
  displayName: string;
}

export interface TransactionCard {
  fantasyPlayerId: number;
  playerName: string;
  playerPhotoUrl: string | null;
  rarity: Rarity;
  achievements: CardAchievement[];
}

export interface TransactionComplaintInfo {
  totalComplaints: number;
  userAlreadyComplained: boolean;
}

export interface TransactionSanctionInfo {
  sanctionedAt: string;
  reason: string;
}

export interface ComplainResult {
  listingId: number;
  totalComplaints: number;
  remainingToday: number;
}
```

Обновить существующие типы:

```typescript
// В MarketplaceFeedItem — добавить поля
export interface MarketplaceFeedItem {
  // ... existing fields ...
  listingId: number;
  sanctioned: boolean;
}

// В PlayerMarketplaceTrade — добавить поля
export interface PlayerMarketplaceTrade {
  // ... existing fields ...
  listingId: number;
  sanctioned: boolean;
}

// В MarketplaceListingEntry — seller теперь nullable
export interface MarketplaceListingEntry {
  // ... existing fields ...
  seller: MarketplaceSellerBrief | null;  // null в каталоге
  card: MarketplaceListingCard;           // card.value может быть null
}
```

---

### 2. API-клиент

**Файл:** `src/api/marketplace.ts` (расширить)

```typescript
export async function fetchTransactionDetail(
  initData: string,
  listingId: number,
): Promise<MarketplaceTransactionDetail> {
  return apiGet(`/api/v1/marketplace/transactions/${listingId}`, initData);
}

export async function complainTransaction(
  initData: string,
  listingId: number,
): Promise<ComplainResult> {
  return apiPost(`/api/v1/marketplace/transactions/${listingId}/complain`, initData);
}
```

React Query хуки:

```typescript
export function useTransactionDetail(listingId: number) {
  const initData = useRawInitData();
  return useQuery({
    queryKey: ['marketplace', 'transaction', listingId, initData],
    queryFn: () => fetchTransactionDetail(initData, listingId),
  });
}

export function useComplainTransaction(listingId: number) {
  const initData = useRawInitData();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => complainTransaction(initData, listingId),
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: ['marketplace', 'transaction', listingId],
      });
    },
  });
}
```

---

### 3. Маршрут

**Файл:** `src/App.tsx`

Добавить route:

```typescript
<Route path="/marketplace/transactions/:listingId" element={<TransactionDetailPage />} />
```

---

### 4. Страница сделки `TransactionDetailPage` (C9)

**Файл:** `src/pages/TransactionDetailPage.tsx` (новый)

Контент страницы (`§15.1`):

1. **Карта:** фото игрока, рамка по редкости (`pf-collection-card__frame--{rarity}`), список достижений (чипы `CardAchievementChips`)
2. **Информация о сделке:**
   - Цена, комиссия, получено продавцом
   - Дата сделки (формат `formatDateShortWithTime`)
3. **Участники:**
   - Продавец → ссылка на `/players/:telegramId`
   - Покупатель → ссылка на `/players/:telegramId`
4. **Блок жалоб:**
   - Счётчик «N жалоб»
   - Кнопка **«Пожаловаться»**:
     - Disabled, если `complaint.userAlreadyComplained === true`
     - Disabled, если текущий пользователь — участник сделки (проверка `seller.telegramId` / `buyer.telegramId` vs текущий `telegramId` из initData)
     - Disabled, если `sanction !== null`
     - При нажатии → `useComplainTransaction` → обновить данные
     - После успеха: toast «Жалоба принята. Осталось N жалоб сегодня»
     - Обработка ошибок: 429 → «Лимит жалоб исчерпан», 409 → «Вы уже пожаловались»
5. **Блок санкции** (если `sanction !== null`):
   - Красный бейдж / блок:
     ```
     ⚠️ Сделка признана нерыночной
     Причина: {sanction.reason}
     Дата: {formatDate(sanction.sanctionedAt)}
     ```

Заголовок страницы: `PageHeader` с текстом «Сделка» или именем игрока.

Навигация назад: на маркетплейс или профиль (по `location.state` или fallback).

---

### 5. Кликабельная лента сделок (C9)

**Файл:** `src/pages/MarketplacePage.tsx`

В секции «Последние покупки» (feed): каждый элемент ленты оборачивается в `<Link>` или `onClick` → навигация на `/marketplace/transactions/${item.listingId}`.

Для санкционированных сделок (`item.sanctioned === true`):

- Добавить CSS-класс `pf-marketplace-feed__item--sanctioned`
- Перечёркнутая цена (CSS `text-decoration: line-through`)
- Красный бейдж «Нерыночная» рядом с ценой

---

### 6. Пометки санкций в профиле игрока (C9)

**Файл:** `src/pages/PlayerProfilePage.tsx` (или компонент, отвечающий за «Последние сделки»)

Аналогично ленте:

- Сделки в блоке «Последние сделки» кликабельны → `/marketplace/transactions/${trade.listingId}`
- Санкционированные сделки (`trade.sanctioned === true`) помечены бейджем

---

### 7. Анонимизация каталога листингов (C10)

**Файл:** `src/pages/MarketplacePage.tsx`

В карточке листинга каталога:

- Убрать отображение имени продавца (поле `seller` теперь `null` из API)
- Убрать ценность карты из карточки (поле `card.value` теперь `null` из API)

Условный рендеринг: если `listing.seller !== null` — показать (для обратной совместимости и `my-listings`), иначе не показывать.

Аналогично для `card.value`: если `null` — не показывать.

---

### 8. CSS-стили

**Файл:** `src/index.css`

```css
/* Страница сделки */
.pf-transaction-detail { ... }
.pf-transaction-detail__card { ... }
.pf-transaction-detail__info { ... }
.pf-transaction-detail__participants { ... }
.pf-transaction-detail__participant { ... }
.pf-transaction-detail__complaint { ... }
.pf-transaction-detail__sanction {
  background: rgba(255, 59, 48, 0.12);
  border: 1px solid var(--pf-danger);
  border-radius: 12px;
  padding: 12px 16px;
}

/* Кнопка жалобы */
.pf-complaint-btn { ... }
.pf-complaint-btn:disabled { opacity: 0.5; }

/* Лента — санкционированные сделки */
.pf-marketplace-feed__item--sanctioned .pf-marketplace-feed__price {
  text-decoration: line-through;
  opacity: 0.6;
}
.pf-sanctioned-badge {
  background: var(--pf-danger);
  color: #fff;
  font-size: 0.7rem;
  padding: 2px 6px;
  border-radius: 4px;
}
```

---

## Проверка готовности

- [ ] `npm run build` (`polemica-fantasy-webapp`) — успешно
- [ ] Route `/marketplace/transactions/:listingId` открывается, данные подгружаются
- [ ] Карта с рамкой по редкости и достижениями отображается на странице сделки
- [ ] Участники кликабельны → переход в профиль
- [ ] Кнопка «Пожаловаться» работает, disabled в правильных состояниях
- [ ] Обработка ошибок: 429, 409, 400 — user-friendly сообщения
- [ ] Блок санкции отображается при `sanction !== null`
- [ ] Лента: элементы кликабельны → страница сделки
- [ ] Лента: санкционированные сделки помечены (перечёркнутая цена, бейдж)
- [ ] Профиль: сделки кликабельны, санкционированные помечены
- [ ] Каталог: имя продавца не отображается
- [ ] Каталог: ценность карты не отображается
- [ ] `my-listings`: имя продавца и ценность по-прежнему видны
