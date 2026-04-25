# План 4: Отслеживание карт на маркетплейсе (MARKETPLACE_WATCH)

> **Предусловия:** План 1 (NotificationDeliveryService, таблицы `marketplace_watch_filter` и `marketplace_watch_pending`)  
> **Результат:** CRUD фильтров отслеживания, matching при выставлении лота, батч-уведомления по расписанию, User API  
> **Дизайн-документ:** §13 (MARKETPLACE_WATCH)

---

## Шаги

### 1. JPA-сущность: `MarketplaceWatchFilter`

**Файл:** `entity/MarketplaceWatchFilter.kt` (новый)

```kotlin
@Entity
@Table(name = "marketplace_watch_filter")
class MarketplaceWatchFilter(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "telegram_user_id", nullable = false)
    var telegramUser: TelegramUser? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fantasy_player_id")
    var fantasyPlayer: FantasyPlayer? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tournament_id")
    var tournament: Tournament? = null,

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    var rarity: Rarity? = null,

    @Column(name = "max_price")
    var maxPrice: Long? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
)
```

### 2. JPA-сущность: `MarketplaceWatchPending`

**Файл:** `entity/MarketplaceWatchPending.kt` (новый)

```kotlin
@Entity
@Table(name = "marketplace_watch_pending")
class MarketplaceWatchPending(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "telegram_user_id", nullable = false)
    var telegramUser: TelegramUser? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "listing_id", nullable = false)
    var listing: MarketplaceListing? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
)
```

### 3. Репозитории

**Файл:** `repository/MarketplaceWatchFilterRepository.kt` (новый)

```kotlin
interface MarketplaceWatchFilterRepository : JpaRepository<MarketplaceWatchFilter, Long> {
    fun findAllByTelegramUser_Id(telegramUserId: Long): List<MarketplaceWatchFilter>
    fun countByTelegramUser_Id(telegramUserId: Long): Int
    fun deleteByIdAndTelegramUser_Id(id: Long, telegramUserId: Long): Int

    @Query(nativeQuery = true, value = """
        SELECT DISTINCT mwf.telegram_user_id
        FROM marketplace_watch_filter mwf
        JOIN telegram_user tu ON tu.id = mwf.telegram_user_id
        WHERE tu.bot_blocked = FALSE
          AND mwf.telegram_user_id != :sellerId
          AND (mwf.fantasy_player_id IS NULL OR mwf.fantasy_player_id = :fantasyPlayerId)
          AND (mwf.rarity IS NULL OR mwf.rarity = :rarity)
          AND (mwf.max_price IS NULL OR mwf.max_price >= :price)
          AND (mwf.tournament_id IS NULL OR mwf.tournament_id IN (:tournamentIds))
          AND NOT EXISTS (
              SELECT 1 FROM notification_preference np
              WHERE np.telegram_user_id = mwf.telegram_user_id
                AND np.category = 'MARKETPLACE_WATCH'
                AND np.enabled = FALSE
          )
    """)
    fun findMatchingUserIds(
        sellerId: Long,
        fantasyPlayerId: Long,
        rarity: String,
        price: Long,
        tournamentIds: List<Long>,
    ): List<Long>
}
```

**Файл:** `repository/MarketplaceWatchPendingRepository.kt` (новый)

```kotlin
interface MarketplaceWatchPendingRepository : JpaRepository<MarketplaceWatchPending, Long> {
    fun findAllByTelegramUser_Id(telegramUserId: Long): List<MarketplaceWatchPending>
    fun deleteAllByTelegramUser_Id(telegramUserId: Long)

    @Query("SELECT DISTINCT p.telegramUser.id FROM MarketplaceWatchPending p")
    fun findDistinctUserIds(): List<Long>
}
```

### 4. Event: `MarketplaceListingCreatedEvent`

**Файл:** `event/MarketplaceListingCreatedEvent.kt` (новый)

```kotlin
data class MarketplaceListingCreatedEvent(
    val listingId: Long,
    val sellerId: Long,
    val fantasyPlayerId: Long,
    val tournamentIds: List<Long>,
    val rarity: Rarity,
    val price: Long,
    val playerName: String,
)
```

### 5. Публикация event при создании лота

**Файл:** `service/MarketplaceService.kt`

В конце `createListing`, после `save`, перед return:

```kotlin
applicationEventPublisher.publishEvent(
    MarketplaceListingCreatedEvent(
        listingId = saved.id!!,
        sellerId = seller.id!!,
        fantasyPlayerId = userCard.cardTemplate!!.fantasyPlayer!!.id!!,
        tournamentIds = /* турниры, в которых участвует этот игрок */,
        rarity = userCard.cardTemplate!!.rarity,
        price = saved.price,
        playerName = userCard.cardTemplate!!.fantasyPlayer!!.nickname,
    )
)
```

