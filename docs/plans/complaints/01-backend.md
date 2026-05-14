# План 1: Backend — жалобы, санкции, анонимизация (C1–C8)

> **Предусловия:** нет  
> **Результат:** Flyway V39, JPA-сущности `MarketplaceComplaint` и `MarketplaceListingSanction`, сервисы жалоб/санкций/транзакций, User API (детали сделки + жалоба), Admin API (списки жалоб, санкционирование, ban), анонимизация листингов, временный бан маркетплейса, уведомления о санкциях, DTO-расширения feed/profile  
> **Дизайн-документ:** [`DESIGN-MARKETPLACE-COMPLAINTS.md`](../../features/DESIGN-MARKETPLACE-COMPLAINTS.md) — §2–§14

---

## Шаги

### 1. Flyway-миграция V39 (C1)

**Файл:** `V39__marketplace_complaints.sql`  
**Путь:** `src/main/resources/db/migration/`

#### 1.1 Таблица `marketplace_complaint`

```sql
CREATE TABLE marketplace_complaint (
    id               BIGSERIAL PRIMARY KEY,
    listing_id       BIGINT NOT NULL REFERENCES marketplace_listing(id),
    telegram_user_id BIGINT NOT NULL REFERENCES telegram_user(id),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_complaint_listing_user UNIQUE (listing_id, telegram_user_id)
);

CREATE INDEX idx_complaint_listing ON marketplace_complaint (listing_id);
CREATE INDEX idx_complaint_user ON marketplace_complaint (telegram_user_id);
```

#### 1.2 Таблица `marketplace_listing_sanction`

```sql
CREATE TABLE marketplace_listing_sanction (
    id                  BIGSERIAL PRIMARY KEY,
    listing_id          BIGINT NOT NULL REFERENCES marketplace_listing(id),
    reason              TEXT NOT NULL,
    seller_fine         BIGINT NOT NULL DEFAULT 0,
    buyer_fine          BIGINT NOT NULL DEFAULT 0,
    complainant_reward  BIGINT NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    admin_username      VARCHAR(64) NOT NULL,
    CONSTRAINT uk_sanction_listing UNIQUE (listing_id)
);
```

#### 1.3 Временный бан маркетплейса

```sql
ALTER TABLE telegram_user ADD COLUMN marketplace_banned_until TIMESTAMPTZ;
```

#### 1.4 economy_config: лимит жалоб

```sql
INSERT INTO economy_config (key, value, description) VALUES
    ('marketplace.daily_complaint_limit', '5', 'Максимум жалоб на маркетплейсе в сутки');
```

---

### 2. JPA-сущности (C2)

#### 2.1 `MarketplaceComplaint`

**Файл:** `entity/MarketplaceComplaint.kt` (новый)

```kotlin
@Entity
@Table(name = "marketplace_complaint")
class MarketplaceComplaint(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "listing_id", nullable = false)
    var listing: MarketplaceListing? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "telegram_user_id", nullable = false)
    var telegramUser: TelegramUser? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
)
```

#### 2.2 `MarketplaceListingSanction`

**Файл:** `entity/MarketplaceListingSanction.kt` (новый)

```kotlin
@Entity
@Table(name = "marketplace_listing_sanction")
class MarketplaceListingSanction(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "listing_id", nullable = false)
    var listing: MarketplaceListing? = null,

    @Column(nullable = false, columnDefinition = "TEXT")
    var reason: String = "",

    @Column(name = "seller_fine", nullable = false)
    var sellerFine: Long = 0,

    @Column(name = "buyer_fine", nullable = false)
    var buyerFine: Long = 0,

    @Column(name = "complainant_reward", nullable = false)
    var complainantReward: Long = 0,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "admin_username", nullable = false, length = 64)
    var adminUsername: String = "",
)
```

#### 2.3 Обновить `TelegramUser`

**Файл:** `entity/TelegramUser.kt`

Добавить поле:

```kotlin
@Column(name = "marketplace_banned_until")
var marketplaceBannedUntil: Instant? = null,
```

#### 2.4 Расширить `FantikiTransactionReason`

