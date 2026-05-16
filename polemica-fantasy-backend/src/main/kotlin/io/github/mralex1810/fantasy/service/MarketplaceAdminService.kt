package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.dto.admin.request.BanPairRequest
import io.github.mralex1810.fantasy.dto.admin.request.BanUserRequest
import io.github.mralex1810.fantasy.dto.admin.request.MarkPairClearedRequest
import io.github.mralex1810.fantasy.dto.admin.request.SanctionTransactionRequest
import io.github.mralex1810.fantasy.dto.admin.response.BanPairConfiscatedCardDto
import io.github.mralex1810.fantasy.dto.admin.response.BanPairPreviewDto
import io.github.mralex1810.fantasy.dto.admin.response.BanPairPreviewUserDto
import io.github.mralex1810.fantasy.dto.admin.response.BanPairResultDto
import io.github.mralex1810.fantasy.dto.admin.response.BanPairUserResultDto
import io.github.mralex1810.fantasy.dto.admin.response.ComplainedTransactionDto
import io.github.mralex1810.fantasy.dto.admin.response.ConcurrentListingDto
import io.github.mralex1810.fantasy.dto.admin.response.MarketplaceAdminParticipantDto
import io.github.mralex1810.fantasy.dto.admin.response.PagedComplainedTransactionsDto
import io.github.mralex1810.fantasy.dto.admin.response.PagedPairSanctionHistoryDto
import io.github.mralex1810.fantasy.dto.admin.response.PagedUsersByComplaintsDto
import io.github.mralex1810.fantasy.dto.admin.response.PairAnalysisDto
import io.github.mralex1810.fantasy.dto.admin.response.PairSanctionHistoryItemDto
import io.github.mralex1810.fantasy.dto.admin.response.PairTradeDto
import io.github.mralex1810.fantasy.dto.admin.response.PairTradesUserBriefDto
import io.github.mralex1810.fantasy.dto.admin.response.PairTradesResultDto
import io.github.mralex1810.fantasy.dto.admin.response.SanctionTransactionResultDto
import io.github.mralex1810.fantasy.dto.admin.response.TransactionComplaintDetailDto
import io.github.mralex1810.fantasy.dto.admin.response.TransactionComplaintsListDto
import io.github.mralex1810.fantasy.dto.admin.response.TransactionMarketContextDto
import io.github.mralex1810.fantasy.dto.admin.response.UserByComplaintsDto
import io.github.mralex1810.fantasy.entity.FantikiTransaction
import io.github.mralex1810.fantasy.entity.FantikiTransactionReason
import io.github.mralex1810.fantasy.entity.MarketplaceListingStatus
import io.github.mralex1810.fantasy.entity.MarketplacePairClearance
import io.github.mralex1810.fantasy.entity.MarketplacePairClearanceId
import io.github.mralex1810.fantasy.entity.MarketplacePairSanctionHistory
import io.github.mralex1810.fantasy.entity.TelegramUser
import io.github.mralex1810.fantasy.event.PairBanNotificationEvent
import io.github.mralex1810.fantasy.repository.FantasyTeamCardRepository
import io.github.mralex1810.fantasy.repository.FantikiTransactionRepository
import io.github.mralex1810.fantasy.repository.MarketplaceComplaintRepository
import io.github.mralex1810.fantasy.repository.MarketplaceListingRepository
import io.github.mralex1810.fantasy.repository.MarketplaceListingSanctionRepository
import io.github.mralex1810.fantasy.repository.MarketplacePairClearanceRepository
import io.github.mralex1810.fantasy.repository.MarketplacePairSanctionHistoryRepository
import io.github.mralex1810.fantasy.repository.TelegramUserRepository
import io.github.mralex1810.fantasy.repository.UserCardOwnershipHistoryRepository
import io.github.mralex1810.fantasy.repository.UserCardRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.math.BigInteger
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
class MarketplaceAdminService(
    private val economyConfigService: EconomyConfigService,
    private val marketplaceListingRepository: MarketplaceListingRepository,
    private val marketplaceListingSanctionRepository: MarketplaceListingSanctionRepository,
    private val marketplaceComplaintRepository: MarketplaceComplaintRepository,
    private val userCardRepository: UserCardRepository,
    private val fantasyTeamCardRepository: FantasyTeamCardRepository,
    private val userCardOwnershipHistoryRepository: UserCardOwnershipHistoryRepository,
    private val fantikiTransactionRepository: FantikiTransactionRepository,
    private val userService: UserService,
    private val telegramUserRepository: TelegramUserRepository,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val marketplacePairClearanceRepository: MarketplacePairClearanceRepository,
    private val marketplacePairSanctionHistoryRepository: MarketplacePairSanctionHistoryRepository,
    private val marketplaceSanctionService: MarketplaceSanctionService,
) {

    @Transactional(readOnly = true)
    fun getPairAnalysis(): List<PairAnalysisDto> {
        val pct = economyConfigService.getMarketplaceCommissionPercent()
        val raw = marketplaceListingRepository.aggregateSoldTradesBySellerBuyer(pct)
        if (raw.isEmpty()) return emptyList()

        data class Stat(val count: Long, val totalGross: Long, val totalNet: Long)
        val directed = mutableMapOf<Pair<Long, Long>, Stat>()
        for (r in raw) {
            val sellerId = toLongId(r[0])
            val buyerId = toLongId(r[1])
            if (buyerId == 0L) continue
            val c = toLongId(r[2])
            val gross = toLongId(r[3])
            val net = toLongId(r[4])
            directed[sellerId to buyerId] = Stat(c, gross, net)
        }
        if (directed.isEmpty()) return emptyList()

        val distinctIds = directed.keys.flatMap { (a, b) -> listOf(a, b) }.distinct()
        val byId = telegramUserRepository.findAllById(distinctIds).associateBy { it.id!! }

        val seenPairs = mutableSetOf<String>()
        data class PairInter(
            val lo: Long,
            val hi: Long,
            val userATelegramId: Long,
            val userBTelegramId: Long,
            val countAtoB: Long,
            val grossAtoB: Long,
            val countBtoA: Long,
            val grossBtoA: Long,
            val netTransfer: Long,
            val bidirectional: Boolean,
        )
        val out = ArrayList<PairInter>()
        for ((s, b) in directed.keys) {
            val lo = minOf(s, b)
            val hi = maxOf(s, b)
            val key = "$lo:$hi"
            if (key in seenPairs) continue

            val aId = lo
            val bId = hi
            val forward = directed[aId to bId]
            val reverse = directed[bId to aId]
            val countAtoB = forward?.count ?: 0L
            val grossAtoB = forward?.totalGross ?: 0L
            val countBtoA = reverse?.count ?: 0L
            val grossBtoA = reverse?.totalGross ?: 0L
            val netA = forward?.totalNet ?: 0L
            val netB = reverse?.totalNet ?: 0L
            val netTransfer = netA - netB

            val uA = byId[aId] ?: continue
            val uB = byId[bId] ?: continue
            seenPairs.add(key)
            out.add(
                PairInter(
                    lo = aId,
                    hi = bId,
                    userATelegramId = uA.telegramId,
                    userBTelegramId = uB.telegramId,
                    countAtoB = countAtoB,
                    grossAtoB = grossAtoB,
                    countBtoA = countBtoA,
                    grossBtoA = grossBtoA,
                    netTransfer = netTransfer,
                    bidirectional = countAtoB > 0 && countBtoA > 0,
                ),
            )
        }
        val keySet = out.map { it.lo to it.hi }.toSet()
        val byKey: Map<Pair<Long, Long>, MarketplacePairClearance> = if (keySet.isEmpty()) {
            emptyMap()
        } else {
            val lows = keySet.map { it.first }.toSet()
            marketplacePairClearanceRepository.findByUserIdLowIn(lows)
                .asSequence()
                .filter { (it.userIdLow to it.userIdHigh) in keySet }
                .associateBy { it.userIdLow to it.userIdHigh }
        }
        return out
            .sortedWith(
                compareByDescending<PairInter> { it.countAtoB + it.countBtoA }
                    .thenByDescending { it.grossAtoB + it.grossBtoA },
            )
            .map { p ->
                val c = byKey[p.lo to p.hi]
                PairAnalysisDto(
                    userATelegramId = p.userATelegramId,
                    userBTelegramId = p.userBTelegramId,
                    tradesAtoB = p.countAtoB,
                    tradesTotalAtoB = p.grossAtoB,
                    tradesBtoA = p.countBtoA,
                    tradesTotalBtoA = p.grossBtoA,
                    netTransfer = p.netTransfer,
                    bidirectional = p.bidirectional,
                    cleared = c != null,
                    clearedAt = c?.createdAt,
                    clearedNote = c?.note,
                )
            }
    }

    @Transactional(readOnly = true)
    fun getPairTrades(telegramIdA: Long, telegramIdB: Long): PairTradesResultDto {
        val (userA, userB) = loadPairUsers(telegramIdA, telegramIdB)
        val idA = userA.id!!
        val idB = userB.id!!

        val sold = marketplaceListingRepository.findSoldListingsBetweenUsers(
            MarketplaceListingStatus.SOLD,
            idA,
            idB,
        )
        val pct = economyConfigService.getMarketplaceCommissionPercent()

        var totalGross = 0L
        var totalNet = 0L
        val items = sold.map { ml ->
            val price = ml.price
            val sellerReceived = price - (price * pct) / 100
            totalGross += price
            totalNet += sellerReceived
            val tpl = ml.soldCardTemplate ?: ml.userCard!!.cardTemplate!!
            val fp = tpl.fantasyPlayer!!
            val ownerTg = ml.userCard!!.telegramUser!!.telegramId
            val buyerTg = ml.buyer!!.telegramId
            PairTradeDto(
                listingId = ml.id!!,
                price = price,
                sellerReceived = sellerReceived,
                soldAt = ml.soldAt,
                sellerTelegramId = ml.seller!!.telegramId,
                buyerTelegramId = buyerTg,
                userCardId = ml.userCard!!.id!!,
                playerName = fp.nickname,
                rarity = tpl.rarity,
                currentOwnerTelegramId = ownerTg,
                buyerStillOwnsCard = ownerTg == buyerTg,
            )
        }
        return PairTradesResultDto(
            userA = toPairTradesUserBrief(userA),
            userB = toPairTradesUserBrief(userB),
            trades = items,
            totalTrades = items.size,
            totalGrossFantiki = totalGross,
            totalSellerReceived = totalNet,
        )
    }

    private fun toPairTradesUserBrief(u: TelegramUser): PairTradesUserBriefDto =
        PairTradesUserBriefDto(
            username = u.username,
            telegramId = u.telegramId,
            displayName = u.publicDisplayName(),
            fantiki = u.fantiki,
        )

    @Transactional(readOnly = true)
    fun getBanPairPreview(telegramA: Long, telegramB: Long): BanPairPreviewDto {
        val (userA, userB) = loadPairUsers(telegramA, telegramB)
        val idA = userA.id!!
        val idB = userB.id!!
        val sold = MarketplaceListingStatus.SOLD
        val pct = economyConfigService.getMarketplaceCommissionPercent()
        val takeA = marketplaceListingRepository.sumSellerReceivedForSalesTo(sold, idA, idB, pct).coerceAtLeast(0L)
        val takeB = marketplaceListingRepository.sumSellerReceivedForSalesTo(sold, idB, idA, pct).coerceAtLeast(0L)
        return BanPairPreviewDto(
            userA = toBanPairPreviewUser(userA, takeA, listConfiscatedCardsForPreview(userA, userB, sold)),
            userB = toBanPairPreviewUser(userB, takeB, listConfiscatedCardsForPreview(userB, userA, sold)),
        )
    }

    @Transactional(readOnly = true)
    fun getBanPairHistory(pageable: Pageable): PagedPairSanctionHistoryDto {
        val page = marketplacePairSanctionHistoryRepository.findAllByOrderByCreatedAtDesc(pageable)
        val userIds = page.content.flatMap { listOf(it.userIdLow, it.userIdHigh) }.distinct()
        val users = if (userIds.isEmpty()) {
            emptyMap()
        } else {
            telegramUserRepository.findAllById(userIds).associateBy { it.id!! }
        }
        val content = page.content.map { h ->
            val uLo = requireNotNull(users[h.userIdLow]) { "User low ${h.userIdLow} not found for sanction history" }
            val uHi = requireNotNull(users[h.userIdHigh]) { "User high ${h.userIdHigh} not found for sanction history" }
            PairSanctionHistoryItemDto(
                id = h.id!!,
                createdAt = h.createdAt,
                reason = h.reason,
                userLowTelegramId = uLo.telegramId,
                userHighTelegramId = uHi.telegramId,
                userLowDisplayName = uLo.publicDisplayName(),
                userHighDisplayName = uHi.publicDisplayName(),
                fantikiTakenLow = h.fantikiTakenLow,
                fantikiTakenHigh = h.fantikiTakenHigh,
                cardsCountLow = h.cardsCountLow,
                cardsCountHigh = h.cardsCountHigh,
            )
        }
        return PagedPairSanctionHistoryDto(
            content = content,
            page = page.number,
            size = page.size,
            totalElements = page.totalElements,
            totalPages = page.totalPages,
        )
    }

    @Transactional(readOnly = true)
    fun getComplainedTransactions(
        page: Int,
        size: Int,
        minComplaints: Int,
        sortBy: String?,
    ): PagedComplainedTransactionsDto {
        val safePage = page.coerceAtLeast(0)
        val safeSize = size.coerceIn(1, 100)
        val safeMinComplaints = minComplaints.coerceAtLeast(1)
        val complaintRows = marketplaceComplaintRepository.findListingComplaintCounts(safeMinComplaints.toLong())
        if (complaintRows.isEmpty()) {
            return PagedComplainedTransactionsDto(
                content = emptyList(),
                page = safePage,
                size = safeSize,
                totalElements = 0,
                totalPages = 0,
            )
        }
        val complaintCountByListingId = complaintRows.associate { row ->
            toLongId(row[0]) to toLongId(row[1]).toInt()
        }
        val listingIds = complaintCountByListingId.keys.toList()
        val listingsById = marketplaceListingRepository.findAllWithTradeDetailsByIdIn(listingIds)
            .associateBy { it.id!! }
        val sanctionedIds = marketplaceListingSanctionRepository.findListingIdsWithSanctions(listingIds).toSet()

        val content = complaintCountByListingId.entries.mapNotNull { (listingId, complaintsCount) ->
            val listing = listingsById[listingId] ?: return@mapNotNull null
            val seller = listing.seller ?: return@mapNotNull null
            val buyer = listing.buyer ?: return@mapNotNull null
            val template = listing.soldCardTemplate ?: listing.userCard?.cardTemplate ?: return@mapNotNull null
            val player = template.fantasyPlayer ?: return@mapNotNull null
            val soldAt = listing.soldAt ?: return@mapNotNull null
            ComplainedTransactionDto(
                listingId = listingId,
                playerName = player.nickname,
                rarity = template.rarity,
                price = listing.price,
                createdAt = listing.createdAt,
                soldAt = soldAt,
                seller = MarketplaceAdminParticipantDto(
                    telegramId = seller.telegramId,
                    displayName = seller.publicDisplayName(),
                ),
                buyer = MarketplaceAdminParticipantDto(
                    telegramId = buyer.telegramId,
                    displayName = buyer.publicDisplayName(),
                ),
                complaintsCount = complaintsCount,
                sanctioned = listingId in sanctionedIds,
            )
        }
        val sorted = when (sortBy?.trim()?.lowercase()) {
            null, "", "complaints_desc" -> content.sortedWith(
                compareByDescending<ComplainedTransactionDto> { it.complaintsCount }
                    .thenByDescending { it.soldAt }
                    .thenByDescending { it.listingId },
            )

            "sold_at_desc" -> content.sortedWith(
                compareByDescending<ComplainedTransactionDto> { it.soldAt }
                    .thenByDescending { it.complaintsCount }
                    .thenByDescending { it.listingId },
            )

            else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid sortBy")
        }
        val totalElements = sorted.size.toLong()
        val totalPages = if (totalElements == 0L) 0 else ((totalElements + safeSize - 1) / safeSize).toInt()
        val pageContent = sorted.drop(safePage * safeSize).take(safeSize)
        return PagedComplainedTransactionsDto(
            content = pageContent,
            page = safePage,
            size = safeSize,
            totalElements = totalElements,
            totalPages = totalPages,
        )
    }

    @Transactional(readOnly = true)
    fun getTransactionComplaints(listingId: Long): TransactionComplaintsListDto {
        val listing = marketplaceListingRepository.findSoldByIdWithTradeDetails(
            id = listingId,
            sold = MarketplaceListingStatus.SOLD,
        ) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found")
        if (listing.buyer == null || listing.seller == null) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found")
        }
        val reviewedTemplate = listing.soldCardTemplate ?: listing.userCard?.cardTemplate
            ?: throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Transaction card template is missing")
        val fantasyPlayer = reviewedTemplate.fantasyPlayer
            ?: throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Transaction fantasy player is missing")
        val fantasyPlayerId = fantasyPlayer.id
            ?: throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Transaction fantasy player id is missing")
        val soldAt = listing.soldAt
            ?: throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Transaction soldAt is missing")
        val reviewedTemplateId = reviewedTemplate.id
            ?: throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Transaction template id is missing")

        val concurrentListings = marketplaceListingRepository.findConcurrentListingsForContext(
            fantasyPlayerId = fantasyPlayerId,
            rarity = reviewedTemplate.rarity,
            soldAt = soldAt,
            excludeId = listingId,
            active = MarketplaceListingStatus.ACTIVE,
            sold = MarketplaceListingStatus.SOLD,
        )
        val concurrentListingDtos = concurrentListings.mapNotNull { concurrent ->
            val concurrentListingId = concurrent.id ?: return@mapNotNull null
            val concurrentSeller = concurrent.seller ?: return@mapNotNull null
            val effectiveTemplate = concurrent.soldCardTemplate ?: concurrent.userCard?.cardTemplate ?: return@mapNotNull null
            ConcurrentListingDto(
                listingId = concurrentListingId,
                sellerDisplayName = concurrentSeller.publicDisplayName(),
                sellerTelegramId = concurrentSeller.telegramId,
                price = concurrent.price,
                createdAt = concurrent.createdAt,
                soldAt = concurrent.soldAt,
                active = concurrent.status == MarketplaceListingStatus.ACTIVE,
                sameTemplate = effectiveTemplate.id == reviewedTemplateId,
            )
        }
        val byPriceThenId = compareBy<ConcurrentListingDto> { it.price }.thenBy { it.listingId }
        val concurrentSameTemplate = concurrentListingDtos.filter { it.sameTemplate }.sortedWith(byPriceThenId)
        val concurrentSamePlayerRarity = concurrentListingDtos.filter { !it.sameTemplate }.sortedWith(byPriceThenId)

        val complaints = marketplaceComplaintRepository.findAllByListing_IdOrderByCreatedAtAsc(listingId)
        return TransactionComplaintsListDto(
            complaints = complaints.map { complaint ->
                val user = complaint.telegramUser
                    ?: throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Complaint user is missing")
                val userId = user.id
                    ?: throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Complaint user id is missing")
                TransactionComplaintDetailDto(
                    userId = userId,
                    displayName = user.publicDisplayName(),
                    telegramId = user.telegramId,
                    complainedAt = complaint.createdAt,
                )
            },
            marketContext = TransactionMarketContextDto(
                listingCreatedAt = listing.createdAt,
                concurrentSameTemplate = concurrentSameTemplate,
                concurrentSamePlayerRarity = concurrentSamePlayerRarity,
            ),
        )
    }

    @Transactional
    fun sanctionTransaction(
        listingId: Long,
        request: SanctionTransactionRequest,
        adminUsername: String,
    ): SanctionTransactionResultDto =
        marketplaceSanctionService.sanctionTransaction(
            listingId = listingId,
            request = request,
            adminUsername = adminUsername,
        )

    @Transactional(readOnly = true)
    fun getUsersByComplaints(
        page: Int,
        size: Int,
        sortBy: String?,
    ): PagedUsersByComplaintsDto {
        val safePage = page.coerceAtLeast(0)
        val safeSize = size.coerceIn(1, 100)
        val complaintRows = marketplaceComplaintRepository.findListingComplaintCounts(1)
        if (complaintRows.isEmpty()) {
            return PagedUsersByComplaintsDto(
                content = emptyList(),
                page = safePage,
                size = safeSize,
                totalElements = 0,
                totalPages = 0,
            )
        }
        val complaintCountByListingId = complaintRows.associate { row ->
            toLongId(row[0]) to toLongId(row[1]).toInt()
        }
        val listingIds = complaintCountByListingId.keys.toList()
        val listingsById = marketplaceListingRepository.findAllWithTradeDetailsByIdIn(listingIds)
            .associateBy { it.id!! }
        val sanctionedIds = marketplaceListingSanctionRepository.findListingIdsWithSanctions(listingIds).toSet()

        data class UserComplaintStats(
            var totalComplaints: Int = 0,
            var transactionsWithComplaints: Int = 0,
            var sanctionedTransactions: Int = 0,
        )

        val statsByUserId = LinkedHashMap<Long, UserComplaintStats>()
        for ((listingId, complaintsCount) in complaintCountByListingId) {
            val listing = listingsById[listingId] ?: continue
            val sellerId = listing.seller?.id ?: continue
            val buyerId = listing.buyer?.id ?: continue
            val sanctioned = listingId in sanctionedIds
            for (userId in listOf(sellerId, buyerId)) {
                val stats = statsByUserId.getOrPut(userId) { UserComplaintStats() }
                stats.totalComplaints += complaintsCount
                stats.transactionsWithComplaints += 1
                if (sanctioned) {
                    stats.sanctionedTransactions += 1
                }
            }
        }
        val usersById = if (statsByUserId.isEmpty()) {
            emptyMap()
        } else {
            telegramUserRepository.findAllById(statsByUserId.keys).associateBy { it.id!! }
        }
        val items = statsByUserId.mapNotNull { (userId, stats) ->
            val user = usersById[userId] ?: return@mapNotNull null
            UserByComplaintsDto(
                telegramId = user.telegramId,
                displayName = user.publicDisplayName(),
                totalComplaints = stats.totalComplaints,
                transactionsWithComplaints = stats.transactionsWithComplaints,
                avgComplaintsPerTransaction = if (stats.transactionsWithComplaints == 0) {
                    0.0
                } else {
                    stats.totalComplaints.toDouble() / stats.transactionsWithComplaints.toDouble()
                },
                sanctionedTransactions = stats.sanctionedTransactions,
                marketplaceBanned = user.marketplaceBanned,
                marketplaceBannedUntil = user.marketplaceBannedUntil,
            )
        }
        val sorted = when (sortBy?.trim()?.lowercase()) {
            null, "", "total_complaints_desc" -> items.sortedWith(
                compareByDescending<UserByComplaintsDto> { it.totalComplaints }
                    .thenByDescending { it.transactionsWithComplaints }
                    .thenByDescending { it.telegramId },
            )

            "avg_complaints_desc" -> items.sortedWith(
                compareByDescending<UserByComplaintsDto> { it.avgComplaintsPerTransaction }
                    .thenByDescending { it.totalComplaints }
                    .thenByDescending { it.telegramId },
            )

            else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid sortBy")
        }
        val totalElements = sorted.size.toLong()
        val totalPages = if (totalElements == 0L) 0 else ((totalElements + safeSize - 1) / safeSize).toInt()
        val pageContent = sorted.drop(safePage * safeSize).take(safeSize)
        return PagedUsersByComplaintsDto(
            content = pageContent,
            page = safePage,
            size = safeSize,
            totalElements = totalElements,
            totalPages = totalPages,
        )
    }

    @Transactional
    fun banUser(telegramId: Long, request: BanUserRequest) {
        val user = telegramUserRepository.findByTelegramId(telegramId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")
        val days = request.days
        if (days == null) {
            user.marketplaceBanned = true
            user.marketplaceBannedUntil = null
        } else {
            if (days <= 0) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Ban days must be positive")
            }
            user.marketplaceBanned = false
            user.marketplaceBannedUntil = Instant.now().plus(days.toLong(), ChronoUnit.DAYS)
        }
        telegramUserRepository.save(user)
    }

    @Transactional
    fun banPair(request: BanPairRequest): BanPairResultDto {
        val tga = request.telegramIdA ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "telegramIdA is required")
        val tgb = request.telegramIdB ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "telegramIdB is required")
        val reason = request.reason?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "reason is required")
        val (userA, userB) = loadPairUsers(tga, tgb)
        val idA = userA.id!!
        val idB = userB.id!!

        val pct = economyConfigService.getMarketplaceCommissionPercent()
        val sold = MarketplaceListingStatus.SOLD

        // Money first for both, then cards/listings. Otherwise the first pass removes SOLD rows (e.g. partner A received
        // a card in a B→A sale) and the second pass under-counts the seller’s net in sumSellerReceivedForSalesTo.
        val takeA = marketplaceListingRepository.sumSellerReceivedForSalesTo(sold, idA, idB, pct).coerceAtLeast(0L)
        val takeB = marketplaceListingRepository.sumSellerReceivedForSalesTo(sold, idB, idA, pct).coerceAtLeast(0L)
        if (takeA > 0) {
            userService.forceDeductBalance(idA, takeA, FantikiTransactionReason.ADMIN_PAIR_BAN)
        }
        if (takeB > 0) {
            userService.forceDeductBalance(idB, takeB, FantikiTransactionReason.ADMIN_PAIR_BAN)
        }

        val partA = sanctionsForOneUserInPair(
            self = userA,
            partner = userB,
            confiscateFantiki = takeA,
            sold = sold,
        )
        val partB = sanctionsForOneUserInPair(
            self = userB,
            partner = userA,
            confiscateFantiki = takeB,
            sold = sold,
        )

        val result = BanPairResultDto(
            userA = partA,
            userB = partB,
            reason = reason,
        )
        val lo = minOf(idA, idB)
        val hi = maxOf(idA, idB)
        val (fantikiLow, fantikiHigh) = if (idA < idB) {
            partA.fantikiConfiscated to partB.fantikiConfiscated
        } else {
            partB.fantikiConfiscated to partA.fantikiConfiscated
        }
        val (countLow, countHigh) = if (idA < idB) {
            partA.cardsConfiscated.size to partB.cardsConfiscated.size
        } else {
            partB.cardsConfiscated.size to partA.cardsConfiscated.size
        }
        marketplacePairSanctionHistoryRepository.save(
            MarketplacePairSanctionHistory(
                createdAt = Instant.now(),
                userIdLow = lo,
                userIdHigh = hi,
                reason = reason,
                fantikiTakenLow = fantikiLow,
                fantikiTakenHigh = fantikiHigh,
                cardsCountLow = countLow,
                cardsCountHigh = countHigh,
            ),
        )
        applicationEventPublisher.publishEvent(
            PairBanNotificationEvent(
                telegramChatId = partA.telegramId,
                reason = reason,
                fantikiConfiscated = partA.fantikiConfiscated,
                newBalance = partA.newBalance,
                cardsConfiscated = partA.cardsConfiscated.map { c -> "${c.playerName} (${c.rarity})" },
            ),
        )
        applicationEventPublisher.publishEvent(
            PairBanNotificationEvent(
                telegramChatId = partB.telegramId,
                reason = reason,
                fantikiConfiscated = partB.fantikiConfiscated,
                newBalance = partB.newBalance,
                cardsConfiscated = partB.cardsConfiscated.map { c -> "${c.playerName} (${c.rarity})" },
            ),
        )
        return result
    }

    @Transactional
    fun markPairCleared(request: MarkPairClearedRequest) {
        val tga = request.telegramIdA ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "telegramIdA is required")
        val tgb = request.telegramIdB ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "telegramIdB is required")
        if (tga == tgb) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "telegramIdA and telegramIdB must be different")
        }
        val userA = telegramUserRepository.findByTelegramId(tga)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User A not found")
        val userB = telegramUserRepository.findByTelegramId(tgb)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User B not found")
        val idA = userA.id!!
        val idB = userB.id!!
        val lo = minOf(idA, idB)
        val hi = maxOf(idA, idB)
        val note = request.note?.trim()?.takeIf { it.isNotEmpty() }
        val id = MarketplacePairClearanceId(userIdLow = lo, userIdHigh = hi)
        if (marketplacePairClearanceRepository.existsById(id)) {
            val existing = marketplacePairClearanceRepository.findById(id).orElseThrow()
            if (note != null) {
                existing.note = note
                marketplacePairClearanceRepository.save(existing)
            }
        } else {
            marketplacePairClearanceRepository.save(
                MarketplacePairClearance(
                    userIdLow = lo,
                    userIdHigh = hi,
                    createdAt = Instant.now(),
                    note = note,
                ),
            )
        }
    }

    @Transactional
    fun unmarkPairCleared(telegramIdA: Long, telegramIdB: Long) {
        if (telegramIdA == telegramIdB) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "userA and userB must be different")
        }
        val userA = telegramUserRepository.findByTelegramId(telegramIdA)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User A not found")
        val userB = telegramUserRepository.findByTelegramId(telegramIdB)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User B not found")
        val lo = minOf(userA.id!!, userB.id!!)
        val hi = maxOf(userA.id!!, userB.id!!)
        val n = marketplacePairClearanceRepository.deleteByUserIdPair(low = lo, high = hi)
        if (n == 0) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "No clearance for this pair")
        }
    }

    @Transactional
    fun unban(telegramId: Long) {
        val user = telegramUserRepository.findByTelegramId(telegramId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")
        user.marketplaceBanned = false
        user.marketplaceBannedUntil = null
        telegramUserRepository.save(user)
    }

    private fun sanctionsForOneUserInPair(
        self: TelegramUser,
        partner: TelegramUser,
        confiscateFantiki: Long,
        sold: MarketplaceListingStatus,
    ): BanPairUserResultDto {
        val selfId = self.id!!
        val partnerId = partner.id!!

        val toRemove = userCardRepository.findUserCardsBoughtOnMarketplaceFromPartner(
            currentOwnerId = selfId,
            partnerId = partnerId,
            sold = sold,
        )
        val cardPayload = mutableListOf<BanPairConfiscatedCardDto>()
        for (uc in toRemove) {
            if (uc.telegramUser!!.id != selfId) {
                continue
            }
            val ucId = uc.id!!
            val template = uc.cardTemplate!!
            val fp = template.fantasyPlayer!!
            cardPayload.add(
                BanPairConfiscatedCardDto(
                    userCardId = ucId,
                    playerName = fp.nickname,
                    rarity = template.rarity,
                ),
            )
            fantasyTeamCardRepository.deleteAllByUserCard_Id(ucId)
            userCardOwnershipHistoryRepository.deleteAllByUserCard_Id(ucId)
            marketplaceListingRepository.deleteAllByUserCard_Id(ucId)
            val ownerRef = telegramUserRepository.getReferenceById(selfId)
            fantikiTransactionRepository.save(
                FantikiTransaction(
                    telegramUser = ownerRef,
                    amount = 0L,
                    reason = FantikiTransactionReason.ADMIN_CARD_CONFISCATE,
                ),
            )
            userCardRepository.delete(uc)
        }

        // Re-load: [self] may be detached with stale [fantiki] after forceDeduct in [banPair].
        val fresh = telegramUserRepository.findById(selfId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "User not found after pair sanctions")
        }
        return BanPairUserResultDto(
            telegramId = fresh.telegramId,
            displayName = fresh.publicDisplayName(),
            fantikiConfiscated = confiscateFantiki,
            newBalance = fresh.fantiki,
            cardsConfiscated = cardPayload,
            listingsCancelled = 0,
        )
    }

    private fun loadPairUsers(telegramIdA: Long, telegramIdB: Long): Pair<TelegramUser, TelegramUser> {
        if (telegramIdA == telegramIdB) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "userA and userB must be different")
        }
        val userA = telegramUserRepository.findByTelegramId(telegramIdA)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User A not found")
        val userB = telegramUserRepository.findByTelegramId(telegramIdB)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User B not found")
        return userA to userB
    }

    private fun toBanPairPreviewUser(
        u: TelegramUser,
        fantikiToConfiscate: Long,
        cards: List<BanPairConfiscatedCardDto>,
    ): BanPairPreviewUserDto {
        val bal = u.fantiki
        return BanPairPreviewUserDto(
            telegramId = u.telegramId,
            displayName = u.publicDisplayName(),
            balance = bal,
            fantikiToConfiscate = fantikiToConfiscate,
            balanceAfter = (bal - fantikiToConfiscate).coerceAtLeast(0L),
            cardsToConfiscate = cards,
        )
    }

    private fun listConfiscatedCardsForPreview(
        self: TelegramUser,
        partner: TelegramUser,
        sold: MarketplaceListingStatus,
    ): List<BanPairConfiscatedCardDto> {
        val selfId = self.id!!
        val partnerId = partner.id!!
        val rows = userCardRepository.findUserCardsBoughtOnMarketplaceFromPartner(
            currentOwnerId = selfId,
            partnerId = partnerId,
            sold = sold,
        )
        val out = ArrayList<BanPairConfiscatedCardDto>()
        for (uc in rows) {
            if (uc.telegramUser!!.id != selfId) {
                continue
            }
            val template = uc.cardTemplate!!
            val fp = template.fantasyPlayer!!
            out.add(
                BanPairConfiscatedCardDto(
                    userCardId = uc.id!!,
                    playerName = fp.nickname,
                    rarity = template.rarity,
                ),
            )
        }
        return out
    }

    private fun toLongId(v: Any?): Long = when (v) {
        null -> 0L
        is Long -> v
        is Int -> v.toLong()
        is BigInteger -> v.longValueExact()
        is BigDecimal -> v.longValueExact()
        is Number -> v.toLong()
        else -> 0L
    }
}