`tournamentIds` — получить через `TournamentPlayerRepository` или аналогичный запрос (турниры, в которых есть `fantasy_player`).

### 6. Listener: matching и запись в pending

**Файл:** `event/MarketplaceWatchNotificationListener.kt` (новый)

```kotlin
@Component
class MarketplaceWatchNotificationListener(
    private val marketplaceWatchFilterRepository: MarketplaceWatchFilterRepository,
    private val marketplaceWatchPendingRepository: MarketplaceWatchPendingRepository,
    private val telegramUserRepository: TelegramUserRepository,
    private val marketplaceListingRepository: MarketplaceListingRepository,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    fun onListingCreated(event: MarketplaceListingCreatedEvent) {
        val matchingUserIds = marketplaceWatchFilterRepository.findMatchingUserIds(
            sellerId = event.sellerId,
            fantasyPlayerId = event.fantasyPlayerId,
            rarity = event.rarity.name,
            price = event.price,
            tournamentIds = event.tournamentIds,
        )
        if (matchingUserIds.isEmpty()) return

        val listing = marketplaceListingRepository.findById(event.listingId).orElse(null) ?: return
        for (userId in matchingUserIds) {
            val user = telegramUserRepository.findById(userId).orElse(null) ?: continue
            marketplaceWatchPendingRepository.save(
                MarketplaceWatchPending(telegramUser = user, listing = listing)
            )
        }
    }
}
```

Не отправляет сразу — складывает в pending-очередь для батчинга.

### 7. Шедулер: батч-отправка watch-уведомлений

**Файл:** `scheduler/MarketplaceWatchScheduler.kt` (новый)

```kotlin
@Component
class MarketplaceWatchScheduler(
    private val marketplaceWatchPendingRepository: MarketplaceWatchPendingRepository,
    private val notificationDeliveryService: NotificationDeliveryService,
    private val notificationButtonFactory: NotificationButtonFactory,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedRate = 300_000) // 5 минут
    @Transactional
    fun flushWatchNotifications() {
        val userIds = marketplaceWatchPendingRepository.findDistinctUserIds()
        for (userId in userIds) {
            try {
                val pending = marketplaceWatchPendingRepository.findAllByTelegramUser_Id(userId)
                if (pending.isEmpty()) continue

                val text = buildWatchMessage(pending)
                val replyMarkup = if (pending.size == 1) {
                    val playerId = pending.first().listing!!.userCard!!.cardTemplate!!.fantasyPlayer!!.id!!
                    notificationButtonFactory.openMarketplaceFilteredButton(playerId)
                } else {
                    notificationButtonFactory.openMarketplaceButton()
                }

                notificationDeliveryService.deliver(
                    telegramChatId = /* resolve telegram_id from userId */,
                    category = NotificationCategory.MARKETPLACE_WATCH,
                    text = text,
                    replyMarkup = replyMarkup,
                )
                marketplaceWatchPendingRepository.deleteAllByTelegramUser_Id(userId)
            } catch (e: Exception) {
                log.error("Failed to flush watch notifications for userId={}", userId, e)
            }
        }
    }
}
```

### 8. Формат сообщений

**Файл:** `scheduler/MarketplaceWatchScheduler.kt`

Одна карта:
```
🔔 На маркетплейсе появилась карта из вашего отслеживания:

Игрок: {playerName}
Редкость: {rarity}
Цена: {price} ₣
```

Несколько карт (batch):
```
🔔 На маркетплейсе появились карты из вашего отслеживания:

• {playerName1} ({rarity1}) — {price1} ₣
• {playerName2} ({rarity2}) — {price2} ₣

[Открыть маркетплейс]
```

### 9. `MarketplaceWatchService` — CRUD

**Файл:** `service/MarketplaceWatchService.kt` (новый)

