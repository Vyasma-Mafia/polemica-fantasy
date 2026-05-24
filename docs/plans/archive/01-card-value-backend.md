# План 1: Ценность карт (backend)

> **Предусловия:** нет  
> **Результат:** бэкенд вычисляет ценность карты, отдаёт её в DTO и предоставляет параметры формулы через API  
> **Дизайн-документ:** §2 (Ценность карты), §6.1 (economy_config keys), §7.1, §7.6

---

## Шаги

### 1. Flyway-миграция: seed `economy_config`

**Файл:** `V33__card_value_config.sql`  
**Путь:** `src/main/resources/db/migration/`

Добавить ключи в `economy_config`:

| Key | Value | Description |
|-----|-------|-------------|
| `card.value.COMMON` | `25` | Базовая ценность COMMON |
| `card.value.RARE` | `40` | Базовая ценность RARE |
| `card.value.EPIC` | `80` | Базовая ценность EPIC |
| `card.value.LEGENDARY` | `370` | Базовая ценность LEGENDARY |
| `card.value.perk_bonus` | `10` | Бонус за каждое перк |

```sql
INSERT INTO economy_config (key, value, description) VALUES
    ('card.value.COMMON',           '25',  'Базовая ценность COMMON карты'),
    ('card.value.RARE',             '40',  'Базовая ценность RARE карты'),
    ('card.value.EPIC',             '80',  'Базовая ценность EPIC карты'),
    ('card.value.LEGENDARY',        '370', 'Базовая ценность LEGENDARY карты'),
    ('card.value.perk_bonus', '10',  'Бонус к ценности за каждое перк шаблона');
```

### 2. `EconomyConfigService` — методы доступа

**Файл:** `service/EconomyConfigService.kt`

Добавить методы:

```kotlin
fun getCardBaseValue(rarity: Rarity): Long = getLong("card.value.$rarity")
fun getCardPerkBonus(): Long = getLong("card.value.perk_bonus")
```

### 3. `CardValueService` — вычисление ценности

**Файл:** `service/CardValueService.kt` (новый)

```kotlin
@Service
class CardValueService(
    private val economyConfigService: EconomyConfigService,
) {
    fun calculateValue(rarity: Rarity, perkCount: Int): Long {
        val base = economyConfigService.getCardBaseValue(rarity)
        val bonus = economyConfigService.getCardPerkBonus()
        return base + perkCount * bonus
    }

    fun calculateValue(cardTemplate: CardTemplate): Long {
        return calculateValue(
            cardTemplate.rarity,
            cardTemplate.perks.distinctBy { it.perk!!.id }.size,
        )
    }

    fun calculateValue(userCard: UserCard): Long {
        return calculateValue(userCard.cardTemplate!!)
    }
}
```

Ценность вычисляется на лету — не хранится в БД (§2.4).

### 4. `UserCardItemDto` — добавить поле `value`

**Файл:** `dto/user/response/UserResponses.kt`

Добавить поле `value: Long` в `UserCardItemDto`.

### 5. `UserCard.toUserCardItemDto` — передавать `value`

**Файл:** `service/UserCardItemMapping.kt`

Добавить параметр `cardValueService: CardValueService` в `toUserCardItemDto`. Вычислять `value` из шаблона:

```kotlin
fun UserCard.toUserCardItemDto(
    templateOverride: CardTemplate? = null,
    imageStorage: ImageStorageService,
    cardValueService: CardValueService,
): UserCardItemDto {
    val ct = templateOverride ?: cardTemplate!!
    // ... существующий код ...
    return UserCardItemDto(
        // ... существующие поля ...
        value = cardValueService.calculateValue(ct),
    )
}
```

### 6. Обновить все вызовы `toUserCardItemDto`

Все места, где вызывается `toUserCardItemDto`, должны передавать `cardValueService`. Затронутые сервисы:

- `UserController` → `GET /me/cards` (метод `listCards` в сервисе, который маппит карты)
- `UserFantasyTeamService` → `getPublicTeamForSeries` (маппит `PublicFantasyTeamSlotDto.card`)
- `LegendaryUpgradeService.upgrade` → возвращает `UserCardItemDto`
- `StoreController` / `CardService` → `BuyPackResponseDto.cards`
- `MarketplaceService` → листинги с карточками
- `CardLifecycleService` → если возвращает карту

**Подход:** инжектить `CardValueService` в каждый из этих сервисов и пробросить в `toUserCardItemDto`.

### 7. `EconomyInfoDto` — добавить `cardValues`

**Файл:** `dto/user/response/EconomyLifecycleDtos.kt`

```kotlin
data class CardValueInfoDto(
    val baseValues: Map<Rarity, Long>,
    val perkBonus: Long,
)

data class EconomyInfoDto(
    // ... существующие поля ...
    val cardValues: CardValueInfoDto,
)
```

### 8. `EconomyConfigService.buildEconomyInfo` — заполнить `cardValues`

**Файл:** `service/EconomyConfigService.kt`

```kotlin
val cardBaseValues = Rarity.entries.associateWith { getCardBaseValue(it) }
val cardPerkBonus = getCardPerkBonus()

return EconomyInfoDto(
    // ... существующие поля ...
    cardValues = CardValueInfoDto(
        baseValues = cardBaseValues,
        perkBonus = cardPerkBonus,
    ),
)
```

### 9. Endpoint `GET /api/v1/card-value/info`

**Файл:** `controller/user/CardValueController.kt` (новый)

Возвращает параметры формулы ценности (base per rarity + perk bonus). Данные берутся из `EconomyConfigService`.

Альтернатива: не создавать отдельный контроллер, а использовать расширенный `EconomyInfoDto` (шаг 7–8). Зависит от предпочтений — отдельный endpoint чище для документации.

### 10. Админка: видимость ключей card.value.*

Ключи в `economy_config` уже управляются через `EconomyConfigAdminController` (`GET/PUT /api/v1/admin/economy-config`). Новые ключи автоматически появятся в админке — дополнительные изменения на бэкенде не нужны.

---

## Проверка готовности

- [ ] Миграция V33 применяется без ошибок
- [ ] `GET /me/cards` возвращает `value` у каждой карты
- [ ] `GET /economy-info` содержит секцию `cardValues`
- [ ] `GET /api/v1/card-value/info` возвращает параметры формулы (если решено делать отдельный endpoint)
- [ ] Маркетплейс, паки, команды, legendary upgrade — все DTO карт содержат `value`
- [ ] Изменение `economy_config` через админку → ценности карт пересчитываются мгновенно (вычисление на лету)