**Файл:** `entity/FantikiTransactionReason.kt`

Добавить:

```kotlin
MARKETPLACE_SANCTION_FINE,
MARKETPLACE_COMPLAINT_REWARD,
```

---

### 3. Репозитории (C2)

#### 3.1 `MarketplaceComplaintRepository`

**Файл:** `repository/MarketplaceComplaintRepository.kt` (новый)

```kotlin
interface MarketplaceComplaintRepository : JpaRepository<MarketplaceComplaint, Long> {
    fun existsByListing_IdAndTelegramUser_Id(listingId: Long, telegramUserId: Long): Boolean

    fun countByListing_Id(listingId: Long): Int

    @Query("""
        SELECT COUNT(c) FROM MarketplaceComplaint c
        WHERE c.telegramUser.id = :userId AND c.createdAt >= :since
    """)
    fun countByTelegramUser_IdAndCreatedAtAfter(
        @Param("userId") userId: Long,
        @Param("since") since: Instant,
    ): Int

    fun findAllByListing_Id(listingId: Long): List<MarketplaceComplaint>
}
```

#### 3.2 `MarketplaceListingSanctionRepository`

**Файл:** `repository/MarketplaceListingSanctionRepository.kt` (новый)

```kotlin
interface MarketplaceListingSanctionRepository : JpaRepository<MarketplaceListingSanction, Long> {
    fun existsByListing_Id(listingId: Long): Boolean

    fun findByListing_Id(listingId: Long): MarketplaceListingSanction?
}
```

#### 3.3 Расширить `MarketplaceListingRepository`

**Файл:** `repository/MarketplaceListingRepository.kt`

Добавить запросы для admin:

```kotlin
@Query("""
    SELECT ml FROM MarketplaceListing ml
    JOIN MarketplaceComplaint mc ON mc.listing.id = ml.id
    WHERE ml.status = :sold
    GROUP BY ml.id
    HAVING COUNT(mc.id) >= :minComplaints
    ORDER BY COUNT(mc.id) DESC
""")
fun findSoldListingsWithComplaints(
    @Param("sold") sold: MarketplaceListingStatus,
    @Param("minComplaints") minComplaints: Long,
    pageable: Pageable,
): Page<MarketplaceListing>
```

Для агрегации по пользователям (users-by-complaints) — native query, см. шаг 8.

---

### 4. `MarketplaceComplaintService` (C3)

**Файл:** `service/MarketplaceComplaintService.kt` (новый)

Ответственность: подача жалобы, проверка лимитов, подсчёт жалоб.

```kotlin
@Service
class MarketplaceComplaintService(
    private val marketplaceComplaintRepository: MarketplaceComplaintRepository,
    private val marketplaceListingRepository: MarketplaceListingRepository,
    private val marketplaceListingSanctionRepository: MarketplaceListingSanctionRepository,
    private val economyConfigService: EconomyConfigService,
) {
    @Transactional
    fun complain(user: TelegramUser, listingId: Long): ComplainResultDto {
        val listing = marketplaceListingRepository.findById(listingId).orElseThrow {
            ResponseStatusException(NOT_FOUND, "Listing not found")
        }

        // Статус SOLD
        if (listing.status != MarketplaceListingStatus.SOLD) {
            throw ResponseStatusException(BAD_REQUEST, "Can only complain about completed transactions")
        }

        // Участник сделки не может жаловаться
        if (listing.seller?.id == user.id || listing.buyer?.id == user.id) {
            throw ResponseStatusException(BAD_REQUEST, "Cannot complain about your own transaction")
        }

        // Уже жаловался
        if (marketplaceComplaintRepository.existsByListing_IdAndTelegramUser_Id(listingId, user.id!!)) {
            throw ResponseStatusException(CONFLICT, "Already complained")
        }

        // Сделка уже санкционирована
        if (marketplaceListingSanctionRepository.existsByListing_Id(listingId)) {
            throw ResponseStatusException(BAD_REQUEST, "Transaction already sanctioned")
        }

        // Лимит в сутки
        val dailyLimit = economyConfigService.getInt("marketplace.daily_complaint_limit")
        val since = Instant.now().minus(24, ChronoUnit.HOURS)
        val todayCount = marketplaceComplaintRepository
            .countByTelegramUser_IdAndCreatedAtAfter(user.id!!, since)
        if (todayCount >= dailyLimit) {
            throw ResponseStatusException(TOO_MANY_REQUESTS, "Daily complaint limit reached ($dailyLimit)")
        }

        val complaint = MarketplaceComplaint(
            listing = listing,
            telegramUser = user,
        )
        marketplaceComplaintRepository.save(complaint)

        val totalComplaints = marketplaceComplaintRepository.countByListing_Id(listingId)
        val remaining = dailyLimit - todayCount - 1

        return ComplainResultDto(
            listingId = listingId,
            totalComplaints = totalComplaints,
            remainingToday = remaining,
        )
    }
}
```

