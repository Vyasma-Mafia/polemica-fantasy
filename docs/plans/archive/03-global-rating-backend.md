# План 3: Глобальный рейтинг (backend)

> **Предусловия:** План 1 (CardValueService существует)  
> **Результат:** API `GET /api/v1/rating` возвращает глобальный рейтинг пользователей по ценности портфеля  
> **Дизайн-документ:** §3 (Глобальный рейтинг), §7.1

---

## Шаги

### 1. DTO глобального рейтинга

**Файл:** `dto/user/response/RatingDtos.kt` (новый)

```kotlin
data class RatingEntryDto(
    val rank: Int,
    val user: UserPublicDto,
    val fantikiBalance: Long,
    val cardsValue: Long,
    val totalValue: Long,
    val cardsCount: Int,
)

data class GlobalRatingDto(
    val entries: List<RatingEntryDto>,
    val currentUser: RatingEntryDto?,
)
```

### 2. `GlobalRatingService`

**Файл:** `service/GlobalRatingService.kt` (новый)

Логика:
1. Одним SQL-запросом агрегировать `portfolio_value = fantiki + Σ card_value` для каждого пользователя
2. Сортировка по `total_value DESC`
3. Пагинация (опционально — первая версия без пагинации, если пользователей < 1000)
4. Для текущего пользователя — его позиция в рейтинге

**Ключевые решения:**
- Учитываются **все** карточки пользователя, включая `uses_remaining = 0` и карты в ACTIVE листинге (§3.1)
- Ценность вычисляется через формулу `base_rarity_value + perk_count × perk_bonus` прямо в SQL

### 3. SQL-запрос в Repository

**Файл:** `repository/GlobalRatingRepository.kt` (новый, или метод в `TelegramUserRepository`)

Нативный запрос (§3.4):

```sql
SELECT
    tu.id,
    tu.telegram_id,
    tu.username,
    tu.first_name,
    tu.display_name,
    tu.fantiki,
    COALESCE(cv.cards_value, 0)  AS cards_value,
    COALESCE(cv.cards_count, 0)  AS cards_count,
    tu.fantiki + COALESCE(cv.cards_value, 0) AS total_value
FROM telegram_user tu
LEFT JOIN (
    SELECT
        uc.telegram_user_id,
        COUNT(*)        AS cards_count,
        SUM(
            (SELECT COALESCE(ec_base.value, '0')::BIGINT
             FROM economy_config ec_base
             WHERE ec_base.key = 'card.value.' || ct.rarity)
            +
            COALESCE(perk.cnt, 0) *
            (SELECT COALESCE(ec_bonus.value, '0')::BIGINT
             FROM economy_config ec_bonus
             WHERE ec_bonus.key = 'card.value.perk_bonus')
        ) AS cards_value
    FROM user_card uc
    JOIN card_template ct ON ct.id = uc.card_template_id
    LEFT JOIN (
        SELECT card_template_id, COUNT(DISTINCT perk_id) AS cnt
        FROM card_template_perk
        GROUP BY card_template_id
    ) perk ON perk.card_template_id = ct.id
    GROUP BY uc.telegram_user_id
) cv ON cv.telegram_user_id = tu.id
ORDER BY total_value DESC
```

**Альтернатива (проще):** загрузить в `GlobalRatingService` всех пользователей с картами и считать ценность через `CardValueService` в коде. Менее эффективно, но проще для первой версии. SQL-вариант — отложенная оптимизация при > 200 пользователей.

**Рекомендация:** начать с варианта в коде (загрузить всех пользователей, их карты, посчитать через `CardValueService`), и перейти на SQL только если производительность неприемлема.

### 4. Кэширование

**Файл:** `service/GlobalRatingService.kt`

Spring `@Cacheable` с TTL 5 минут:

```kotlin
@Cacheable("globalRating", key = "'all'")
fun getRating(): List<RatingEntryProjection> { ... }
```

Инвалидация:
- После `SeriesFinalizationService.finalizeSeries` (крупная транзакция — начисление наград)
- После покупки/переработки карт (опционально — или просто TTL)
- Через `@CacheEvict` или `CacheManager.getCache("globalRating")?.clear()`

Для простоты V1: TTL-based без явной инвалидации. `@EnableCaching` + `CaffeineCacheManager` (или Spring default).

### 5. Позиция текущего пользователя

В `GlobalRatingService.getRating(currentUserId)`:
1. Получить полный рейтинг (из кэша)
2. Найти позицию текущего пользователя
3. Если пользователь отсутствует в рейтинге (нет карт, нулевой баланс) — вычислить его `totalValue` (= `fantiki`, т.к. карт нет) и определить позицию

### 6. Controller: `GET /api/v1/rating`

**Файл:** `controller/user/RatingController.kt` (новый)

```kotlin
@RestController
@RequestMapping("/api/v1/rating")
class RatingController(
    private val globalRatingService: GlobalRatingService,
) {
    @GetMapping
    fun getRating(auth: TelegramAuthentication): GlobalRatingDto {
        return globalRatingService.getRating(auth.user)
    }
}
```

Ответ — как в §3.3:
```json
{
  "entries": [...],
  "currentUser": { "rank": 42, ... }
}
```

### 7. Тесты

- Юнит-тест `CardValueService`: проверить формулу для каждой редкости с разным числом перков
- Интеграционный тест `GlobalRatingService`: два пользователя с разным балансом и картами → правильный порядок
- Тест на пользователя без карт (только баланс)
- Тест на пользователя с `uses_remaining = 0` (карта всё равно считается)

---

## Проверка готовности

- [ ] `GET /api/v1/rating` возвращает список пользователей, отсортированный по `totalValue` DESC
- [ ] `currentUser` содержит позицию текущего пользователя
- [ ] Карты с `uses_remaining = 0` учтены в рейтинге
- [ ] Карты в ACTIVE листинге учтены в рейтинге
- [ ] Кэш работает (повторный запрос не порождает SQL)
- [ ] После изменения `economy_config` ценности пересчитываются (при истечении TTL кэша)
