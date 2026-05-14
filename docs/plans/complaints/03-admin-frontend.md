# План 3: Админка — жалобы, санкции, баны (C11)

> **Предусловия:** План 1 (backend C1–C8), Admin API endpoint'ы  
> **Результат:** вкладка «Жалобы» на Marketplace Moderation, модалка санкции, вкладка «Игроки по жалобам», бан/разбан с поддержкой временных банов  
> **Дизайн-документ:** [`DESIGN-MARKETPLACE-COMPLAINTS.md`](../../features/DESIGN-MARKETPLACE-COMPLAINTS.md) — §9 (Списки в админке), §16 (Админка — новые разделы)

---

## Шаги

### 1. TypeScript-типы

**Файл:** `src/api/types.ts` (расширить)

```typescript
// === Complained Transactions ===

export interface ComplainedTransactionDto {
  listingId: number;
  playerName: string;
  rarity: string;
  price: number;
  soldAt: string;
  seller: TransactionParticipantDto;
  buyer: TransactionParticipantDto;
  complaintsCount: number;
  sanctioned: boolean;
}

export interface TransactionParticipantDto {
  telegramId: number;
  displayName: string;
}

export interface PagedComplainedTransactionsDto {
  content: ComplainedTransactionDto[];
  totalElements: number;
  page: number;
  size: number;
}

// === Complaints Detail ===

export interface TransactionComplaintDetailDto {
  userId: number;
  displayName: string;
  telegramId: number;
  complainedAt: string;
}

export interface TransactionComplaintsListDto {
  complaints: TransactionComplaintDetailDto[];
}

// === Sanction ===

export interface SanctionTransactionRequest {
  reason: string;
  sellerFine: number;
  buyerFine: number;
  complainantReward: number;
  banSeller: { days: number } | null;
  banBuyer: { days: number } | null;
}

export interface SanctionTransactionResultDto {
  listingId: number;
  sellerFined: number;
  sellerNewBalance: number;
  sellerBannedUntil: string | null;
  buyerFined: number;
  buyerNewBalance: number;
  buyerBannedUntil: string | null;
  complainantsRewarded: number;
  totalRewardPaid: number;
}

// === Users by Complaints ===

export interface UserByComplaintsDto {
  telegramId: number;
  displayName: string;
  totalComplaints: number;
  transactionsWithComplaints: number;
  avgComplaintsPerTransaction: number;
  sanctionedTransactions: number;
  marketplaceBanned: boolean;
  marketplaceBannedUntil: string | null;
}

export interface PagedUsersByComplaintsDto {
  content: UserByComplaintsDto[];
  totalElements: number;
  page: number;
  size: number;
}

// === Ban ===

export interface BanUserRequest {
  days: number | null;
}
```

---

### 2. API-клиент

**Файл:** `src/api/marketplaceAdmin.ts` (расширить)

```typescript
export async function getComplainedTransactions(params: {
  page?: number;
  size?: number;
  minComplaints?: number;
}): Promise<PagedComplainedTransactionsDto> {
  const query = new URLSearchParams();
  if (params.page != null) query.set('page', String(params.page));
  if (params.size != null) query.set('size', String(params.size));
  if (params.minComplaints != null) query.set('minComplaints', String(params.minComplaints));
  return apiGet(`/api/v1/admin/marketplace/complained-transactions?${query}`);
}

export async function getTransactionComplaints(
  listingId: number,
): Promise<TransactionComplaintsListDto> {
  return apiGet(`/api/v1/admin/marketplace/transactions/${listingId}/complaints`);
}

export async function sanctionTransaction(
  listingId: number,
  request: SanctionTransactionRequest,
): Promise<SanctionTransactionResultDto> {
  return apiPost(`/api/v1/admin/marketplace/transactions/${listingId}/sanction`, request);
}

export async function getUsersByComplaints(params: {
  page?: number;
  size?: number;
}): Promise<PagedUsersByComplaintsDto> {
  const query = new URLSearchParams();
  if (params.page != null) query.set('page', String(params.page));
  if (params.size != null) query.set('size', String(params.size));
  return apiGet(`/api/v1/admin/marketplace/users-by-complaints?${query}`);
}

export async function banMarketplaceUser(
  telegramId: number,
  request: BanUserRequest,
): Promise<void> {
  return apiPost(`/api/v1/admin/marketplace/users/${telegramId}/ban`, request);
}
```