---

### 5. `MarketplaceTransactionService` (C3)

**Файл:** `service/MarketplaceTransactionService.kt` (новый)

Ответственность: детали завершённой сделки для страницы транзакции.

```kotlin
@Service
@Transactional(readOnly = true)
class MarketplaceTransactionService(
    private val marketplaceListingRepository: MarketplaceListingRepository,
    private val marketplaceComplaintRepository: MarketplaceComplaintRepository,
    private val marketplaceListingSanctionRepository: MarketplaceListingSanctionRepository,
    private val economyConfigService: EconomyConfigService,
) {
    fun getTransactionDetail(user: TelegramUser, listingId: Long): MarketplaceTransactionDetailDto {
        val listing = marketplaceListingRepository.findById(listingId).orElseThrow {
            ResponseStatusException(NOT_FOUND, "Listing not found")
        }
        if (listing.status != MarketplaceListingStatus.SOLD) {
            throw ResponseStatusException(NOT_FOUND, "Transaction not found")
        }

        val commissionPct = economyConfigService.getMarketplaceCommissionPercent()
        val commission = listing.price * commissionPct / 100
        val sellerReceived = listing.price - commission

        val totalComplaints = marketplaceComplaintRepository.countByListing_Id(listingId)
        val userAlreadyComplained = marketplaceComplaintRepository
            .existsByListing_IdAndTelegramUser_Id(listingId, user.id!!)

        val sanction = marketplaceListingSanctionRepository.findByListing_Id(listingId)

        // Карта: снимок шаблона (soldCardTemplate) или текущий user_card.cardTemplate
        val cardTemplate = listing.soldCardTemplate ?: listing.userCard?.cardTemplate

        return MarketplaceTransactionDetailDto(
            listingId = listing.id!!,
            price = listing.price,
            soldAt = listing.soldAt!!,
            commission = commission,
            sellerReceived = sellerReceived,
            seller = listing.seller!!.toTransactionParticipant(),
            buyer = listing.buyer!!.toTransactionParticipant(),
            card = cardTemplate!!.toTransactionCard(),
            complaint = TransactionComplaintInfoDto(
                totalComplaints = totalComplaints,
                userAlreadyComplained = userAlreadyComplained,
            ),
            sanction = sanction?.let {
                TransactionSanctionInfoDto(
                    sanctionedAt = it.createdAt,
                    reason = it.reason,
                )
            },
        )
    }
}
```

Вспомогательные extension-функции — в том же файле или в отдельном маппере.

---

### 6. `MarketplaceSanctionService` (C4)

**Файл:** `service/MarketplaceSanctionService.kt` (новый)

Ответственность: применение санкции, штрафы, награды жалобщикам, публикация события.

