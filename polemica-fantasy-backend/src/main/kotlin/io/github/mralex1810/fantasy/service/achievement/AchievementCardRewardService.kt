package io.github.mralex1810.fantasy.service.achievement

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.mralex1810.fantasy.dto.user.response.AchievementCardChoiceOptionDto
import io.github.mralex1810.fantasy.dto.user.response.AchievementGrantedCardDto
import io.github.mralex1810.fantasy.dto.user.response.CardPerkBriefDto
import io.github.mralex1810.fantasy.entity.AchievementReward
import io.github.mralex1810.fantasy.entity.CardAcquisitionType
import io.github.mralex1810.fantasy.entity.Rarity
import io.github.mralex1810.fantasy.entity.TelegramUser
import io.github.mralex1810.fantasy.entity.UserCard
import io.github.mralex1810.fantasy.repository.CardSkinRepository
import io.github.mralex1810.fantasy.repository.FantasyPlayerRepository
import io.github.mralex1810.fantasy.repository.PerkRepository
import io.github.mralex1810.fantasy.repository.UserCardRepository
import io.github.mralex1810.fantasy.service.CardPackService
import io.github.mralex1810.fantasy.service.EconomyConfigService
import io.github.mralex1810.fantasy.service.ImageStorageService
import io.github.mralex1810.fantasy.service.UserCardOwnershipService
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID
import kotlin.random.Random