Существующий `unbanMarketplaceUser` уже сбрасывает оба поля (backend расширен в Плане 1).

---

### 3. Вкладка «Жалобы» на `MarketplaceModerationPage` (§16.1)

**Файл:** `src/pages/MarketplaceModerationPage.tsx` (расширить)

Добавить новый таб **«Жалобы»** (рядом с существующими табами «Pair analysis», «История санкций»).

#### 3.1 Таблица жалоб

Компонент `ComplainedTransactionsTab` (inline или отдельный файл).

Ant Design `<Table>` с колонками:

| Колонка | Поле | Рендеринг |
|---------|------|-----------|
| Карта | `playerName`, `rarity` | Имя + бейдж редкости (`Tag` с цветом) |
| Цена | `price` | `{price} ₣` |
| Продавец | `seller.displayName` | Имя + `seller.telegramId` |
| Покупатель | `buyer.displayName` | Имя + `buyer.telegramId` |
| Дата | `soldAt` | `dayjs(soldAt).format(...)` |
| Жалобы | `complaintsCount` | Число, красный если ≥ 3 |
| Статус | `sanctioned` | `Tag`: «Ожидает» (оранжевый) / «Санкционирована» (красный) |
| Действие | — | Кнопка «Санкционировать» (disabled если `sanctioned`) |

Пагинация: `<Table pagination>` с `total={data.totalElements}`.

Фильтр над таблицей: `InputNumber` «Мин. жалоб» (default 1).

Кнопка «Санкционировать» → открывает модалку санкции (шаг 4).

#### 3.2 React Query

```typescript
const { data, isLoading } = useQuery({
  queryKey: ['admin', 'complained-transactions', page, minComplaints],
  queryFn: () => getComplainedTransactions({ page, size: 20, minComplaints }),
});
```

---

### 4. Модалка санкции (§16.2)

**Файл:** `src/pages/MarketplaceModerationPage.tsx` (или отдельный компонент `SanctionTransactionModal.tsx`)

Ant Design `<Modal>` с содержимым:

#### 4.1 Информация о сделке

Вверху модалки — краткая сводка: карта, цена, продавец, покупатель, дата, число жалоб.

#### 4.2 Список жалобщиков

Загружается через `getTransactionComplaints(listingId)` при открытии модалки.

Таблица или список:

| Колонка | Поле |
|---------|------|
| Имя | `displayName` |
| Telegram ID | `telegramId` |
| Дата жалобы | `complainedAt` |

#### 4.3 Форма санкции

Поля `<Form>`:

| Поле | Тип | Default |
|------|-----|---------|
| Причина | `Input.TextArea` | `"Нерыночная сделка"` |
| Штраф продавцу | `InputNumber` | `sellerReceived` (рассчитать: `price - ⌊price × commission% / 100⌋`) |
| Штраф покупателю | `InputNumber` | `0` |
| Награда жалобщику | `InputNumber` | `⌊commission / complainantsCount⌋` |
| Бан продавца | `Checkbox` + `InputNumber` (дни) | Не отмечен, 3 дня |
| Бан покупателя | `Checkbox` + `InputNumber` (дни) | Не отмечен, 3 дня |

Рекомендуемые значения рассчитываются при открытии модалки на основе данных сделки. `commission` = `price × commissionPct / 100` (commissionPct из `economy-info` или hardcode, либо добавить в `ComplainedTransactionDto`).

Предупреждение (`Alert` warning): если `complainantReward × complainantsCount > commission` — «Суммарная награда жалобщикам превышает комиссию сделки».

#### 4.4 Подтверждение и отправка

Кнопка **«Применить санкцию»** с `Popconfirm`:

```
Применить санкцию?
Штраф продавцу: -X ₣
Штраф покупателю: -Y ₣
Награда жалобщикам: Z × N = W ₣
Забанить продавца: 3 дня / Нет
Забанить покупателя: Нет
```