```kotlin
@Service
class MarketplaceSanctionService(
    private val marketplaceListingRepository: MarketplaceListingRepository,
    private val marketplaceListingSanctionRepository: MarketplaceListingSanctionRepository,
    private val marketplaceComplaintRepository: MarketplaceComplaintRepository,
    private val userService: UserService,
    private val telegramUserRepository: TelegramUserRepository,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val economyConfigService: EconomyConfigService,
) {
    @Transactional
    fun sanctionTransaction(
        listingId: Long,
        request: SanctionTransactionRequest,
        adminUsername: String,
    ): SanctionTransactionResultDto {
        val listing = marketplaceListingRepository.findById(listingId).orElseThrow {
            ResponseStatusException(NOT_FOUND, "Listing not found")
        }
        if (listing.status != MarketplaceListingStatus.SOLD) {
            throw ResponseStatusException(BAD_REQUEST, "Can only sanction completed transactions")
        }
        if (marketplaceListingSanctionRepository.existsByListing_Id(listingId)) {
            throw ResponseStatusException(CONFLICT, "Transaction already sanctioned")
        }

        val seller = listing.seller!!
        val buyer = listing.buyer!!

        // 1. Штраф продавцу
        if (request.sellerFine > 0) {
            userService.forceDeductBalance(seller.id!!, request.sellerFine, MARKETPLACE_SANCTION_FINE)
        }

        // 2. Штраф покупателю
        if (request.buyerFine > 0) {
            userService.forceDeductBalance(buyer.id!!, request.buyerFine, MARKETPLACE_SANCTION_FINE)
        }

        // 3. Награда жалобщикам
        val complaints = marketplaceComplaintRepository.findAllByListing_Id(listingId)
        val complainantInfos = mutableListOf<ComplainantRewardInfo>()
        if (request.complainantReward > 0 && complaints.isNotEmpty()) {
            for (complaint in complaints) {
                val complainant = complaint.telegramUser!!
                userService.addBalance(complainant.id!!, request.complainantReward, MARKETPLACE_COMPLAINT_REWARD)
                val newBalance = userService.getBalance(complainant.id!!)
                complainantInfos.add(ComplainantRewardInfo(
                    telegramChatId = complainant.telegramId,
                    reward = request.complainantReward,
                    newBalance = newBalance,
                ))
            }
        }

        // 4. Запись санкции
        val sanction = MarketplaceListingSanction(
            listing = listing,
            reason = request.reason,
            sellerFine = request.sellerFine,
            buyerFine = request.buyerFine,
            complainantReward = request.complainantReward,
            adminUsername = adminUsername,
        )
        marketplaceListingSanctionRepository.save(sanction)

        // 5. Бан участников (опционально)
        request.banSeller?.let { ban ->
            val sellerUser = telegramUserRepository.findById(seller.id!!).orElseThrow()
            sellerUser.marketplaceBannedUntil = Instant.now().plus(ban.days.toLong(), ChronoUnit.DAYS)
            telegramUserRepository.save(sellerUser)
        }
        request.banBuyer?.let { ban ->
            val buyerUser = telegramUserRepository.findById(buyer.id!!).orElseThrow()
            buyerUser.marketplaceBannedUntil = Instant.now().plus(ban.days.toLong(), ChronoUnit.DAYS)
            telegramUserRepository.save(buyerUser)
        }

        // 6. Событие для уведомлений
        val cardTemplate = listing.soldCardTemplate ?: listing.userCard?.cardTemplate
        applicationEventPublisher.publishEvent(MarketplaceSanctionAppliedEvent(
            listingId = listingId,
            playerName = cardTemplate?.fantasyPlayer?.nickname ?: "—",
            rarity = cardTemplate?.rarity ?: Rarity.COMMON,
            price = listing.price,
            reason = request.reason,
            sellerTelegramChatId = seller.telegramId,
            sellerFine = request.sellerFine,
            sellerNewBalance = userService.getBalance(seller.id!!),
            buyerTelegramChatId = buyer.telegramId,
            buyerFine = request.buyerFine,
            buyerNewBalance = userService.getBalance(buyer.id!!),
            complainants = complainantInfos,
        ))

        return SanctionTransactionResultDto(
            listingId = listingId,
            sellerFined = request.sellerFine,
            sellerNewBalance = userService.getBalance(seller.id!!),
            sellerBannedUntil = request.banSeller?.let {
                Instant.now().plus(it.days.toLong(), ChronoUnit.DAYS)
            },
            buyerFined = request.buyerFine,
            buyerNewBalance = userService.getBalance(buyer.id!!),
            buyerBannedUntil = request.banBuyer?.let {
                Instant.now().plus(it.days.toLong(), ChronoUnit.DAYS)
            },
            complainantsRewarded = complaints.size,
            totalRewardPaid = request.complainantReward * complaints.size,
        )
    }
}
```

