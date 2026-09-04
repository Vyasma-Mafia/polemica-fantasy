package io.github.mralex1810.fantasy.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.mralex1810.fantasy.config.EasterEggProperties
import io.github.mralex1810.fantasy.dto.user.response.BuyPackResponseKind
import io.github.mralex1810.fantasy.dto.user.response.BuyPackResponseDto
import io.github.mralex1810.fantasy.dto.user.response.CardPerkBriefDto
import io.github.mralex1810.fantasy.dto.user.response.PackChoiceCardDto
import io.github.mralex1810.fantasy.dto.user.response.PackChoiceDto
import io.github.mralex1810.fantasy.dto.user.response.PackChoiceOptionDto
import io.github.mralex1810.fantasy.dto.user.response.PackOpeningCardDto
import io.github.mralex1810.fantasy.dto.user.response.StorePackItemDto
import io.github.mralex1810.fantasy.dto.user.response.StorePackRaritySlotDto
import io.github.mralex1810.fantasy.dto.user.response.UserCardItemDto
import io.github.mralex1810.fantasy.entity.CardPack
import io.github.mralex1810.fantasy.entity.CardPackOpeningMode
import io.github.mralex1810.fantasy.entity.FantikiTransactionReason
import io.github.mralex1810.fantasy.entity.OnboardingStep
import io.github.mralex1810.fantasy.entity.PackPurchaseIdempotency
import io.github.mralex1810.fantasy.entity.TelegramUser
import io.github.mralex1810.fantasy.entity.UserCardPackChoice
import io.github.mralex1810.fantasy.repository.CardPackRepository
import io.github.mralex1810.fantasy.repository.CardTemplateRepository
import io.github.mralex1810.fantasy.repository.PerkRepository
import io.github.mralex1810.fantasy.repository.PackPurchaseIdempotencyRepository
import io.github.mralex1810.fantasy.repository.TelegramUserRepository
import io.github.mralex1810.fantasy.repository.UserCardPackChoiceRepository
import io.github.mralex1810.fantasy.repository.UserCardPackFreeUsageRepository
import io.github.mralex1810.fantasy.repository.UserCardPackOpenCountRepository
import io.github.mralex1810.fantasy.repository.UserCardRepository
import kotlin.math.max
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class UserStoreService(
    private val objectMapper: ObjectMapper,
    private val cardPackRepository: CardPackRepository,
    private val cardService: CardService,
    private val cardPackService: CardPackService,
    private val userService: UserService,
    private val userCardRepository: UserCardRepository,
    private val cardTemplateRepository: CardTemplateRepository,
    private val perkRepository: PerkRepository,
    private val userCardPackChoiceRepository: UserCardPackChoiceRepository,
    private val userCardPackFreeUsageRepository: UserCardPackFreeUsageRepository,
    private val userCardPackOpenCountRepository: UserCardPackOpenCountRepository,
    private val telegramUserRepository: TelegramUserRepository,
    private val imageStorageService: ImageStorageService,
    private val cardValueService: CardValueService,
    private val economyConfigService: EconomyConfigService,
    private val easterEggProperties: EasterEggProperties,
    private val onboardingService: OnboardingService,
    private val packPurchaseIdempotencyRepository: PackPurchaseIdempotencyRepository,
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
        val pendingByPackId =
            userCardPackChoiceRepository
                .findAllByTelegramUser_IdAndCardPack_IdInAndSelectedAtIsNull(internalId, packIds)
                .associateBy { it.cardPack!!.id!! }
        return packs.map { pack ->
            val used = usedByPackId[pack.id!!] ?: 0
            val freeRemaining =
                if (pack.priceFantiki <= 0L || pack.freeOpensPerUser <= 0) {
                    0
                } else {
                    max(0, pack.freeOpensPerUser - used)
            }
            val packId = pack.id!!
            val pendingChoice = pendingByPackId[packId]
            val packOpensUsed = (opensByPackId[packId] ?: 0) + if (pendingChoice != null) 1 else 0
            StorePackItemDto(
                id = packId,
                name = pack.name,
                openingMode = pack.openingMode,
                priceFantiki = pack.priceFantiki,
                freeOpensRemaining = freeRemaining,
                maxOpensPerUser = pack.maxOpensPerUser,
                packOpensUsed = packOpensUsed,
                pendingChoice = pendingChoice?.let { choiceDto(it) },
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
    fun buyPack(user: TelegramUser, packId: Long, idempotencyKey: String? = null): BuyPackResponseDto {
        val internalId = user.id!!
        val managedUser = telegramUserRepository.findByIdForUpdate(internalId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User $internalId not found")
        val normalizedKey = normalizeIdempotencyKey(idempotencyKey)
        val keyHash = normalizedKey?.let(::sha256)
        val canonicalRequestHash = canonicalPackPurchaseHash(packId)
        if (keyHash != null) {
            val existing = packPurchaseIdempotencyRepository.findByTelegramUser_IdAndKeyHash(internalId, keyHash)
            if (existing != null) {
                if (existing.canonicalRequestHash != canonicalRequestHash) {
                    throw ResponseStatusException(HttpStatus.CONFLICT, "Idempotency-Key was used for another pack purchase")
                }
                return objectMapper.readValue(existing.responseJson, BuyPackResponseDto::class.java)
            }
        }
        val pack =
            cardPackRepository.findByIdWithRarityConfigs(packId) ?: throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Card pack $packId not found",
            )
        if (!pack.active) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Card pack is not active")
        }
        val response = when (pack.openingMode) {
            CardPackOpeningMode.INSTANT -> buyInstantPack(managedUser, pack.id!!)
            CardPackOpeningMode.CHOOSE -> buyChoosePack(managedUser, pack)
        }
        if (keyHash != null) {
            packPurchaseIdempotencyRepository.save(
                PackPurchaseIdempotency(
                    telegramUser = managedUser,
                    keyHash = keyHash,
                    canonicalRequestHash = canonicalRequestHash,
                    cardPack = pack,
                    responseKind = response.kind.name,
                    balanceAfter = response.fantiki,
                    packChoiceId = response.choice?.id,
                    userCardIds = objectMapper.writeValueAsString(response.cards.map { it.id }),
                    responseJson = objectMapper.writeValueAsString(response),
                    createdAt = cardPackService.currentDbInstant(),
                ),
            )
        }
        return response
    }

    @Transactional
    fun selectPackChoice(user: TelegramUser, choiceId: Long, optionId: String): BuyPackResponseDto {
        val internalId = user.id!!
        val managedUser = telegramUserRepository.findByIdForUpdate(internalId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User $internalId not found")
        val choice = userCardPackChoiceRepository.findByIdAndTelegramUserIdForUpdate(choiceId, internalId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Pack choice $choiceId not found")
        val requestedOptionId = optionId.trim()
        if (requestedOptionId.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "optionId is required")
        }
        if (choice.selectedAt != null) {
            if (choice.selectedOptionId != requestedOptionId) {
                throw ResponseStatusException(HttpStatus.CONFLICT, "Pack choice already selected")
            }
            val ids = choice.selectedUserCardIds?.let { parseSelectedCardIds(it) }.orEmpty()
            val cards = loadUserCards(ids, internalId)
            return openedResponse(internalId, cards, grantEasterEggBonus = false)
        }
        val options = parseChoiceOptions(choice.options)
        val selected = options.firstOrNull { it.optionId == requestedOptionId }
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown choice option")
        val pack = choice.cardPack
            ?: throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Choice pack is missing")
        val now = cardPackService.currentDbInstant()
        val userCards = cardPackService.materializeDrawOption(managedUser, pack, selected, now)
        cardPackService.recordPackOpened(managedUser, pack, now)
        onboardingService.markStep(internalId, OnboardingStep.OPEN_PACK)
        choice.selectedOptionId = requestedOptionId
        choice.selectedUserCardIds = objectMapper.writeValueAsString(userCards.map { it.id!! })
        choice.selectedAt = now
        userCardPackChoiceRepository.save(choice)
        val cards = loadUserCards(userCards.map { it.id!! }, internalId)
        return openedResponse(internalId, cards, grantEasterEggBonus = true)
    }

    private fun buyInstantPack(user: TelegramUser, packId: Long): BuyPackResponseDto {
        val internalId = user.id!!
        validatePackLimitForNewPurchase(internalId, packId)
        reservePayment(internalId, cardPackRepository.findById(packId).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Card pack $packId not found")
            })
        val opened = cardService.openPack(user.telegramId, packId)
        onboardingService.markStep(internalId, OnboardingStep.OPEN_PACK)
        val cards = loadUserCards(opened.userCards.map { it.id }, internalId)
        return openedResponse(internalId, cards, grantEasterEggBonus = true)
    }

    private fun buyChoosePack(user: TelegramUser, pack: CardPack): BuyPackResponseDto {
        val internalId = user.id!!
        val existing = userCardPackChoiceRepository.findByTelegramUser_IdAndCardPack_IdAndSelectedAtIsNull(
            internalId,
            pack.id!!,
        )
        if (existing != null) {
            return pendingChoiceResponse(internalId, existing)
        }
        validatePackLimitForNewPurchase(internalId, pack.id!!)
        val payment = reservePayment(internalId, pack)
        val options = cardPackService.generateChoiceOptions(pack, CHOOSE_OPTION_COUNT)
        val now = cardPackService.currentDbInstant()
        val choice = userCardPackChoiceRepository.save(
            UserCardPackChoice(
                telegramUser = user,
                cardPack = pack,
                options = objectMapper.writeValueAsString(options),
                paymentKind = payment.kind,
                priceFantikiReserved = payment.priceFantikiReserved,
                freeUsageReserved = payment.freeUsageReserved,
                createdAt = now,
            ),
        )
        return pendingChoiceResponse(internalId, choice)
    }

    private fun loadUserCards(ids: List<Long>, internalId: Long): List<UserCardItemDto> {
        if (ids.isEmpty()) return emptyList()
        val byId = userCardRepository.findAllByIdInAndTelegramUser_Id(ids, internalId).associateBy { it.id!! }
        val templateIds = byId.values.map { it.cardTemplate!!.id!! }.distinct()
        val templatesById =
            if (templateIds.isEmpty()) {
                emptyMap()
            } else {
                cardTemplateRepository.findAllByIdWithPerksLoaded(templateIds).associateBy { it.id!! }
            }
        return ids.map { id ->
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
            uc.toUserCardItemDto(ct, imageStorageService, cardValueService)
        }
    }

    private fun openedResponse(
        internalId: Long,
        cards: List<UserCardItemDto>,
        grantEasterEggBonus: Boolean,
    ): BuyPackResponseDto {
        val openingCardsResult = buildOpeningCards(cards)
        if (grantEasterEggBonus && openingCardsResult.fantikiBonus > 0L) {
            userService.addBalance(internalId, openingCardsResult.fantikiBonus, FantikiTransactionReason.EASTER_EGG_BONUS)
        }
        val balance = userService.getBalance(internalId)
        return BuyPackResponseDto(
            kind = BuyPackResponseKind.OPENED,
            fantiki = balance,
            cards = cards,
            openingCards = openingCardsResult.openingCards,
        )
    }

    private fun pendingChoiceResponse(internalId: Long, choice: UserCardPackChoice): BuyPackResponseDto =
        BuyPackResponseDto(
            kind = BuyPackResponseKind.PENDING_CHOICE,
            fantiki = userService.getBalance(internalId),
            choice = choiceDto(choice),
        )

    private fun validatePackLimitForNewPurchase(internalId: Long, packId: Long) {
        val pack = cardPackRepository.findById(packId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Card pack $packId not found")
        }
        if (pack.maxOpensPerUser <= 0) return
        val opensUsed =
            userCardPackOpenCountRepository
                .findByTelegramUser_IdAndCardPack_Id(internalId, packId)
                ?.openCount
                ?: 0
        val hasPending =
            userCardPackChoiceRepository.findByTelegramUser_IdAndCardPack_IdAndSelectedAtIsNull(internalId, packId) != null
        val pendingOpens = if (hasPending) 1 else 0
        if (opensUsed + pendingOpens >= pack.maxOpensPerUser) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Pack purchase limit reached")
        }
    }

    private fun reservePayment(internalId: Long, pack: CardPack): PaymentReservation {
        val price = pack.priceFantiki
        if (price <= 0L) {
            return PaymentReservation(kind = "ZERO", priceFantikiReserved = 0, freeUsageReserved = false)
        }
        val limit = pack.freeOpensPerUser
        if (limit > 0) {
            userCardPackFreeUsageRepository.ensureUsageRow(internalId, pack.id!!)
            val incremented = userCardPackFreeUsageRepository.incrementIfBelowLimit(internalId, pack.id!!, limit)
            if (incremented > 0) {
                return PaymentReservation(kind = "FREE", priceFantikiReserved = 0, freeUsageReserved = true)
            }
        }
        userService.deductBalance(internalId, price, FantikiTransactionReason.PACK_PURCHASE)
        return PaymentReservation(kind = "PAID", priceFantikiReserved = price, freeUsageReserved = false)
    }

    private fun choiceDto(choice: UserCardPackChoice): PackChoiceDto {
        val pack = choice.cardPack
            ?: throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Choice pack is missing")
        val options = parseChoiceOptions(choice.options)
        return PackChoiceDto(
            id = choice.id!!,
            packId = pack.id!!,
            packName = pack.name,
            requiredCount = CHOOSE_REQUIRED_COUNT,
            options = options.map { option ->
                PackChoiceOptionDto(
                    optionId = option.optionId,
                    cards = option.cards.map { choiceCardDto(it) },
                )
            },
        )
    }

    private fun choiceCardDto(card: CardPackDrawCard): PackChoiceCardDto {
        val perks =
            if (card.perkIds.isEmpty()) {
                emptyList()
            } else {
                val byId = perkRepository.findAllByIdIn(card.perkIds).associateBy { it.id }
                card.perkIds.mapNotNull { perkId ->
                    byId[perkId]?.let { perk ->
                        CardPerkBriefDto(
                            perkId = perk.id,
                            perkName = perk.name,
                            bonusPoints = perk.bonusPoints,
                        )
                    }
                }
            }
        return PackChoiceCardDto(
            fantasyPlayerId = card.fantasyPlayerId,
            playerName = card.playerName,
            playerPhotoUrl = imageStorageService.publicObjectUrl(card.playerPhotoUrl),
            rarity = card.rarity,
            skinCode = card.skinCode,
            perks = perks,
            value = cardValueService.calculateValue(card.rarity, card.perkIds.distinct().size),
        )
    }

    private fun parseChoiceOptions(json: String): List<CardPackDrawOption> =
        objectMapper.readValue(json, object : TypeReference<List<CardPackDrawOption>>() {})

    private fun parseSelectedCardIds(json: String): List<Long> =
        objectMapper.readValue(json, object : TypeReference<List<Long>>() {})

    private fun buildOpeningCards(cards: List<UserCardItemDto>): OpeningCardsResult {
        val developerFantasyPlayerId = economyConfigService.getEasterEggDeveloperFantasyPlayerId()
        val tyulenchikImageUrl = easterEggProperties.tyulenchikImageUrl.trim().takeIf { it.isNotEmpty() }
        val openingCards = ArrayList<PackOpeningCardDto>(cards.size * 2)
        var fantikiBonus = 0L
        cards.forEach { card ->
            openingCards.add(PackOpeningCardDto.userCard(card))
            if (developerFantasyPlayerId > 0L && card.fantasyPlayerId == developerFantasyPlayerId) {
                openingCards.add(
                    PackOpeningCardDto.companion(
                        relatedUserCardId = card.id,
                        rarity = card.rarity,
                        value = card.value,
                        companionCardName = "Тюленчик",
                        companionCardImageUrl = tyulenchikImageUrl,
                    ),
                )
                fantikiBonus += card.value
            }
        }
        return OpeningCardsResult(openingCards = openingCards, fantikiBonus = fantikiBonus)
    }

    private data class OpeningCardsResult(
        val openingCards: List<PackOpeningCardDto>,
        val fantikiBonus: Long,
    )

    private data class PaymentReservation(
        val kind: String,
        val priceFantikiReserved: Long,
        val freeUsageReserved: Boolean,
    )

    private companion object {
        const val CHOOSE_OPTION_COUNT = 3
        const val CHOOSE_REQUIRED_COUNT = 1
        const val MAX_IDEMPOTENCY_KEY_LENGTH = 200
        val IDEMPOTENCY_KEY_PATTERN = Regex("^[A-Za-z0-9._:-]+$")
    }

    private fun normalizeIdempotencyKey(value: String?): String? {
        if (value == null) return null
        val normalized = value.trim()
        if (
            normalized.isEmpty() ||
            normalized.length > MAX_IDEMPOTENCY_KEY_LENGTH ||
            !IDEMPOTENCY_KEY_PATTERN.matches(normalized)
        ) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Idempotency-Key")
        }
        return normalized
    }

    private fun canonicalPackPurchaseHash(packId: Long): String = sha256("PACK_PURCHASE\n$packId")

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
