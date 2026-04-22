package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.dto.admin.request.BanPairRequest
import io.github.mralex1810.fantasy.dto.admin.response.BanPairConfiscatedCardDto
import io.github.mralex1810.fantasy.dto.admin.response.BanPairResultDto
import io.github.mralex1810.fantasy.dto.admin.response.BanPairUserResultDto
import io.github.mralex1810.fantasy.dto.admin.response.PairAnalysisDto
import io.github.mralex1810.fantasy.dto.admin.response.PairTradeDto
import io.github.mralex1810.fantasy.dto.admin.response.PairTradesUserBriefDto
import io.github.mralex1810.fantasy.dto.admin.response.PairTradesResultDto
import io.github.mralex1810.fantasy.entity.FantikiTransaction
import io.github.mralex1810.fantasy.entity.FantikiTransactionReason
import io.github.mralex1810.fantasy.entity.MarketplaceListingStatus
import io.github.mralex1810.fantasy.entity.TelegramUser
import io.github.mralex1810.fantasy.event.PairBanNotificationEvent
import io.github.mralex1810.fantasy.repository.FantasyTeamCardRepository
import io.github.mralex1810.fantasy.repository.FantikiTransactionRepository
import io.github.mralex1810.fantasy.repository.MarketplaceListingRepository
import io.github.mralex1810.fantasy.repository.TelegramUserRepository
import io.github.mralex1810.fantasy.repository.UserCardOwnershipHistoryRepository
import io.github.mralex1810.fantasy.repository.UserCardRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.math.BigInteger

@Service
class MarketplaceAdminService(
    private val economyConfigService: EconomyConfigService,
    private val marketplaceListingRepository: MarketplaceListingRepository,
    private val userCardRepository: UserCardRepository,
    private val fantasyTeamCardRepository: FantasyTeamCardRepository,
    private val userCardOwnershipHistoryRepository: UserCardOwnershipHistoryRepository,
    private val fantikiTransactionRepository: FantikiTransactionRepository,
    private val userService: UserService,
    private val telegramUserRepository: TelegramUserRepository,
    private val applicationEventPublisher: ApplicationEventPublisher,
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
        val out = ArrayList<PairAnalysisDto>()
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
                PairAnalysisDto(
                    userATelegramId = uA.telegramId,
                    userBTelegramId = uB.telegramId,
                    tradesAtoB = countAtoB,
                    tradesTotalAtoB = grossAtoB,
                    tradesBtoA = countBtoA,
                    tradesTotalBtoA = grossBtoA,
                    netTransfer = netTransfer,
                    bidirectional = countAtoB > 0 && countBtoA > 0,
                ),
            )
        }
        return out.sortedWith(compareByDescending<PairAnalysisDto> { it.tradesAtoB + it.tradesBtoA }
            .thenByDescending { it.tradesTotalAtoB + it.tradesTotalBtoA })
    }

    @Transactional(readOnly = true)
    fun getPairTrades(telegramIdA: Long, telegramIdB: Long): PairTradesResultDto {
        if (telegramIdA == telegramIdB) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "userA and userB must be different")
        }
        val userA = telegramUserRepository.findByTelegramId(telegramIdA)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User A not found")
        val userB = telegramUserRepository.findByTelegramId(telegramIdB)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User B not found")
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

    @Transactional
    fun banPair(request: BanPairRequest): BanPairResultDto {
        val tga = request.telegramIdA ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "telegramIdA is required")
        val tgb = request.telegramIdB ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "telegramIdB is required")
        val reason = request.reason?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "reason is required")
        if (tga == tgb) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "telegramIdA and telegramIdB must be different")
        }
        val userA = telegramUserRepository.findByTelegramId(tga)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User A not found")
        val userB = telegramUserRepository.findByTelegramId(tgb)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User B not found")
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
    fun unban(telegramId: Long) {
        val user = telegramUserRepository.findByTelegramId(telegramId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")
        user.marketplaceBanned = false
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