---

### 7. Событие и listener уведомлений (C4)

#### 7.1 Событие

**Файл:** `event/MarketplaceSanctionAppliedEvent.kt` (новый)

```kotlin
data class MarketplaceSanctionAppliedEvent(
    val listingId: Long,
    val playerName: String,
    val rarity: Rarity,
    val price: Long,
    val reason: String,
    val sellerTelegramChatId: Long,
    val sellerFine: Long,
    val sellerNewBalance: Long,
    val buyerTelegramChatId: Long,
    val buyerFine: Long,
    val buyerNewBalance: Long,
    val complainants: List<ComplainantRewardInfo>,
)

data class ComplainantRewardInfo(
    val telegramChatId: Long,
    val reward: Long,
    val newBalance: Long,
)
```

#### 7.2 Категории уведомлений

**Файл:** `entity/NotificationCategory.kt`

Добавить:

```kotlin
MARKETPLACE_SANCTION_APPLIED(
    userToggleable = false,
    enabledByDefault = true,
    description = "Уведомления о санкциях на маркетплейсе",
),
MARKETPLACE_COMPLAINT_RESOLVED(
    userToggleable = true,
    enabledByDefault = true,
    description = "Решения по вашим жалобам на маркетплейсе",
),
```

#### 7.3 Listener

**Файл:** `event/MarketplaceSanctionNotificationListener.kt` (новый)

```kotlin
@Component
class MarketplaceSanctionNotificationListener(
    private val notificationDeliveryService: NotificationDeliveryService,
    private val notificationButtonFactory: NotificationButtonFactory,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    fun onSanctionApplied(event: MarketplaceSanctionAppliedEvent) {
        val cardLabel = "«${event.playerName}» (${event.rarity})"

        // Продавцу (неотключаемо)
        val sellerText = buildString {
            append("⚠️ Сделка по карте $cardLabel за ${event.price} ₣ признана нерыночной.\n")
            append("Причина: ${event.reason}.\n")
            append("Штраф: −${event.sellerFine} ₣.\n")
            append("Баланс: ${event.sellerNewBalance} ₣.")
        }
        notificationDeliveryService.deliver(
            telegramChatId = event.sellerTelegramChatId,
            category = NotificationCategory.MARKETPLACE_SANCTION_APPLIED,
            text = sellerText,
        )

        // Покупателю (неотключаемо)
        val buyerText = if (event.buyerFine > 0) {
            buildString {
                append("⚠️ Сделка по карте $cardLabel за ${event.price} ₣ признана нерыночной.\n")
                append("Причина: ${event.reason}.\n")
                append("Штраф: −${event.buyerFine} ₣.\n")
                append("Баланс: ${event.buyerNewBalance} ₣.")
            }
        } else {
            buildString {
                append("ℹ️ Сделка по карте $cardLabel за ${event.price} ₣ признана нерыночной.\n")
                append("Причина: ${event.reason}.\n")
                append("К вам штраф не применён.")
            }
        }
        notificationDeliveryService.deliver(
            telegramChatId = event.buyerTelegramChatId,
            category = NotificationCategory.MARKETPLACE_SANCTION_APPLIED,
            text = buyerText,
        )

        // Жалобщикам (отключаемо)
        val replyMarkup = notificationButtonFactory.openTransactionButton(event.listingId)
        for (complainant in event.complainants) {
            val text = buildString {
                append("✅ По вашей жалобе на сделку $cardLabel за ${event.price} ₣ принято решение.\n")
                append("Сделка признана нерыночной.\n")
                append("Награда: +${complainant.reward} ₣.\n")
                append("Баланс: ${complainant.newBalance} ₣.")
            }
            notificationDeliveryService.deliver(
                telegramChatId = complainant.telegramChatId,
                category = NotificationCategory.MARKETPLACE_COMPLAINT_RESOLVED,
                text = text,
                replyMarkup = replyMarkup,
            )
        }
    }
}
```