`onOk` → `sanctionTransaction(listingId, request)`.

После успеха:

1. Показать `Modal.success` с результатом (`SanctionTransactionResultDto`): новые балансы, сроки банов, число награждённых
2. Инвалидировать `['admin', 'complained-transactions']`
3. Закрыть модалку

---

### 5. Вкладка «Игроки по жалобам» (§16.3)

**Файл:** `src/pages/MarketplaceModerationPage.tsx` (расширить)

Новый таб **«Игроки по жалобам»**.

#### 5.1 Таблица

Ant Design `<Table>` с колонками:

| Колонка | Поле | Рендеринг |
|---------|------|-----------|
| Пользователь | `displayName`, `telegramId` | Имя + id |
| Всего жалоб | `totalComplaints` | Число |
| Сделок с жалобами | `transactionsWithComplaints` | Число |
| Среднее жалоб/сделка | `avgComplaintsPerTransaction` | Число с 1 знаком после запятой |
| Санкционировано | `sanctionedTransactions` | Число |
| Статус бана | `marketplaceBanned`, `marketplaceBannedUntil` | См. ниже |
| Действие | — | Кнопка «Забанить» / «Разбанить» |

Рендеринг статуса бана:

```typescript
function renderBanStatus(record: UserByComplaintsDto) {
  if (record.marketplaceBanned) return <Tag color="red">Перманентный бан</Tag>;
  if (record.marketplaceBannedUntil) {
    const until = dayjs(record.marketplaceBannedUntil);
    if (until.isAfter(dayjs())) {
      return <Tag color="orange">Бан до {until.format('DD.MM.YYYY HH:mm')}</Tag>;
    }
  }
  return <Tag color="green">Активен</Tag>;
}
```

Пагинация: `<Table pagination>` с `total={data.totalElements}`.

#### 5.2 Действия: бан / разбан

Кнопка **«Забанить»** → `Popover` или мини-модалка с:
- Пресеты: `3 дня`, `7 дней`, `30 дней`, `Перманент`
- Или `InputNumber` для кастомного числа дней

При выборе → `Popconfirm` → `banMarketplaceUser(telegramId, { days })`.

- `days = null` → перманентный бан
- `days = N` → временный бан

Кнопка **«Разбанить»** (видна если `marketplaceBanned || marketplaceBannedUntil`) → `Popconfirm` → `unbanMarketplaceUser(telegramId)`.

После любого действия → инвалидация `['admin', 'users-by-complaints']`.

---

### 6. Расширение существующего разбана

**Файл:** `src/pages/MarketplaceModerationPage.tsx`

Существующее поле «Unban» в секции pair analysis уже вызывает `POST /unban/{telegramId}`. Backend расширен (План 1) — теперь сбрасывает оба поля. Никаких изменений в UI не нужно, поведение расширилось автоматически.

---

### 7. Навигация

**Файл:** `src/pages/MarketplaceModerationPage.tsx`

Табы на странице Marketplace Moderation после изменений:

1. **Pair analysis** (существующий)
2. **Жалобы** (новый — шаг 3)
3. **Игроки по жалобам** (новый — шаг 5)
4. **История санкций** (существующий, pair ban history)

Порядок табов — на усмотрение, «Жалобы» логично первым (самый частый workflow).

---

## Проверка готовности

- [ ] `npm run build` (`polemica-fantasy-admin`) — успешно
- [ ] Таб «Жалобы»: таблица загружается, пагинация работает, фильтр мин. жалоб
- [ ] Кнопка «Санкционировать» открывает модалку с данными сделки и жалобщиками
- [ ] Форма санкции: рекомендуемые значения подставляются, валидация, предупреждение о превышении комиссии
- [ ] Подтверждение санкции → штрафы/награды применяются, модалка с результатом
- [ ] После санкции строка в таблице обновляется (статус → «Санкционирована», кнопка disabled)
- [ ] Таб «Игроки по жалобам»: таблица загружается, пагинация
- [ ] Статус бана отображается корректно (перманент / временный / активен)
- [ ] Бан: пресеты + кастомные дни, перманент работает
- [ ] Разбан: кнопка работает, сбрасывает оба поля
