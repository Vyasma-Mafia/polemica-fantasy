package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.dto.user.response.BuyPackResponseDto
import io.github.mralex1810.fantasy.dto.user.response.StorePackItemDto
import io.github.mralex1810.fantasy.dto.user.response.StorePackRaritySlotDto
import io.github.mralex1810.fantasy.entity.FantikiTransactionReason
import io.github.mralex1810.fantasy.entity.TelegramUser
import io.github.mralex1810.fantasy.repository.CardPackRepository
import io.github.mralex1810.fantasy.repository.CardTemplateRepository
import io.github.mralex1810.fantasy.repository.UserCardRepository
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
) {

    @Transactional(readOnly = true)
    fun listStorePacks(): List<StorePackItemDto> {
        return cardPackRepository
            .findAllByActiveTrueAndPriceFantikiGreaterThanEqualOrderByIdAsc(0L)
            .map { pack ->
                StorePackItemDto(
                    id = pack.id!!,
                    name = pack.name,
                    priceFantiki = pack.priceFantiki,
                    rarityLayout = pack.rarityConfigs
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
        val pack = cardPackRepository.findById(packId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Card pack $packId not found")
        }
        if (!pack.active) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Card pack is not active")
        }
        val internalId = user.id!!
        userService.deductBalance(internalId, pack.priceFantiki, FantikiTransactionReason.PACK_PURCHASE)
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
        val cards = ids.map { id ->
            val uc = byId[id]
                ?: throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Opened card $id not found")
            val tid = uc.cardTemplate!!.id!!
            val ct = templatesById[tid]
                ?: throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Card template $tid not found")
            uc.toUserCardItemDto(ct)
        }
        val balance = userService.getBalance(internalId)
        return BuyPackResponseDto(fantiki = balance, cards = cards)
    }
}