#### 7.4 Кнопка в `NotificationButtonFactory`

**Файл:** `telegram/NotificationButtonFactory.kt`

Добавить:

```kotlin
fun openTransactionButton(listingId: Long) = InlineKeyboardMarkup(
    inlineKeyboard = listOf(listOf(
        InlineKeyboardButton.WebApp(
            text = "📋 Посмотреть сделку",
            url = "${appProperties.webappBaseUrl}/marketplace/transactions/$listingId",
        ),
    )),
)
```

---

### 8. Анонимизация листингов и временный бан (C5)

#### 8.1 Анонимизация каталога

**Файл:** `service/MarketplaceService.kt`

В `getListings` (каталог `GET /marketplace/listings`): при маппинге в `MarketplaceListingEntryDto` передавать `seller = null`, `card.value = null`.

**Файл:** `dto/user/response/MarketplaceResponses.kt`

Сделать `seller` nullable:

```kotlin
data class MarketplaceListingEntryDto(
    val listingId: Long,
    val price: Long,
    val createdAt: Instant,
    val card: MarketplaceListingCardDto,
    val seller: MarketplaceSellerBriefDto?,   // nullable для анонимности
    val canBuy: Boolean,
    val canBuyReason: String?,
)
```

В `MarketplaceListingCardDto` сделать `value` nullable (если ещё не):

```kotlin
val value: Long?,   // null в каталоге, показывается в my-listings и transactions
```

`getMyListings` продолжает возвращать `seller` и `card.value` как раньше.

#### 8.2 Проверка `marketplace_banned_until`

**Файл:** `service/MarketplaceService.kt`

Извлечь метод проверки бана, заменить три точки проверки `me.marketplaceBanned` на:

```kotlin
private fun checkMarketplaceBan(user: TelegramUser) {
    if (user.marketplaceBanned) {
        throw ResponseStatusException(FORBIDDEN, "Your marketplace access is suspended")
    }
    val until = user.marketplaceBannedUntil
    if (until != null && until.isAfter(Instant.now())) {
        throw ResponseStatusException(FORBIDDEN, "Your marketplace access is suspended until $until")
    }
}
```

Вызвать в `createListing`, `updateListingPrice`, `buyCard`.

---

### 9. DTO жалоб и санкций

#### 9.1 User DTO

**Файл:** `dto/user/response/MarketplaceTransactionDtos.kt` (новый)

```kotlin
data class MarketplaceTransactionDetailDto(
    val listingId: Long,
    val price: Long,
    val soldAt: Instant,
    val commission: Long,
    val sellerReceived: Long,
    val seller: TransactionParticipantDto,
    val buyer: TransactionParticipantDto,
    val card: TransactionCardDto,
    val complaint: TransactionComplaintInfoDto,
    val sanction: TransactionSanctionInfoDto?,
)

data class TransactionParticipantDto(
    val telegramId: Long,
    val displayName: String,
)

data class TransactionCardDto(
    val fantasyPlayerId: Long,
    val playerName: String,
    val playerPhotoUrl: String?,
    val rarity: Rarity,
    val achievements: List<MarketplaceCardAchievementDto>,
)

data class TransactionComplaintInfoDto(
    val totalComplaints: Int,
    val userAlreadyComplained: Boolean,
)

data class TransactionSanctionInfoDto(
    val sanctionedAt: Instant,
    val reason: String,
)

data class ComplainResultDto(
    val listingId: Long,
    val totalComplaints: Int,
    val remainingToday: Int,
)
```

#### 9.2 Admin DTO

**Файл:** `dto/admin/response/MarketplaceComplaintAdminDtos.kt` (новый)

