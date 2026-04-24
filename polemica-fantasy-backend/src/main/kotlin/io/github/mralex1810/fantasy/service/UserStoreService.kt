package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.dto.user.response.BuyPackResponseDto
import io.github.mralex1810.fantasy.dto.user.response.StorePackItemDto
import io.github.mralex1810.fantasy.dto.user.response.StorePackRaritySlotDto
import io.github.mralex1810.fantasy.entity.FantikiTransactionReason
import io.github.mralex1810.fantasy.entity.TelegramUser
import io.github.mralex1810.fantasy.repository.CardPackRepository
import io.github.mralex1810.fantasy.repository.CardTemplateRepository
import io.github.mralex1810.fantasy.repository.TelegramUserRepository
import io.github.mralex1810.fantasy.repository.UserCardPackFreeUsageRepository
import io.github.mralex1810.fantasy.repository.UserCardPackOpenCountRepository
import io.github.mralex1810.fantasy.repository.UserCardRepository
import kotlin.math.max
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class UserStoreService(
    private val cardPackRepository: CardPackRepository,
    private val cardService: CardService,
    private val userService: UserService,
    private val userCardRepository: UserCardRepository,
    private val cardTemplateRepository: CardTemplateRepository,
    private val userCardPackFreeUsageRepository: UserCardPackFreeUsageRepository,
    private val userCardPackOpenCountRepository: UserCardPackOpenCountRepository,
    private val telegramUserRepository: TelegramUserRepository,
    private val imageStorageService: ImageStorageService,
) {

    @Transactional(readOnly = true)
    fun listStorePacks(user: TelegramUser): List<StorePackItemDto> {
        val packs =
            cardPackRepository
                .findAllByActiveTrueAndPriceFantikiGreaterThanEqualOrderByIdAsc(0L)
        if (packs.isEmpty()) return emptyList()
        val internalId = user.id!!
        val packIds = packs.map { it.id!! }
        val usedByPackId =
            userCardPackFreeUsageRepository
                .findAllByTelegramUser_IdAndCardPack_IdIn(internalId, packIds)
                .associate { it.cardPack!!.id!! to it.freeOpensUsed }
        val opensByPackId =
            userCardPackOpenCountRepository
                .findAllByTelegramUser_IdAndCardPack_IdIn(internalId, packIds)
                .associate { it.cardPack!!.id!! to it.openCount }
        return packs.map { pack ->
            val used = usedByPackId[pack.id!!] ?: 0
            val freeRemaining =
                if (pack.priceFantiki <= 0L || pack.freeOpensPerUser <= 0) {
                    0
                } else {
                    max(0, pack.freeOpensPerUser - used)
                }
            val packId = pack.id!!
            val packOpensUsed = opensByPackId[packId] ?: 0
            StorePackItemDto(
                id = packId,
                name = pack.name,
                priceFantiki = pack.priceFantiki,
                freeOpensRemaining = freeRemaining,
                maxOpensPerUser = pack.maxOpensPerUser,
                packOpensUsed = packOpensUsed,
                rarityLayout =
                    pack.rarityConfigs
                        .sortedBy { it.rarity.ordinal }
                        .map { cfg ->
                            StorePackRaritySlotDto(
                                rarity = cfg.rarity,
                                cardsCount = cfg.cardsCount,
                            )
                        },
            )
        }
    }

    @Transactional
    fun buyPack(user: TelegramUser, packId: Long): BuyPackResponseDto {
        val pack =
            cardPackRepository.findById(packId).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Card pack $packId not found")
            }
        if (!pack.active) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Card pack is not active")
        }
        val internalId = user.id!!
        if (pack.maxOpensPerUser > 0) {
            // Pessimistic lock: serialize purchases for this user so the count + openPack cannot
            // race with another request (REQUIRED joins the same transaction as openPack).
            telegramUserRepository.findByIdForUpdate(internalId)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User $internalId not found")
            val opensUsed =
                userCardPackOpenCountRepository
                    .findByTelegramUser_IdAndCardPack_Id(internalId, packId)
                    ?.openCount
                    ?: 0
            if (opensUsed >= pack.maxOpensPerUser) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Pack purchase limit reached")
            }
        }
        val price = pack.priceFantiki
        if (price > 0L) {
            val limit = pack.freeOpensPerUser
            if (limit > 0) {
                userCardPackFreeUsageRepository.ensureUsageRow(internalId, pack.id!!)
                val incremented =
                    userCardPackFreeUsageRepository.incrementIfBelowLimit(internalId, pack.id!!, limit)
                if (incremented == 0) {
                    userService.deductBalance(internalId, price, FantikiTransactionReason.PACK_PURCHASE)
                }
            } else {
                userService.deductBalance(internalId, price, FantikiTransactionReason.PACK_PURCHASE)
            }
        }
        val opened = cardService.openPack(user.telegramId, packId)
        val ids = opened.userCards.map { it.id }
        val byId = userCardRepository.findAllByIdInAndTelegramUser_Id(ids, internalId).associateBy { it.id!! }
        val templateIds = byId.values.map { it.cardTemplate!!.id!! }.distinct()
        val templatesById =
            if (templateIds.isEmpty()) {
                emptyMap()
            } else {
                cardTemplateRepository.findAllByIdWithAchievementsLoaded(templateIds).associateBy { it.id!! }
            }
        val cards =
            ids.map { id ->
                val uc =
                    byId[id]
                        ?: throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Opened card $id not found")
                val tid = uc.cardTemplate!!.id!!
                val ct =
                    templatesById[tid]
                        ?: throw ResponseStatusException(
                            HttpStatus.INTERNAL_SERVER_ERROR,
                            "Card template $tid not found",
                        )
                uc.toUserCardItemDto(ct, imageStorageService)
            }
        val balance = userService.getBalance(internalId)
        return BuyPackResponseDto(fantiki = balance, cards = cards)
    }
}