@Service
class AchievementCardRewardService(
    private val objectMapper: ObjectMapper,
    private val cardPackService: CardPackService,
    private val fantasyPlayerRepository: FantasyPlayerRepository,
    private val perkRepository: PerkRepository,
    private val cardSkinRepository: CardSkinRepository,
    private val userCardRepository: UserCardRepository,
    private val userCardOwnershipService: UserCardOwnershipService,
    private val economyConfigService: EconomyConfigService,
    private val imageStorageService: ImageStorageService,
) {
    fun parseSpec(reward: AchievementReward): AchievementCardRewardSpec {
        val metadata = reward.metadata?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Card reward metadata is required")
        val node = try {
            objectMapper.readTree(metadata)
        } catch (e: Exception) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Card reward metadata must be a JSON object", e)
        }
        if (!node.isObject) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Card reward metadata must be a JSON object")
        }
        val rarity = parseRarity(node.requiredText("rarity"))
        val count = node.requiredPositiveInt("count")
        val options = node.optionalPositiveInt("options") ?: count
        if (options < count) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Card reward options must be >= count")
        }
        val source = node.optionalText("source") ?: "ACTIVE_PACKS"
        if (source != "ACTIVE_PACKS") {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported card reward source: $source")
        }
        if (rarity == Rarity.LEGENDARY) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "LEGENDARY achievement cards require a non-pack source")
        }
        val skinCode = node.optionalText("skinCode") ?: reward.rewardCode?.trim()?.takeIf { it.isNotEmpty() }
        return AchievementCardRewardSpec(
            rarity = rarity,
            count = count,
            options = options,
            skinCode = skinCode,
            source = source,
        )
    }

    fun generateOptions(reward: AchievementReward): List<AchievementCardRewardOptionInternal> {
        val spec = parseSpec(reward)
        val playerPool = cardPackService.buildActivePackFantasyPlayerPool(spec.rarity)
        if (playerPool.size < spec.options) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Not enough eligible active-pack players for ${spec.rarity} reward",
            )
        }
        return playerPool.shuffled(Random.Default).take(spec.options).map { player ->
            AchievementCardRewardOptionInternal(
                optionId = UUID.randomUUID().toString(),
                fantasyPlayerId = player.id!!,
                playerName = player.nickname,
                playerPhotoUrl = imageStorageService.publicObjectUrl(player.photoUrl),
                rarity = spec.rarity.name,
                skinCode = spec.skinCode,
                perkIds = pickPerkIds(spec.rarity),
            )
        }
    }

    fun issueRandomCards(
        user: TelegramUser,
        reward: AchievementReward,
        now: Instant,
    ): List<AchievementGrantedCardDto> {
        val spec = parseSpec(reward)
        val options = generateOptions(reward).take(spec.count)
        return issueCardsFromOptions(user, options, now)
    }

    fun issueCardsFromOptions(
        user: TelegramUser,
        options: List<AchievementCardRewardOptionInternal>,
        now: Instant,
    ): List<AchievementGrantedCardDto> =
        options.map { option ->
            val fantasyPlayer = fantasyPlayerRepository.findById(option.fantasyPlayerId).orElseThrow {
                ResponseStatusException(HttpStatus.BAD_REQUEST, "Fantasy player ${option.fantasyPlayerId} not found")
            }
            val rarity = parseRarity(option.rarity)
            val perks = if (option.perkIds.isEmpty()) emptyList() else perkRepository.findAllByIdIn(option.perkIds)
            if (perks.size != option.perkIds.size) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Card reward option contains unknown perks")
            }
            val skin = option.skinCode?.let { code ->
                cardSkinRepository.findByCode(code)
                    ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Card skin $code not found")
            }
            val template = cardPackService.findOrCreateCardTemplateForPerks(fantasyPlayer, rarity, perks)
            val saved = userCardRepository.save(
                UserCard(
                    telegramUser = user,
                    cardTemplate = template,
                    cardSkin = skin,
                    acquiredAt = now,
                    usesRemaining = economyConfigService.getUsesForRarity(rarity),
                    timesRenewed = 0,
                ),
            )
            userCardOwnershipService.recordAcquisition(saved, user, CardAcquisitionType.ACHIEVEMENT_REWARD, now)
            AchievementGrantedCardDto(
                userCardId = saved.id!!,
                fantasyPlayerId = fantasyPlayer.id!!,
                playerName = fantasyPlayer.nickname,
                playerPhotoUrl = imageStorageService.publicObjectUrl(fantasyPlayer.photoUrl),
                rarity = rarity.name,
                skinCode = skin?.code,
            )
        }

    fun optionDto(option: AchievementCardRewardOptionInternal): AchievementCardChoiceOptionDto {
        val perksById = if (option.perkIds.isEmpty()) {
            emptyMap()
        } else {
            perkRepository.findAllByIdIn(option.perkIds).associateBy { it.id }
        }
        val perks = option.perkIds.map { perkId ->
            val perk = perksById[perkId]
                ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Card reward option contains unknown perk: $perkId")
            CardPerkBriefDto(
                perkId = perk.id,
                perkName = perk.name,
                bonusPoints = perk.bonusPoints,
            )
        }
        return AchievementCardChoiceOptionDto(
            optionId = option.optionId,
            fantasyPlayerId = option.fantasyPlayerId,
            playerName = option.playerName,
            playerPhotoUrl = option.playerPhotoUrl,
            rarity = option.rarity,
            skinCode = option.skinCode,
            perks = perks,
        )
    }

    private fun pickPerkIds(rarity: Rarity): List<String> {
        val pool = perkRepository.findAllByCanAppearOnRandomCardsTrueOrderById()
        return when (rarity) {
            Rarity.COMMON -> emptyList()
            Rarity.RARE -> {
                if (pool.isEmpty()) {
                    throw ResponseStatusException(HttpStatus.BAD_REQUEST, "No perks marked for random cards")
                }
                listOf(pool[Random.Default.nextInt(pool.size)].id)
            }
            Rarity.EPIC -> {
                if (pool.size < 2) {
                    throw ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Need at least 2 perks marked for random cards for EPIC rewards",
                    )
                }
                pool.shuffled(Random.Default).take(2).map { it.id }
            }
            Rarity.LEGENDARY -> throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "LEGENDARY achievement cards require a non-pack source",
            )
        }
    }

    private fun parseRarity(value: String): Rarity =
        try {
            Rarity.valueOf(value.uppercase())
        } catch (e: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid card reward rarity: $value")
        }

    private fun JsonNode.requiredText(field: String): String =
        optionalText(field) ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Card reward $field is required")

    private fun JsonNode.optionalText(field: String): String? =
        get(field)?.takeIf { it.isTextual }?.asText()?.trim()?.takeIf { it.isNotEmpty() }

    private fun JsonNode.requiredPositiveInt(field: String): Int =
        optionalPositiveInt(field)
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Card reward $field must be a positive integer")

    private fun JsonNode.optionalPositiveInt(field: String): Int? {
        val node = get(field) ?: return null
        if (!node.isInt || node.asInt() <= 0) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Card reward $field must be a positive integer")
        }
        return node.asInt()
    }
}

data class AchievementCardRewardSpec(
    val rarity: Rarity,
    val count: Int,
    val options: Int,
    val skinCode: String?,
    val source: String,
)

data class AchievementCardRewardOptionInternal(
    val optionId: String,
    val fantasyPlayerId: Long,
    val playerName: String,
    val playerPhotoUrl: String?,
    val rarity: String,
    val skinCode: String?,
    val perkIds: List<String>,
)
