package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.dto.admin.request.CardPackRarityConfigDto
import io.github.mralex1810.fantasy.dto.admin.request.CreateCardPackRequest
import io.github.mralex1810.fantasy.dto.admin.request.UpdateCardPackRequest
import io.github.mralex1810.fantasy.dto.admin.response.CardPackDto
import io.github.mralex1810.fantasy.dto.admin.response.CardPackRarityConfigResponseDto
import io.github.mralex1810.fantasy.dto.admin.response.CardSkinDto
import io.github.mralex1810.fantasy.dto.admin.response.OpenPackResultDto
import io.github.mralex1810.fantasy.dto.admin.response.UserCardDto
import io.github.mralex1810.fantasy.entity.Perk
import io.github.mralex1810.fantasy.entity.CardAcquisitionType
import io.github.mralex1810.fantasy.entity.CardPack
import io.github.mralex1810.fantasy.entity.CardPackOpeningMode
import io.github.mralex1810.fantasy.entity.CardPackPerk
import io.github.mralex1810.fantasy.entity.CardPackPlayer
import io.github.mralex1810.fantasy.entity.CardPackRarityConfig
import io.github.mralex1810.fantasy.entity.CardTemplate
import io.github.mralex1810.fantasy.entity.CardTemplatePerk
import io.github.mralex1810.fantasy.entity.FantasyPlayer
import io.github.mralex1810.fantasy.entity.Rarity
import io.github.mralex1810.fantasy.entity.UserCard
import io.github.mralex1810.fantasy.entity.UserCardPackOpenEvent
import io.github.mralex1810.fantasy.event.AchievementProgressEvent
import io.github.mralex1810.fantasy.event.AchievementProgressEventType
import io.github.mralex1810.fantasy.repository.PerkRepository
import io.github.mralex1810.fantasy.repository.CardPackPerkRepository
import io.github.mralex1810.fantasy.repository.CardPackPlayerRepository
import io.github.mralex1810.fantasy.repository.CardPackRarityConfigRepository
import io.github.mralex1810.fantasy.repository.CardPackRepository
import io.github.mralex1810.fantasy.repository.CardSkinRepository
import io.github.mralex1810.fantasy.repository.CardTemplatePerkRepository
import io.github.mralex1810.fantasy.repository.CardTemplateRepository
import io.github.mralex1810.fantasy.repository.FantasyPlayerRepository
import io.github.mralex1810.fantasy.repository.TelegramUserRepository
import io.github.mralex1810.fantasy.repository.TournamentPlayerRepository
import io.github.mralex1810.fantasy.repository.TournamentRepository
import io.github.mralex1810.fantasy.repository.UserCardPackOpenCountRepository
import io.github.mralex1810.fantasy.repository.UserCardPackOpenEventRepository
import io.github.mralex1810.fantasy.repository.UserCardRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.Random
import java.util.UUID

data class CardPackDrawOption(
    val optionId: String,
    val cards: List<CardPackDrawCard>,
)

data class CardPackDrawCard(
    val fantasyPlayerId: Long,
    val playerName: String,
    val playerPhotoUrl: String?,
    val rarity: Rarity,
    val skinCode: String?,
    val perkIds: List<String>,
)

