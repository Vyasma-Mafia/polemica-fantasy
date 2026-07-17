package io.github.mralex1810.fantasy.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.mralex1810.fantasy.dto.user.request.CardMergeConfirmRequest
import io.github.mralex1810.fantasy.dto.user.request.CardMergePreviewRequest
import io.github.mralex1810.fantasy.dto.user.response.ActiveMarketplaceListingBriefDto
import io.github.mralex1810.fantasy.dto.user.response.CardMergeBlockReason
import io.github.mralex1810.fantasy.dto.user.response.CardMergeConfirmResponseDto
import io.github.mralex1810.fantasy.dto.user.response.CardMergeMaterialDto
import io.github.mralex1810.fantasy.dto.user.response.CardMergeOperationOptionDto
import io.github.mralex1810.fantasy.dto.user.response.CardMergeOptionsDto
import io.github.mralex1810.fantasy.dto.user.response.CardMergePlayerGroupDto
import io.github.mralex1810.fantasy.dto.user.response.CardMergePreviewResponseDto
import io.github.mralex1810.fantasy.dto.user.response.CardMergeResultSummaryDto
import io.github.mralex1810.fantasy.dto.user.response.CardMergeWarningDto
import io.github.mralex1810.fantasy.dto.user.response.PerkCatalogItemDto
import io.github.mralex1810.fantasy.entity.CardAcquisitionType
import io.github.mralex1810.fantasy.entity.CardMergeOperation
import io.github.mralex1810.fantasy.entity.MarketplaceListingStatus
import io.github.mralex1810.fantasy.entity.Perk
import io.github.mralex1810.fantasy.entity.Rarity
import io.github.mralex1810.fantasy.entity.TelegramUser
import io.github.mralex1810.fantasy.entity.UserCard
import io.github.mralex1810.fantasy.entity.UserCardMerge
import io.github.mralex1810.fantasy.entity.UserCardMergeInput
import io.github.mralex1810.fantasy.entity.UserCardMergePreview
import io.github.mralex1810.fantasy.event.AchievementProgressEvent
import io.github.mralex1810.fantasy.event.AchievementProgressEventType
import io.github.mralex1810.fantasy.repository.CardTemplateRepository
import io.github.mralex1810.fantasy.repository.FantasyTeamCardRepository
import io.github.mralex1810.fantasy.repository.MarketplaceListingRepository
import io.github.mralex1810.fantasy.repository.PerkRepository
import io.github.mralex1810.fantasy.repository.UserCardMergeInputRepository
import io.github.mralex1810.fantasy.repository.UserCardMergePreviewRepository
import io.github.mralex1810.fantasy.repository.UserCardMergeRepository
import io.github.mralex1810.fantasy.repository.UserCardRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.security.MessageDigest
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
class CardMergeService(
    private val userCardRepository: UserCardRepository,
    private val fantasyTeamCardRepository: FantasyTeamCardRepository,
    private val marketplaceListingRepository: MarketplaceListingRepository,
    private val perkRepository: PerkRepository,
    private val cardPackService: CardPackService,
    private val cardTemplateRepository: CardTemplateRepository,
    private val userCardMergePreviewRepository: UserCardMergePreviewRepository,
    private val userCardMergeRepository: UserCardMergeRepository,
    private val userCardMergeInputRepository: UserCardMergeInputRepository,
    private val userCardOwnershipService: UserCardOwnershipService,
    private val economyConfigService: EconomyConfigService,
    private val userService: UserService,
    private val imageStorageService: ImageStorageService,
    private val cardValueService: CardValueService,
    private val productEventService: ProductEventService,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val objectMapper: ObjectMapper,
) {

    @Transactional(readOnly = true)
    fun getOptions(user: TelegramUser): CardMergeOptionsDto {
        val cards = userCardRepository.findAllMergeCandidatesByUserId(
            telegramUserId = user.id!!,
            rarities = listOf(Rarity.COMMON, Rarity.RARE),
        )
        if (cards.isEmpty()) return CardMergeOptionsDto(emptyList())

        val cardIds = cards.map { it.id!! }
        val activeTeamIds = fantasyTeamCardRepository.countReservedLeaguesByUserCardIds(cardIds)
            .map { (it[0] as Number).toLong() }
            .toSet()
        val activeListings = marketplaceListingRepository
            .findAllByUserCard_IdInAndStatus(cardIds, MarketplaceListingStatus.ACTIVE)
            .associateBy { it.userCard!!.id!! }

        val groups = cards
            .groupBy { it.cardTemplate!!.fantasyPlayer!!.id!! }
            .map { (_, playerCards) ->
                val fp = playerCards.first().cardTemplate!!.fantasyPlayer!!
                val operations = CardMergeOperation.entries.mapNotNull { operation ->
                    val sourceCards = playerCards.filter { it.cardTemplate!!.rarity == operation.sourceRarity }
                    if (sourceCards.isEmpty()) return@mapNotNull null
                    val materials = sourceCards.map { card ->
                        val listing = activeListings[card.id!!]
                        val blockReason = blockReason(card, activeTeamIds, listing != null)
                        CardMergeMaterialDto(
                            userCard = card.toUserCardItemDto(
                                imageStorage = imageStorageService,
                                cardValueService = cardValueService,
                                activeMarketplaceListing = listing?.let {
                                    ActiveMarketplaceListingBriefDto(it.id!!, it.price)
                                },
                            ),
                            blockReason = blockReason,
                            listingId = listing?.id,
                            canCancelListing = listing != null,
                        )
                    }
                    val available = materials.filter { it.blockReason == null }
                    CardMergeOperationOptionDto(
                        operation = operation,
                        sourceRarity = operation.sourceRarity,
                        resultRarity = operation.resultRarity,
                        availableCards = available,
                        blockedCards = materials.filter { it.blockReason != null },
                        eligible = available.size >= REQUIRED_INPUT_CARDS,
                    )
                }
                CardMergePlayerGroupDto(
                    fantasyPlayerId = fp.id!!,
                    nickname = fp.nickname,
                    photoUrl = imageStorageService.publicObjectUrl(fp.photoUrl),
                    operations = operations,
                )
            }
            .filter { it.operations.isNotEmpty() }
            .sortedWith(
                compareByDescending<CardMergePlayerGroupDto> {
                    it.operations.any { op -> op.operation == CardMergeOperation.RARE_TO_EPIC && op.eligible }
                }.thenByDescending {
                    it.operations.any { op -> op.operation == CardMergeOperation.COMMON_TO_RARE && op.eligible }
                }.thenBy { it.nickname.lowercase() },
            )
        return CardMergeOptionsDto(groups)
    }

    @Transactional
    @Synchronized
    fun preview(user: TelegramUser, request: CardMergePreviewRequest): CardMergePreviewResponseDto {
        val inputIds = normalizeInputIds(request.inputUserCardIds)
        val cards = loadAndValidateInputCards(user.id!!, request.operation, inputIds)
        val selectedSkinSourceUserCardId = resolveSelectedSkinSourceUserCardId(cards, request.selectedSkinSourceUserCardId)
        val inputSetHash = inputSetHash(inputIds)
        val now = Instant.now()
        val expiresAt = now.plus(PREVIEW_TTL_MINUTES, ChronoUnit.MINUTES)

        val existing = userCardMergePreviewRepository
            .findByTelegramUser_IdAndOperationAndInputSetHashAndConsumedAtIsNull(
                user.id!!,
                request.operation,
                inputSetHash,
            )
        val preview: UserCardMergePreview
        val reused: Boolean
        if (existing != null) {
            existing.selectedSkinSourceUserCardId = selectedSkinSourceUserCardId
            existing.expiresAt = expiresAt
            existing.updatedAt = now
            preview = userCardMergePreviewRepository.save(existing)
            reused = true
        } else {
            val perkPlan = buildPreviewPerkPlan(request.operation, cards)
            preview = userCardMergePreviewRepository.save(
                UserCardMergePreview(
                    telegramUser = user,
                    operation = request.operation,
                    inputUserCardIds = objectMapper.valueToTree(inputIds),
                    inputSetHash = inputSetHash,
                    fixedPerkIds = objectMapper.valueToTree(perkPlan.fixedPerkIds),
                    offeredPerkIds = objectMapper.valueToTree(perkPlan.offeredPerkIds),
                    selectedSkinSourceUserCardId = selectedSkinSourceUserCardId,
                    expiresAt = expiresAt,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            reused = false
        }
        recordPreviewEvent(user.id!!, preview, cards, reused)
        return buildPreviewResponse(preview, cards, reused)
    }

    @Transactional
    fun confirm(user: TelegramUser, request: CardMergeConfirmRequest): CardMergeConfirmResponseDto {
        val inputIds = normalizeInputIds(request.inputUserCardIds)
        val preview = userCardMergePreviewRepository.findByIdAndTelegramUser_IdForUpdate(request.previewId, user.id!!)
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Preview not found")

        if (preview.consumedAt != null) {
            return confirmConsumedPreview(user, preview, request, inputIds)
        }

        if (preview.operation != request.operation || jsonLongList(preview.inputUserCardIds) != inputIds) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Preview does not match merge request")
        }
        if (preview.expiresAt.isBefore(Instant.now())) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Preview expired")
        }

        val lockedCards = userCardRepository.findAllByIdInAndTelegramUser_IdForUpdate(inputIds, user.id!!)
        val cards = lockedCards.sortedBy { it.id!! }
        validateInputCards(request.operation, inputIds, cards)
        val selectedSkinSourceUserCardId = resolveSelectedSkinSourceUserCardId(cards, request.selectedSkinSourceUserCardId)
        if (selectedSkinSourceUserCardId != preview.selectedSkinSourceUserCardId) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Preview does not match selected skin")
        }

        val selectedPerkIds = resolveSelectedPerkIds(
            operation = request.operation,
            fixedPerkIds = jsonStringList(preview.fixedPerkIds),
            offeredPerkIds = jsonStringList(preview.offeredPerkIds),
            selectedPerkIds = request.selectedPerkIds,
        )
        val selectedPerks = loadPerks(selectedPerkIds)

        val now = Instant.now()
        val fantasyPlayer = cards.first().cardTemplate!!.fantasyPlayer!!
        val resultTemplate = cardPackService.findOrCreateCardTemplateForPerks(
            fantasyPlayer = fantasyPlayer,
            rarity = request.operation.resultRarity,
            perks = selectedPerks,
        )
        val resultUses = resultUsesRemaining(request.operation.resultRarity, cards)
        val resultTimesRenewed = cards.maxOf { it.timesRenewed }
        val selectedSkin = selectedSkinSourceUserCardId?.let { skinSourceId ->
            cards.first { it.id == skinSourceId }.cardSkin
        }

        val resultCard = userCardRepository.save(
            UserCard(
                telegramUser = user,
                cardTemplate = resultTemplate,
                sourceCardPack = null,
                cardSkin = selectedSkin,
                craftedBy = user,
                acquiredAt = now,
                usesRemaining = resultUses,
                timesRenewed = resultTimesRenewed,
            ),
        )
        cards.forEach { it.deletedAt = now }
        userCardRepository.saveAll(cards)

        val merge = userCardMergeRepository.save(
            UserCardMerge(
                telegramUser = user,
                preview = preview,
                resultUserCard = resultCard,
                operation = request.operation,
                sourceRarity = request.operation.sourceRarity,
                resultRarity = request.operation.resultRarity,
                fantasyPlayer = fantasyPlayer,
                selectedPerkIds = objectMapper.valueToTree(selectedPerkIds),
                fixedPerkIds = preview.fixedPerkIds,
                offeredPerkIds = preview.offeredPerkIds,
                selectedSkinSourceUserCardId = selectedSkinSourceUserCardId,
                resultSkinCode = selectedSkin?.code,
                costFantiki = 0L,
                createdAt = now,
            ),
        )
        cards.forEach { card ->
            val input = userCardMergeInputRepository.save(
                UserCardMergeInput(
                    merge = merge,
                    inputUserCard = card,
                    inputCardTemplate = card.cardTemplate,
                    inputRarity = card.cardTemplate!!.rarity,
                    inputPerkIds = objectMapper.valueToTree(cardPerkIds(card)),
                    inputUsesRemaining = card.usesRemaining,
                    inputTimesRenewed = card.timesRenewed,
                    inputSkinCode = card.cardSkin?.code,
                ),
            )
            merge.inputs.add(input)
        }
        preview.consumedAt = now
        preview.resultUserCard = resultCard
        preview.updatedAt = now
        userCardMergePreviewRepository.save(preview)

        userCardOwnershipService.recordAcquisition(
            userCard = resultCard,
            telegramUser = user,
            acquisitionType = CardAcquisitionType.CARD_MERGE,
            acquiredAt = now,
        )
        recordConfirmEvent(user.id!!, merge, cards)
        applicationEventPublisher.publishEvent(
            AchievementProgressEvent(AchievementProgressEventType.COLLECTION_CHANGED, setOf(user.id!!)),
        )

        return buildConfirmResponse(user.id!!, resultCard.id!!)
    }

    private fun confirmConsumedPreview(
        user: TelegramUser,
        preview: UserCardMergePreview,
        request: CardMergeConfirmRequest,
        inputIds: List<Long>,
    ): CardMergeConfirmResponseDto {
        if (preview.operation != request.operation || jsonLongList(preview.inputUserCardIds) != inputIds) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Preview already consumed")
        }
        if (preview.selectedSkinSourceUserCardId != request.selectedSkinSourceUserCardId) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Preview already consumed")
        }
        val merge = userCardMergeRepository.findByPreview_Id(preview.id!!)
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Preview already consumed")
        val selectedPerkIds = resolveSelectedPerkIds(
            operation = request.operation,
            fixedPerkIds = jsonStringList(preview.fixedPerkIds),
            offeredPerkIds = jsonStringList(preview.offeredPerkIds),
            selectedPerkIds = request.selectedPerkIds,
        )
        if (selectedPerkIds.toSet() != jsonStringList(merge.selectedPerkIds).toSet()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Preview already consumed")
        }
        return buildConfirmResponse(user.id!!, merge.resultUserCard!!.id!!)
    }

    private fun buildConfirmResponse(internalUserId: Long, resultUserCardId: Long): CardMergeConfirmResponseDto {
        val fresh = userCardRepository.findByIdAndTelegramUser_IdWithTemplatePerks(resultUserCardId, internalUserId)
            ?: throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to reload merged card")
        val template = cardTemplateRepository.findAllByIdWithPerksLoaded(listOf(fresh.cardTemplate!!.id!!))
            .firstOrNull() ?: fresh.cardTemplate!!
        return CardMergeConfirmResponseDto(
            card = fresh.toUserCardItemDto(template, imageStorageService, cardValueService),
            spentFantiki = 0L,
            newBalance = userService.getBalance(internalUserId),
        )
    }

    private fun loadAndValidateInputCards(
        internalUserId: Long,
        operation: CardMergeOperation,
        inputIds: List<Long>,
    ): List<UserCard> {
        val cards = userCardRepository.findAllByIdInAndTelegramUser_Id(inputIds, internalUserId)
            .sortedBy { it.id!! }
        validateInputCards(operation, inputIds, cards)
        return cards
    }

    private fun validateInputCards(
        operation: CardMergeOperation,
        inputIds: List<Long>,
        cards: List<UserCard>,
    ) {
        if (cards.size != REQUIRED_INPUT_CARDS || cards.map { it.id!! }.toSet() != inputIds.toSet()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Card not found or not owned")
        }
        val rarities = cards.map { it.cardTemplate!!.rarity }.toSet()
        if (rarities.size != 1) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Cards must have the same rarity")
        }
        val sourceRarity = rarities.single()
        if (sourceRarity !in setOf(Rarity.COMMON, Rarity.RARE)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Only COMMON and RARE cards can be merged")
        }
        if (sourceRarity != operation.sourceRarity) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Only COMMON and RARE cards can be merged")
        }
        if (cards.map { it.cardTemplate!!.fantasyPlayer!!.id!! }.toSet().size != 1) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Cards must belong to the same player")
        }
        cards.forEach { card ->
            if (userCardRepository.isPeriodicRatingRewardCard(card.id!!)) {
                throw ResponseStatusException(HttpStatus.CONFLICT, "Periodic rating trophy cards cannot be used as merge inputs")
            }
            if (fantasyTeamCardRepository.countInNonFinalizedSeries(card.id!!) > 0) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot merge a card in an active team")
            }
            if (marketplaceListingRepository.existsByUserCard_IdAndStatus(card.id!!, MarketplaceListingStatus.ACTIVE)) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot merge a card listed on the marketplace")
            }
            if (card.usesRemaining <= 0) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Only cards with remaining uses can be merged")
            }
        }
    }

    private fun buildPreviewPerkPlan(operation: CardMergeOperation, cards: List<UserCard>): PreviewPerkPlan =
        when (operation) {
            CardMergeOperation.COMMON_TO_RARE -> {
                val offered = randomPerks().map { it.id }
                if (offered.isEmpty()) {
                    throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No random perks configured for card merge")
                }
                PreviewPerkPlan(emptyList(), offered)
            }
            CardMergeOperation.RARE_TO_EPIC -> {
                val sourcePerkIds = cards.map { card ->
                    val ids = cardPerkIds(card).distinct()
                    if (ids.size != 1) {
                        throw ResponseStatusException(HttpStatus.BAD_REQUEST, "RARE cards must have exactly one perk")
                    }
                    ids.single()
                }
                val unique = sourcePerkIds.distinct().sorted()
                when (unique.size) {
                    1 -> {
                        val offered = randomPerks(excludeIds = unique.toSet()).map { it.id }
                        if (offered.isEmpty()) {
                            throw ResponseStatusException(
                                HttpStatus.INTERNAL_SERVER_ERROR,
                                "No random perks configured for card merge",
                            )
                        }
                        PreviewPerkPlan(unique, offered)
                    }
                    2 -> PreviewPerkPlan(unique, emptyList())
                    else -> PreviewPerkPlan(emptyList(), unique)
                }
            }
        }

    private fun buildPreviewResponse(
        preview: UserCardMergePreview,
        cards: List<UserCard>,
        reused: Boolean,
    ): CardMergePreviewResponseDto {
        val operation = preview.operation
        val fixedPerkIds = jsonStringList(preview.fixedPerkIds)
        val offeredPerkIds = jsonStringList(preview.offeredPerkIds)
        val selectedSkinSourceUserCardId = preview.selectedSkinSourceUserCardId
        val skinCode = selectedSkinSourceUserCardId?.let { skinSourceId ->
            cards.firstOrNull { it.id == skinSourceId }?.cardSkin?.code
        }
        val burnedSkins = cards
            .mapNotNull { card -> card.cardSkin?.code?.takeIf { card.id != selectedSkinSourceUserCardId } }
        val resultUses = resultUsesRemaining(operation.resultRarity, cards)
        val baseUses = economyConfigService.getUsesForRarity(operation.resultRarity)
        val resultTimesRenewed = cards.maxOf { it.timesRenewed }
        val maxRenewals = economyConfigService.getMaxRenewals()
        val valueBefore = cards.sumOf { cardValueService.calculateValue(it.cardTemplate!!) }
        val resultPerkCount = when (operation.resultRarity) {
            Rarity.RARE -> 1
            Rarity.EPIC -> 2
            else -> 0
        }
        val valueAfter = cardValueService.calculateValue(operation.resultRarity, resultPerkCount)
        val selectablePerks = perkRepository.findAllByIdIn(offeredPerkIds).associateBy { it.id }
        return CardMergePreviewResponseDto(
            operation = operation,
            previewId = preview.id!!,
            expiresAt = preview.expiresAt,
            sameRollForInputSet = reused,
            fixedPerkIds = fixedPerkIds,
            selectablePerks = offeredPerkIds.mapNotNull { selectablePerks[it]?.toCatalogDto() },
            requiredSelections = requiredSelections(operation, fixedPerkIds),
            result = CardMergeResultSummaryDto(
                fantasyPlayerId = cards.first().cardTemplate!!.fantasyPlayer!!.id!!,
                rarity = operation.resultRarity,
                usesRemaining = resultUses,
                baseUses = baseUses,
                timesRenewed = resultTimesRenewed,
                maxRenewals = maxRenewals,
                skinCode = skinCode,
                marketplaceAvailable = resultTimesRenewed < maxRenewals && resultUses > 0,
                marketplaceHint = if (resultTimesRenewed < maxRenewals && resultUses > 0) {
                    "Можно будет выставить на маркетплейс"
                } else {
                    "Нельзя будет выставить: лимит переподписаний или истёкший контракт"
                },
            ),
            valueBefore = valueBefore,
            valueAfter = valueAfter,
            warnings = buildWarnings(valueBefore, valueAfter, resultUses, resultTimesRenewed, operation.resultRarity),
            materials = cards.map { card ->
                CardMergeMaterialDto(
                    userCard = card.toUserCardItemDto(
                        imageStorage = imageStorageService,
                        cardValueService = cardValueService,
                    ),
                )
            },
            burnedSkins = burnedSkins,
        )
    }

    private fun buildWarnings(
        valueBefore: Long,
        valueAfter: Long,
        resultUses: Int,
        resultTimesRenewed: Int,
        resultRarity: Rarity,
    ): List<CardMergeWarningDto> {
        val warnings = mutableListOf<CardMergeWarningDto>()
        if (valueAfter < valueBefore) {
            warnings.add(
                CardMergeWarningDto(
                    code = "PORTFOLIO_VALUE_DECREASE",
                    message = "Ценность коллекции уменьшится: $valueBefore -> $valueAfter",
                ),
            )
        }
        val baseUses = economyConfigService.getUsesForRarity(resultRarity)
        if (resultUses < baseUses) {
            warnings.add(
                CardMergeWarningDto(
                    code = "LOW_CONTRACT_USES",
                    message = "Контракт результата будет короче базового: $resultUses из $baseUses",
                ),
            )
        }
        val maxRenewals = economyConfigService.getMaxRenewals()
        if (resultTimesRenewed >= maxRenewals) {
            warnings.add(
                CardMergeWarningDto(
                    code = "MAX_RENEWALS_REACHED",
                    message = "Результат наследует лимит переподписаний: $resultTimesRenewed/$maxRenewals",
                ),
            )
        }
        return warnings
    }

    private fun resolveSelectedPerkIds(
        operation: CardMergeOperation,
        fixedPerkIds: List<String>,
        offeredPerkIds: List<String>,
        selectedPerkIds: List<String>,
    ): List<String> {
        val requiredSelections = requiredSelections(operation, fixedPerkIds)
        val fixedSet = fixedPerkIds.toSet()
        val offeredSet = offeredPerkIds.toSet()
        val selected = selectedPerkIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (requiredSelections == 0) {
            if (selected.isNotEmpty() && selected.toSet() != fixedSet) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid perk selection")
            }
            return fixedPerkIds
        }
        val additions = when {
            selected.size == requiredSelections && selected.none { it in fixedSet } -> selected
            selected.size == requiredSelections + fixedPerkIds.size && fixedSet.all { it in selected } ->
                selected.filter { it !in fixedSet }
            fixedPerkIds.isEmpty() && selected.size == requiredSelections -> selected
            else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid perk selection")
        }
        if (additions.size != requiredSelections || additions.any { it !in offeredSet }) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid perk selection")
        }
        val result = (fixedPerkIds + additions).distinct()
        val expectedCount = when (operation.resultRarity) {
            Rarity.RARE -> 1
            Rarity.EPIC -> 2
            else -> 0
        }
        if (result.size != expectedCount) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid perk selection")
        }
        return result
    }

    private fun requiredSelections(operation: CardMergeOperation, fixedPerkIds: List<String>): Int =
        when (operation) {
            CardMergeOperation.COMMON_TO_RARE -> 1
            CardMergeOperation.RARE_TO_EPIC -> when (fixedPerkIds.size) {
                0 -> 2
                1 -> 1
                else -> 0
            }
        }

    private fun loadPerks(selectedPerkIds: List<String>): List<Perk> {
        val found = perkRepository.findAllByIdIn(selectedPerkIds).associateBy { it.id }
        val missing = selectedPerkIds.filter { it !in found.keys }
        if (missing.isNotEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown perk ids: $missing")
        }
        return selectedPerkIds.map { found.getValue(it) }
    }

    private fun randomPerks(excludeIds: Set<String> = emptySet()): List<Perk> =
        perkRepository.findAllByCanAppearOnRandomCardsTrueOrderById()
            .filter { it.id !in excludeIds }
            .shuffled()
            .take(MAX_ROLLED_PERKS)

    private fun resolveSelectedSkinSourceUserCardId(cards: List<UserCard>, requestedId: Long?): Long? {
        val skinnedIds = cards.filter { it.cardSkin != null }.map { it.id!! }
        if (skinnedIds.isEmpty()) return null
        if (skinnedIds.size == 1) {
            return skinnedIds.single()
        }
        if (requestedId == null || requestedId !in skinnedIds) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "selectedSkinSourceUserCardId must reference one of input cards with skin",
            )
        }
        return requestedId
    }

    private fun resultUsesRemaining(resultRarity: Rarity, cards: List<UserCard>): Int =
        minOf(economyConfigService.getUsesForRarity(resultRarity), cards.sumOf { it.usesRemaining })

    private fun blockReason(card: UserCard, activeTeamIds: Set<Long>, hasActiveListing: Boolean): CardMergeBlockReason? =
        when {
            userCardRepository.isPeriodicRatingRewardCard(card.id!!) -> CardMergeBlockReason.PERIODIC_RATING_TROPHY
            card.id!! in activeTeamIds -> CardMergeBlockReason.ACTIVE_TEAM
            hasActiveListing -> CardMergeBlockReason.MARKETPLACE_ACTIVE
            card.usesRemaining <= 0 -> CardMergeBlockReason.EXPIRED_CONTRACT
            else -> null
        }

    private fun cardPerkIds(card: UserCard): List<String> =
        card.cardTemplate!!.perks.distinctBy { it.perk!!.id }.map { it.perk!!.id }.sorted()

    private fun normalizeInputIds(ids: List<Long>): List<Long> {
        if (ids.size != REQUIRED_INPUT_CARDS || ids.toSet().size != REQUIRED_INPUT_CARDS) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Exactly 3 unique inputUserCardIds are required")
        }
        return ids.sorted()
    }

    private fun inputSetHash(inputIds: List<Long>): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(inputIds.joinToString(",").toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun jsonStringList(node: JsonNode?): List<String> =
        node?.map { it.asText() } ?: emptyList()

    private fun jsonLongList(node: JsonNode?): List<Long> =
        node?.map { it.asLong() } ?: emptyList()

    private fun Perk.toCatalogDto(): PerkCatalogItemDto =
        PerkCatalogItemDto(
            id = id,
            name = name,
            description = description,
            bonusPoints = bonusPoints,
            occurrenceType = occurrenceType,
            applicableRoles = applicableRoles.map { it.role }.sorted(),
            canAppearOnRandomCards = canAppearOnRandomCards,
        )

    private fun recordPreviewEvent(
        internalUserId: Long,
        preview: UserCardMergePreview,
        cards: List<UserCard>,
        reused: Boolean,
    ) {
        productEventService.record(
            userId = internalUserId,
            eventType = if (reused) "CARD_MERGE_PREVIEW_REUSED" else "CARD_MERGE_PREVIEW_CREATED",
            subjectType = "CARD_MERGE_PREVIEW",
            subjectId = preview.id!!,
            metadata = mergeMetadata(
                operation = preview.operation,
                fantasyPlayerId = cards.first().cardTemplate!!.fantasyPlayer!!.id!!,
                inputUserCardIds = cards.map { it.id!! },
                inputUsesSum = cards.sumOf { it.usesRemaining },
                inputMaxTimesRenewed = cards.maxOf { it.timesRenewed },
                resultUsesRemaining = resultUsesRemaining(preview.operation.resultRarity, cards),
                resultTimesRenewed = cards.maxOf { it.timesRenewed },
                selectedPerkIds = emptyList(),
                offeredPerkIds = jsonStringList(preview.offeredPerkIds),
                skinTransferred = preview.selectedSkinSourceUserCardId != null,
                skinsBurnedCount = cards.count { it.cardSkin != null && it.id != preview.selectedSkinSourceUserCardId },
                valueBefore = cards.sumOf { cardValueService.calculateValue(it.cardTemplate!!) },
                valueAfter = cardValueService.calculateValue(
                    preview.operation.resultRarity,
                    if (preview.operation.resultRarity == Rarity.RARE) 1 else 2,
                ),
            ),
        )
    }

    private fun recordConfirmEvent(internalUserId: Long, merge: UserCardMerge, inputCards: List<UserCard>) {
        productEventService.record(
            userId = internalUserId,
            eventType = "CARD_MERGE_CONFIRMED",
            subjectType = "CARD_MERGE",
            subjectId = merge.id!!,
            metadata = mergeMetadata(
                operation = merge.operation,
                fantasyPlayerId = merge.fantasyPlayer!!.id!!,
                inputUserCardIds = inputCards.map { it.id!! },
                inputUsesSum = inputCards.sumOf { it.usesRemaining },
                inputMaxTimesRenewed = inputCards.maxOf { it.timesRenewed },
                resultUsesRemaining = merge.resultUserCard!!.usesRemaining,
                resultTimesRenewed = merge.resultUserCard!!.timesRenewed,
                selectedPerkIds = jsonStringList(merge.selectedPerkIds),
                offeredPerkIds = jsonStringList(merge.offeredPerkIds),
                skinTransferred = merge.resultSkinCode != null,
                skinsBurnedCount = inputCards.count { it.cardSkin != null && it.id != merge.selectedSkinSourceUserCardId },
                valueBefore = inputCards.sumOf { cardValueService.calculateValue(it.cardTemplate!!) },
                valueAfter = cardValueService.calculateValue(merge.resultUserCard!!.cardTemplate!!),
            ),
        )
    }

    private fun mergeMetadata(
        operation: CardMergeOperation,
        fantasyPlayerId: Long,
        inputUserCardIds: List<Long>,
        inputUsesSum: Int,
        inputMaxTimesRenewed: Int,
        resultUsesRemaining: Int,
        resultTimesRenewed: Int,
        selectedPerkIds: List<String>,
        offeredPerkIds: List<String>,
        skinTransferred: Boolean,
        skinsBurnedCount: Int,
        valueBefore: Long,
        valueAfter: Long,
    ): JsonNode =
        objectMapper.valueToTree(
            mapOf(
                "operation" to operation.name,
                "sourceRarity" to operation.sourceRarity.name,
                "resultRarity" to operation.resultRarity.name,
                "fantasyPlayerId" to fantasyPlayerId,
                "inputUserCardIds" to inputUserCardIds,
                "inputUsesSum" to inputUsesSum,
                "resultUsesRemaining" to resultUsesRemaining,
                "inputMaxTimesRenewed" to inputMaxTimesRenewed,
                "resultTimesRenewed" to resultTimesRenewed,
                "selectedPerkIds" to selectedPerkIds,
                "offeredPerkIds" to offeredPerkIds,
                "skinTransferred" to skinTransferred,
                "skinsBurnedCount" to skinsBurnedCount,
                "costFantiki" to 0L,
                "valueBefore" to valueBefore,
                "valueAfter" to valueAfter,
            ),
        )

    private data class PreviewPerkPlan(
        val fixedPerkIds: List<String>,
        val offeredPerkIds: List<String>,
    )

    companion object {
        private const val REQUIRED_INPUT_CARDS = 3
        private const val MAX_ROLLED_PERKS = 3
        private const val PREVIEW_TTL_MINUTES = 15L
    }
}