```kotlin
@Service
class MarketplaceWatchService(
    private val marketplaceWatchFilterRepository: MarketplaceWatchFilterRepository,
    private val telegramUserRepository: TelegramUserRepository,
    private val fantasyPlayerRepository: FantasyPlayerRepository,
    private val tournamentRepository: TournamentRepository,
) {
    companion object {
        const val MAX_WATCHES_PER_USER = 10
    }

    @Transactional(readOnly = true)
    fun getWatches(internalUserId: Long): MarketplaceWatchesResponse {
        val filters = marketplaceWatchFilterRepository.findAllByTelegramUser_Id(internalUserId)
        return MarketplaceWatchesResponse(
            watches = filters.map { it.toDto() },
            maxWatches = MAX_WATCHES_PER_USER,
        )
    }

    @Transactional
    fun createWatch(
        internalUserId: Long,
        request: CreateMarketplaceWatchRequest,
    ): MarketplaceWatchDto {
        val count = marketplaceWatchFilterRepository.countByTelegramUser_Id(internalUserId)
        if (count >= MAX_WATCHES_PER_USER) {
            throw ResponseStatusException(BAD_REQUEST, "Maximum $MAX_WATCHES_PER_USER watches reached")
        }
        if (request.fantasyPlayerId == null && request.tournamentId == null && request.rarity == null) {
            throw ResponseStatusException(BAD_REQUEST, "At least one filter criterion required")
        }
        if (request.maxPrice != null && request.maxPrice <= 0) {
            throw ResponseStatusException(BAD_REQUEST, "maxPrice must be positive")
        }
        val user = telegramUserRepository.findById(internalUserId).orElseThrow()
        val player = request.fantasyPlayerId?.let { fantasyPlayerRepository.findById(it).orElseThrow() }
        val tournament = request.tournamentId?.let { tournamentRepository.findById(it).orElseThrow() }

        val filter = marketplaceWatchFilterRepository.save(
            MarketplaceWatchFilter(
                telegramUser = user,
                fantasyPlayer = player,
                tournament = tournament,
                rarity = request.rarity,
                maxPrice = request.maxPrice,
            )
        )
        return filter.toDto()
    }

    @Transactional
    fun deleteWatch(internalUserId: Long, watchId: Long) {
        val deleted = marketplaceWatchFilterRepository.deleteByIdAndTelegramUser_Id(watchId, internalUserId)
        if (deleted == 0) {
            throw ResponseStatusException(NOT_FOUND, "Watch filter not found")
        }
    }
}
```

### 10. DTO для marketplace watches

**Файл:** `dto/user/response/MarketplaceWatchDtos.kt` (новый)

```kotlin
data class MarketplaceWatchDto(
    val id: Long,
    val fantasyPlayer: FantasyPlayerBriefDto?,
    val tournament: TournamentBriefDto?,
    val rarity: String?,
    val maxPrice: Long?,
    val createdAt: Instant,
)

data class MarketplaceWatchesResponse(
    val watches: List<MarketplaceWatchDto>,
    val maxWatches: Int,
)
```

**Файл:** `dto/user/request/CreateMarketplaceWatchRequest.kt` (новый)

```kotlin
data class CreateMarketplaceWatchRequest(
    val fantasyPlayerId: Long?,
    val tournamentId: Long?,
    val rarity: Rarity?,
    val maxPrice: Long?,
)
```

### 11. Controller: marketplace watches

**Файл:** `controller/user/NotificationSettingsController.kt` (обновить)

Добавить эндпоинты:

```kotlin
@GetMapping("/marketplace-watches")
fun getMarketplaceWatches(@AuthenticationPrincipal user: TelegramUser) =
    marketplaceWatchService.getWatches(user.id!!)

@PostMapping("/marketplace-watches")
fun createMarketplaceWatch(
    @AuthenticationPrincipal user: TelegramUser,
    @RequestBody request: CreateMarketplaceWatchRequest,
) = marketplaceWatchService.createWatch(user.id!!, request)

@DeleteMapping("/marketplace-watches/{id}")
fun deleteMarketplaceWatch(
    @AuthenticationPrincipal user: TelegramUser,
    @PathVariable id: Long,
) = marketplaceWatchService.deleteWatch(user.id!!, id)
```

Или выделить в отдельный `MarketplaceWatchController` — на усмотрение.

---

## Проверка готовности

- [ ] `POST /api/v1/settings/marketplace-watches` — создаёт фильтр, валидация: хотя бы одно поле, max 10
- [ ] `GET /api/v1/settings/marketplace-watches` — возвращает фильтры с `maxWatches`
- [ ] `DELETE /api/v1/settings/marketplace-watches/{id}` — удаляет только свой фильтр
- [ ] Создание лота на маркетплейсе → `MarketplaceListingCreatedEvent` публикуется
- [ ] Matching: фильтры по player/tournament/rarity/maxPrice корректно матчат
- [ ] Продавец не получает уведомление о своём лоте
- [ ] Pending-записи создаются для каждого matching-пользователя
- [ ] Шедулер (каждые 5 мин) агрегирует pending → одно сообщение на пользователя
- [ ] Batch: 5 карт за 5 минут → одно сообщение со списком
- [ ] Пользователь с `MARKETPLACE_WATCH = false` → не получает
- [ ] Пользователь с `bot_blocked = true` → не получает
- [ ] Дубликат фильтра → ошибка `UNIQUE constraint`