@Service
class CardPackService(
    private val cardPackRepository: CardPackRepository,
    private val cardPackRarityConfigRepository: CardPackRarityConfigRepository,
    private val cardPackPerkRepository: CardPackPerkRepository,
    private val cardPackPlayerRepository: CardPackPlayerRepository,
    private val cardSkinRepository: CardSkinRepository,
    private val tournamentRepository: TournamentRepository,
    private val tournamentPlayerRepository: TournamentPlayerRepository,
    private val fantasyPlayerRepository: FantasyPlayerRepository,
    private val perkRepository: PerkRepository,
    private val cardTemplateRepository: CardTemplateRepository,
    private val cardTemplatePerkRepository: CardTemplatePerkRepository,
    private val userCardRepository: UserCardRepository,
    private val userCardPackOpenCountRepository: UserCardPackOpenCountRepository,
    private val userCardPackOpenEventRepository: UserCardPackOpenEventRepository,
    private val userService: UserService,
    private val telegramUserRepository: TelegramUserRepository,
    private val economyConfigService: EconomyConfigService,
    private val userCardOwnershipService: UserCardOwnershipService,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val jdbcTemplate: JdbcTemplate,
) {

    private val random = Random()

    @Transactional(readOnly = true)
    fun listPacks(tournamentId: Long?): List<CardPackDto> {
        val packs = if (tournamentId != null) {
            if (!tournamentRepository.existsById(tournamentId)) {
                throw ResponseStatusException(HttpStatus.NOT_FOUND, "Tournament $tournamentId not found")
            }
            cardPackRepository.findAllByTournament_IdOrderByIdAsc(tournamentId)
        } else {
            cardPackRepository.findAllByOrderByIdAsc()
        }
        return packs.map {
            it.toDto(loadPlayerIds(it.id!!), loadPackPerkIds(it.id!!))
        }
    }

    @Transactional(readOnly = true)
    fun listSkins(): List<CardSkinDto> =
        cardSkinRepository.findAllByOrderByIdAsc().map {
            CardSkinDto(
                id = it.id!!,
                code = it.code,
                name = it.name,
            )
        }

    @Transactional(readOnly = true)
    fun getPack(id: Long): CardPackDto {
        val pack = cardPackRepository.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Card pack $id not found")
        }
        return pack.toDto(
            cardPackPlayerRepository.findAllByCardPack_Id(id).map { it.fantasyPlayer!!.id!! },
            loadPackPerkIds(id),
        )
    }

    @Transactional
    fun createPack(request: CreateCardPackRequest): CardPackDto {
        validatePackConfiguration(
            request.rarityConfigs,
            request.useAllTournamentPlayers,
            request.playerIds,
            request.tournamentId,
        )
        validateOpeningMode(request.openingMode, request.autoGenerated, request.rarityConfigs)
        validatePackPerkIds(request.perkIds)
        val tournament = tournamentRepository.findById(request.tournamentId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Tournament ${request.tournamentId} not found")
        }
        val pack = cardPackRepository.save(
            CardPack(
                name = request.name.trim(),
                tournament = tournament,
                active = request.active,
                autoGenerated = request.autoGenerated,
                priceFantiki = request.priceFantiki,
                freeOpensPerUser = request.freeOpensPerUser,
                maxOpensPerUser = request.maxOpensPerUser,
                openingMode = request.openingMode,
                useAllTournamentPlayers = request.useAllTournamentPlayers,
                cardSkin = resolveSkin(request.skinId),
            ),
        )
        replacePackPerks(pack.id!!, request.perkIds)
        request.rarityConfigs.forEach { dto ->
            cardPackRarityConfigRepository.save(
                CardPackRarityConfig(
                    cardPack = pack,
                    rarity = dto.rarity,
                    cardsCount = dto.cardsCount,
                ),
            )
        }
        if (!request.useAllTournamentPlayers) {
            replacePlayerPool(pack, request.playerIds!!)
        }
        return getPack(pack.id!!)
    }

    @Transactional
    fun updatePack(id: Long, request: UpdateCardPackRequest): CardPackDto {
        val pack = cardPackRepository.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Card pack $id not found")
        }
        request.name?.let { pack.name = it.trim() }
        request.active?.let { pack.active = it }
        request.autoGenerated?.let { pack.autoGenerated = it }
        request.priceFantiki?.let { pack.priceFantiki = it }
        request.freeOpensPerUser?.let { pack.freeOpensPerUser = it }
        request.maxOpensPerUser?.let { pack.maxOpensPerUser = it }
        request.perkIds?.let { ids ->
            validatePackPerkIds(ids)
            replacePackPerks(id, ids)
        }
        request.skinId?.let { pack.cardSkin = resolveSkin(it) }

        val mergedUseAll = request.useAllTournamentPlayers ?: pack.useAllTournamentPlayers
        val currentPlayerIds = cardPackPlayerRepository.findAllByCardPack_Id(id).map { it.fantasyPlayer!!.id!! }
        val mergedPlayerIds = request.playerIds ?: currentPlayerIds

        val configsForValidation = request.rarityConfigs
            ?: pack.rarityConfigs.map { CardPackRarityConfigDto(rarity = it.rarity, cardsCount = it.cardsCount) }
        val mergedOpeningMode = request.openingMode ?: pack.openingMode
        validateOpeningMode(mergedOpeningMode, pack.autoGenerated, configsForValidation)
        request.openingMode?.let { pack.openingMode = it }
        validatePackConfiguration(
            configsForValidation,
            mergedUseAll,
            if (mergedUseAll) null else mergedPlayerIds,
            pack.tournament!!.id!!,
        )

        request.useAllTournamentPlayers?.let { pack.useAllTournamentPlayers = it }

        request.rarityConfigs?.let { cfg ->
            cardPackRarityConfigRepository.deleteAllByCardPack_Id(id)
            cardPackRarityConfigRepository.flush()
            val newCfgs = cfg.map { dto ->
                cardPackRarityConfigRepository.save(
                    CardPackRarityConfig(
                        cardPack = pack,
                        rarity = dto.rarity,
                        cardsCount = dto.cardsCount,
                    ),
                )
            }
            pack.rarityConfigs.clear()
            pack.rarityConfigs.addAll(newCfgs)
        }

        if (request.useAllTournamentPlayers != null || request.playerIds != null) {
            cardPackPlayerRepository.deleteAllByCardPack_Id(id)
            cardPackPlayerRepository.flush()
            if (!pack.useAllTournamentPlayers) {
                replacePlayerPool(pack, mergedPlayerIds)
            }
        }

        cardPackRepository.save(pack)
        return getPack(id)
    }

    @Transactional
    fun replacePackPlayers(packId: Long, playerIds: List<Long>): CardPackDto {
        val pack = cardPackRepository.findById(packId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Card pack $packId not found")
        }
        if (pack.useAllTournamentPlayers) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Player pool is fixed to all tournament players for this pack",
            )
        }
        validatePlayerIdsForTournament(pack.tournament!!.id!!, playerIds)
        if (playerIds.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "playerIds must not be empty")
        }
        cardPackPlayerRepository.deleteAllByCardPack_Id(packId)
        cardPackPlayerRepository.flush()
        replacePlayerPool(pack, playerIds)
        return getPack(packId)
    }

    /**
     * @param telegramUserId Telegram platform user id (not internal DB id).
     */
    @Transactional
    fun openPack(telegramUserId: Long, packId: Long): OpenPackResultDto {
        val user = userService.getOrCreateAndUpdateProfile(telegramUserId, null, null)
        val pack = cardPackRepository.findByIdWithRarityConfigs(packId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Card pack $packId not found")
        if (!pack.autoGenerated) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Card pack is not auto-generated")
        }
        if (pack.openingMode == CardPackOpeningMode.CHOOSE) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "CHOOSE packs must be selected in the user store")
        }
        if (!pack.active) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Card pack is not active")
        }
        val now = currentDbInstant()
        val option = drawPackOption(pack, avoidDuplicatePlayers = false)
        val drawn = materializeDrawOption(user, pack, option, now)
        recordPackOpened(user, pack, now)
        return OpenPackResultDto(userCards = drawn.map { it.toUserCardDto() })
    }

    fun currentDbInstant(): Instant =
        jdbcTemplate.queryForObject("SELECT clock_timestamp()", java.sql.Timestamp::class.java)!!.toInstant()

    fun generateChoiceOptions(pack: CardPack, optionCount: Int): List<CardPackDrawOption> {
        if (pack.openingMode != CardPackOpeningMode.CHOOSE) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Card pack is not a CHOOSE pack")
        }
        validateDrawablePack(pack)
        return List(optionCount) { drawPackOption(pack, avoidDuplicatePlayers = true) }
    }

    fun materializeDrawOption(
        user: io.github.mralex1810.fantasy.entity.TelegramUser,
        pack: CardPack,
        option: CardPackDrawOption,
        now: Instant,
    ): List<UserCard> =
        option.cards.map { draw ->
            val fantasyPlayer = fantasyPlayerRepository.findById(draw.fantasyPlayerId).orElseThrow {
                ResponseStatusException(HttpStatus.BAD_REQUEST, "Fantasy player ${draw.fantasyPlayerId} not found")
            }
            val perks =
                if (draw.perkIds.isEmpty()) {
                    emptyList()
                } else {
                    val byId = perkRepository.findAllByIdIn(draw.perkIds).associateBy { it.id }
                    draw.perkIds.map { perkId ->
                        byId[perkId]
                            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Card pack option contains unknown perk")
                    }
                }
            val template = findOrCreateCardTemplateForPerks(fantasyPlayer, draw.rarity, perks)
            val saved = userCardRepository.save(
                UserCard(
                    telegramUser = user,
                    cardTemplate = template,
                    sourceCardPack = pack,
                    cardSkin = pack.cardSkin,
                    acquiredAt = now,
                    usesRemaining = economyConfigService.getUsesForRarity(draw.rarity),
                    timesRenewed = 0,
                ),
            )
            userCardOwnershipService.recordAcquisition(saved, user, CardAcquisitionType.PACK_OPENING, now)
            saved
        }

    fun recordPackOpened(user: io.github.mralex1810.fantasy.entity.TelegramUser, pack: CardPack, now: Instant) {
        userCardPackOpenEventRepository.save(
            UserCardPackOpenEvent(
                telegramUser = user,
                cardPack = pack,
                openedAt = now,
            ),
        )
        userCardPackOpenCountRepository.incrementOpenCount(user.id!!, pack.id!!)
        telegramUserRepository.incrementPackOpensCount(user.id!!)
        applicationEventPublisher.publishEvent(
            AchievementProgressEvent(AchievementProgressEventType.PACK_OPENED, setOf(user.id!!)),
        )
    }

    private fun drawPackOption(pack: CardPack, avoidDuplicatePlayers: Boolean): CardPackDrawOption {
        validateDrawablePack(pack)
        val playerPool = buildFantasyPlayerPool(pack, pack.tournament!!.id!!)
        val randomPerks = loadRandomPerks(pack.id!!)
        val usedPlayerIds = mutableSetOf<Long>()
        val cards = pack.rarityConfigs
            .sortedBy { it.rarity.ordinal }
            .flatMap { cfg ->
                List(cfg.cardsCount) {
                    val eligible =
                        if (avoidDuplicatePlayers) {
                            playerPool.filter { it.id !in usedPlayerIds }.takeIf { it.isNotEmpty() } ?: playerPool
                        } else {
                            playerPool
                        }
                    val fantasyPlayer = eligible[random.nextInt(eligible.size)]
                    fantasyPlayer.id?.let { usedPlayerIds.add(it) }
                    val perks = pickPerksForSlot(cfg.rarity, randomPerks)
                    CardPackDrawCard(
                        fantasyPlayerId = fantasyPlayer.id!!,
                        playerName = fantasyPlayer.nickname,
                        playerPhotoUrl = fantasyPlayer.photoUrl,
                        rarity = cfg.rarity,
                        skinCode = pack.cardSkin?.code,
                        perkIds = perks.map { it.id },
                    )
                }
            }
        return CardPackDrawOption(optionId = UUID.randomUUID().toString(), cards = cards)
    }

    private fun validateDrawablePack(pack: CardPack) {
        if (!pack.autoGenerated) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Card pack is not auto-generated")
        }
        if (pack.rarityConfigs.any { it.rarity == Rarity.LEGENDARY && it.cardsCount > 0 }) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "LEGENDARY cannot be generated from packs")
        }
        val playerPool = buildFantasyPlayerPool(pack, pack.tournament!!.id!!)
        if (playerPool.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Player pool for this pack is empty")
        }
    }

    private fun loadRandomPerks(packId: Long): List<Perk> {
        val packLinks = cardPackPerkRepository.findAllByCardPackId(packId)
        return if (packLinks.isNotEmpty()) {
            val perkIds = packLinks.mapNotNull { it.perkId }
            perkRepository.findAllById(perkIds).toList()
        } else {
            perkRepository.findAllByCanAppearOnRandomCardsTrueOrderById()
        }
    }

    private fun buildFantasyPlayerPool(pack: CardPack, tournamentId: Long): List<FantasyPlayer> {
        return if (pack.useAllTournamentPlayers) {
            tournamentPlayerRepository.findAllByTournament_IdOrderById(tournamentId)
                .filter { !it.excludedFromPackPool }
                .map { it.fantasyPlayer!! }
        } else {
            val excludedIds = tournamentPlayerRepository.findAllByTournament_IdOrderById(tournamentId)
                .filter { it.excludedFromPackPool }
                .mapNotNull { it.fantasyPlayer?.id }
                .toSet()
            cardPackPlayerRepository.findAllByCardPack_Id(pack.id!!)
                .map { it.fantasyPlayer!! }
                .filter { it.id !in excludedIds }
        }
    }

    @Transactional(readOnly = true)
    fun buildActivePackFantasyPlayerPool(rarity: Rarity): List<FantasyPlayer> {
        if (rarity == Rarity.LEGENDARY) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "LEGENDARY cannot be generated from active packs")
        }
        return cardPackRepository.findAllActiveAutoGeneratedWithRarityConfigs()
            .filter { pack -> pack.rarityConfigs.any { it.rarity == rarity && it.cardsCount > 0 } }
            .flatMap { pack -> buildFantasyPlayerPool(pack, pack.tournament!!.id!!) }
            .distinctBy { it.id }
    }

    private fun pickPerksForSlot(rarity: Rarity, pool: List<Perk>): List<Perk> {
        return when (rarity) {
            Rarity.COMMON -> emptyList()
            Rarity.RARE -> {
                if (pool.isEmpty()) {
                    throw ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "No perks marked for random cards",
                    )
                }
                listOf(pool[random.nextInt(pool.size)])
            }
            Rarity.EPIC -> {
                if (pool.size < 2) {
                    throw ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Need at least 2 perks marked for random cards for EPIC slots",
                    )
                }
                pool.shuffled(random).take(2)
            }
            Rarity.LEGENDARY -> throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "LEGENDARY cannot be generated from packs",
            )
        }
    }

    /**
     * Shared by pack opening and EPIC → LEGENDARY upgrade: reuse [CardTemplate] when the same
     * player, rarity, and perk id set already exists.
     */
    @Synchronized
    fun findOrCreateCardTemplateForPerks(
        fantasyPlayer: FantasyPlayer,
        rarity: Rarity,
        perks: List<Perk>,
    ): CardTemplate {
        val wantedIds = perks.map { it.id }.toSet()
        val candidates = cardTemplateRepository.findAllByFantasyPlayer_IdAndRarity(fantasyPlayer.id!!, rarity)
        val existing = candidates.find { ct ->
            val ids = cardTemplatePerkRepository.findPerkIdsByCardTemplateId(ct.id!!).toSet()
            ids == wantedIds
        }
        if (existing != null) {
            return existing
        }
        val saved = cardTemplateRepository.save(
            CardTemplate(
                fantasyPlayer = fantasyPlayer,
                rarity = rarity,
            ),
        )
        perks.forEach { a ->
            val row = CardTemplatePerk(
                cardTemplate = saved,
                perk = a,
                bonusPoints = null,
            )
            cardTemplatePerkRepository.save(row)
            saved.perks.add(row)
        }
        return cardTemplateRepository.findById(saved.id!!).orElseThrow()
    }

    private fun replacePlayerPool(pack: CardPack, playerIds: List<Long>) {
        playerIds.forEach { fpId ->
            val fp = fantasyPlayerRepository.findById(fpId).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Fantasy player $fpId not found")
            }
            cardPackPlayerRepository.save(CardPackPlayer(cardPack = pack, fantasyPlayer = fp))
        }
    }

    private fun loadPlayerIds(packId: Long): List<Long> =
        cardPackPlayerRepository.findAllByCardPack_Id(packId).map { it.fantasyPlayer!!.id!! }

    private fun loadPackPerkIds(packId: Long): List<String> =
        cardPackPerkRepository
            .findAllByCardPackId(packId)
            .mapNotNull { it.perkId }
            .sorted()

    private fun replacePackPerks(packId: Long, perkIds: List<String>) {
        cardPackPerkRepository.deleteAllByCardPackId(packId)
        cardPackPerkRepository.flush()
        if (perkIds.isEmpty()) return
        for (achId in perkIds) {
            val row = CardPackPerk()
            row.cardPackId = packId
            row.perkId = achId
            cardPackPerkRepository.save(row)
        }
    }

    private fun validateOpeningMode(
        openingMode: CardPackOpeningMode,
        autoGenerated: Boolean,
        rarityConfigs: List<CardPackRarityConfigDto>,
    ) {
        if (openingMode != CardPackOpeningMode.CHOOSE) return
        if (!autoGenerated) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "CHOOSE packs must be auto-generated")
        }
        if (rarityConfigs.any { it.rarity == Rarity.LEGENDARY && it.cardsCount > 0 }) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "LEGENDARY cannot be generated from CHOOSE packs")
        }
    }

    private fun validatePackPerkIds(perkIds: List<String>) {
        if (perkIds.isEmpty()) return
        val unique = perkIds.toSet()
        if (unique.size != perkIds.size) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "perkIds must not contain duplicates")
        }
        val found = perkRepository.findAllById(unique)
        if (found.size != unique.size) {
            val known = found.map { it.id }.toSet()
            val missing = unique - known
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown perk ids: $missing")
        }
    }

    private fun resolveSkin(skinId: Long?) =
        skinId?.let {
            cardSkinRepository.findById(it).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Card skin $it not found")
            }
        }

    private fun CardPack.toDto(playerIds: List<Long>, perkIds: List<String>): CardPackDto {
        val cfgs = rarityConfigs.map { c ->
            CardPackRarityConfigResponseDto(
                id = c.id!!,
                rarity = c.rarity,
                cardsCount = c.cardsCount,
            )
        }
        return CardPackDto(
            id = id!!,
            name = name,
            tournamentId = tournament!!.id!!,
            active = active,
            autoGenerated = autoGenerated,
            openingMode = openingMode,
            priceFantiki = priceFantiki,
            freeOpensPerUser = freeOpensPerUser,
            maxOpensPerUser = maxOpensPerUser,
            perkIds = perkIds,
            useAllTournamentPlayers = useAllTournamentPlayers,
            playerIds = playerIds,
            rarityConfigs = cfgs,
            skinId = cardSkin?.id,
            skinCode = cardSkin?.code,
        )
    }

    private fun UserCard.toUserCardDto() = UserCardDto(
        id = id!!,
        telegramUserId = telegramUser!!.telegramId,
        cardTemplateId = cardTemplate!!.id!!,
        acquiredAt = acquiredAt,
        sourceCardPackId = sourceCardPack?.id,
    )

    companion object {
        fun validateRarityConfigs(configs: List<CardPackRarityConfigDto>) {
            if (configs.isEmpty()) {
                throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "At least one rarity config is required",
                )
            }
            val duplicates = configs.groupingBy { it.rarity }.eachCount().filter { it.value > 1 }.keys
            if (duplicates.isNotEmpty()) {
                throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Duplicate rarity entries: $duplicates",
                )
            }
            configs.forEach { c ->
                if (c.cardsCount <= 0) {
                    throw ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "cardsCount must be positive for rarity ${c.rarity}",
                    )
                }
                if (c.rarity == Rarity.LEGENDARY) {
                    throw ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "LEGENDARY is not allowed in card packs",
                    )
                }
            }
        }

        fun validatePackConfiguration(
            configs: List<CardPackRarityConfigDto>,
            useAllTournamentPlayers: Boolean,
            playerIds: List<Long>?,
            tournamentId: Long,
            tournamentPlayerRepository: TournamentPlayerRepository,
        ) {
            validateRarityConfigs(configs)
            if (useAllTournamentPlayers) {
                if (tournamentPlayerRepository.countByTournament_IdAndExcludedFromPackPoolIsFalse(tournamentId) == 0L) {
                    throw ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Tournament has no players eligible for pack pool (all may be excluded from pack)",
                    )
                }
            } else {
                val ids = playerIds ?: emptyList()
                if (ids.isEmpty()) {
                    throw ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "playerIds is required when useAllTournamentPlayers is false",
                    )
                }
                ids.forEach { fpId ->
                    if (!tournamentPlayerRepository.existsByTournament_IdAndFantasyPlayer_Id(tournamentId, fpId)) {
                        throw ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Fantasy player $fpId is not registered in tournament $tournamentId",
                        )
                    }
                }
                val anyEligible = ids.any { fpId ->
                    tournamentPlayerRepository.existsByTournament_IdAndFantasyPlayer_IdAndExcludedFromPackPoolIsFalse(
                        tournamentId,
                        fpId,
                    )
                }
                if (!anyEligible) {
                    throw ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "All selected players are excluded from pack pool for this tournament",
                    )
                }
            }
        }
    }

    private fun validatePackConfiguration(
        configs: List<CardPackRarityConfigDto>,
        useAllTournamentPlayers: Boolean,
        playerIds: List<Long>?,
        tournamentId: Long,
    ) {
        validatePackConfiguration(
            configs,
            useAllTournamentPlayers,
            playerIds,
            tournamentId,
            tournamentPlayerRepository,
        )
    }

    private fun validatePlayerIdsForTournament(tournamentId: Long, playerIds: List<Long>) {
        playerIds.forEach { fpId ->
            if (!tournamentPlayerRepository.existsByTournament_IdAndFantasyPlayer_Id(tournamentId, fpId)) {
                throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Fantasy player $fpId is not registered in tournament $tournamentId",
                )
            }
        }
        val anyEligible = playerIds.any { fpId ->
            tournamentPlayerRepository.existsByTournament_IdAndFantasyPlayer_IdAndExcludedFromPackPoolIsFalse(
                tournamentId,
                fpId,
            )
        }
        if (!anyEligible) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "All selected players are excluded from pack pool for this tournament",
            )
        }
    }
}