```kotlin
data class ComplainedTransactionDto(
    val listingId: Long,
    val playerName: String,
    val rarity: Rarity,
    val price: Long,
    val soldAt: Instant,
    val seller: TransactionParticipantDto,
    val buyer: TransactionParticipantDto,
    val complaintsCount: Int,
    val sanctioned: Boolean,
)

data class PagedComplainedTransactionsDto(
    val content: List<ComplainedTransactionDto>,
    val totalElements: Long,
    val page: Int,
    val size: Int,
)

data class TransactionComplaintDetailDto(
    val userId: Long,
    val displayName: String,
    val telegramId: Long,
    val complainedAt: Instant,
)

data class TransactionComplaintsListDto(
    val complaints: List<TransactionComplaintDetailDto>,
)

data class UserByComplaintsDto(
    val telegramId: Long,
    val displayName: String,
    val totalComplaints: Int,
    val transactionsWithComplaints: Int,
    val avgComplaintsPerTransaction: Double,
    val sanctionedTransactions: Int,
    val marketplaceBanned: Boolean,
    val marketplaceBannedUntil: Instant?,
)

data class PagedUsersByComplaintsDto(
    val content: List<UserByComplaintsDto>,
    val totalElements: Long,
    val page: Int,
    val size: Int,
)
```

#### 9.3 Admin Request DTO

**Файл:** `dto/admin/request/MarketplaceComplaintAdminRequests.kt` (новый)

```kotlin
data class SanctionTransactionRequest(
    val reason: String,
    val sellerFine: Long,
    val buyerFine: Long,
    val complainantReward: Long,
    val banSeller: BanDuration?,
    val banBuyer: BanDuration?,
)

data class BanDuration(val days: Int)

data class BanUserRequest(val days: Int?)

data class SanctionTransactionResultDto(
    val listingId: Long,
    val sellerFined: Long,
    val sellerNewBalance: Long,
    val sellerBannedUntil: Instant?,
    val buyerFined: Long,
    val buyerNewBalance: Long,
    val buyerBannedUntil: Instant?,
    val complainantsRewarded: Int,
    val totalRewardPaid: Long,
)
```

---

### 10. User API (C7)

**Файл:** `controller/user/MarketplaceController.kt`

Добавить два endpoint'а:

```kotlin
@GetMapping("/transactions/{listingId}")
fun getTransactionDetail(
    @AuthenticationPrincipal user: TelegramUser,
    @PathVariable listingId: Long,
): MarketplaceTransactionDetailDto =
    marketplaceTransactionService.getTransactionDetail(user, listingId)

@PostMapping("/transactions/{listingId}/complain")
@ResponseStatus(HttpStatus.CREATED)
fun complain(
    @AuthenticationPrincipal user: TelegramUser,
    @PathVariable listingId: Long,
): ComplainResultDto =
    marketplaceComplaintService.complain(user, listingId)
```

---

### 11. Admin API (C6)

**Файл:** `controller/admin/MarketplaceAdminController.kt`

Добавить endpoint'ы (§13 дизайн-документа):

```kotlin
@GetMapping("/complained-transactions")
fun getComplainedTransactions(
    @RequestParam(defaultValue = "0") page: Int,
    @RequestParam(defaultValue = "20") size: Int,
    @RequestParam(defaultValue = "1") minComplaints: Int,
): PagedComplainedTransactionsDto

@GetMapping("/transactions/{listingId}/complaints")
fun getTransactionComplaints(
    @PathVariable listingId: Long,
): TransactionComplaintsListDto

@PostMapping("/transactions/{listingId}/sanction")
fun sanctionTransaction(
    @PathVariable listingId: Long,
    @Valid @RequestBody request: SanctionTransactionRequest,
    @AuthenticationPrincipal admin: UserDetails,
): SanctionTransactionResultDto

@GetMapping("/users-by-complaints")
fun getUsersByComplaints(
    @RequestParam(defaultValue = "0") page: Int,
    @RequestParam(defaultValue = "20") size: Int,
): PagedUsersByComplaintsDto

@PostMapping("/users/{telegramId}/ban")
fun banUser(
    @PathVariable telegramId: Long,
    @Valid @RequestBody request: BanUserRequest,
): ResponseEntity<Void>
```

Существующий `POST /unban/{telegramId}` расширить: сбрасывать **оба** поля `marketplaceBanned = false`, `marketplaceBannedUntil = null`.

Реализация — в `MarketplaceAdminService` (расширить существующий):

- `getComplainedTransactions` — запрос SOLD-листингов с JOIN на complaints, подсчёт, left join sanction
- `getTransactionComplaints` — список `MarketplaceComplaint` для listing с данными пользователей
- `sanctionTransaction` → делегирует в `MarketplaceSanctionService`
- `getUsersByComplaints` — native query с агрегацией по пользователям-участникам сделок с жалобами
- `banUser` — установка `marketplaceBanned = true` (days = null) или `marketplaceBannedUntil`

---

### 12. Изменения в существующих DTO (C8)

#### 12.1 `MarketplaceFeedItemDto`

**Файл:** `dto/user/response/MarketplaceResponses.kt`

Добавить:

```kotlin
val listingId: Long,
val sanctioned: Boolean,
```

**Файл:** `service/MarketplaceService.kt` — в `getFeed` при маппинге проставлять `listingId` и `sanctioned` (LEFT JOIN на `marketplace_listing_sanction`).

#### 12.2 `PlayerMarketplaceTradeDto`

**Файл:** `dto/user/response/PlayerProfileDtos.kt`

Добавить:

```kotlin
val listingId: Long,
val sanctioned: Boolean,
```

**Файл:** сервис, отвечающий за формирование профиля игрока — при маппинге сделок аналогично проставлять `listingId` и `sanctioned`.

#### 12.3 `MarketplaceFeedItemDto.listingId` в `MarketplaceService.getFeed`

Для ленты: `listing.id!!` уже доступен при построении DTO.

Для `sanctioned`: LEFT JOIN на `MarketplaceListingSanction` по `listing_id` или подзапрос `EXISTS`.

Подход: добавить в `MarketplaceListingSanctionRepository`:

```kotlin
fun findAllByListing_IdIn(listingIds: Collection<Long>): List<MarketplaceListingSanction>
```

В `getFeed` и профиле: загрузить sanctions одним запросом для всех listing id, сделать `Set<Long>` id → `sanctioned = id in sanctionedIds`.

---

### 13. Тесты

#### 13.1 `MarketplaceComplaintServiceTest` (unit)

- Жалоба на SOLD-листинг → success
- Жалоба участника сделки → 400
- Повторная жалоба → 409
- Превышение лимита → 429
- Жалоба на несанкционированную сделку → 400

#### 13.2 Интеграционные тесты

В `UserApiIntegrationTest`:

- `GET /marketplace/transactions/{id}` — для SOLD-листинга, проверка полей
- `POST /marketplace/transactions/{id}/complain` — 201, проверка totalComplaints, повторная → 409

В `AdminApiIntegrationTest`:

- `GET /admin/marketplace/complained-transactions` — наличие записей
- `POST /admin/marketplace/transactions/{id}/sanction` — штрафы, награды, баланс участников
- `POST /admin/marketplace/users/{telegramId}/ban` — проверка `marketplace_banned_until`
- `POST /admin/marketplace/unban/{telegramId}` — сброс обоих полей

---

## Проверка готовности

- [ ] Миграция V39 применяется без ошибок
- [ ] `MarketplaceComplaint` + `MarketplaceListingSanction` — CRUD работает
- [ ] Жалоба: правила подачи (статус, участник, дубликат, лимит, санкция) → корректные коды ошибок
- [ ] Санкция: штрафы списываются, награды начисляются, запись в аудит
- [ ] Уведомления: продавец и покупатель получают неотключаемое, жалобщики — отключаемое
- [ ] Анонимизация: `GET /listings` — `seller = null`, `card.value = null`; `GET /my-listings` — с данными
- [ ] Временный бан: `marketplace_banned_until` проверяется в `createListing`, `updateListingPrice`, `buyCard`
- [ ] `POST /unban` сбрасывает оба поля
- [ ] `MarketplaceFeedItemDto` и `PlayerMarketplaceTradeDto` содержат `listingId` и `sanctioned`
- [ ] Admin API: все 5 новых endpoint'ов работают
- [ ] Тесты зелёные: `./gradlew test`
