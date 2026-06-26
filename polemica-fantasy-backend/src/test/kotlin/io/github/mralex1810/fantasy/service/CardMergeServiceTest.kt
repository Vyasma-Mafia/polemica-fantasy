package io.github.mralex1810.fantasy.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.github.mralex1810.fantasy.dto.user.request.CardMergePreviewRequest
import io.github.mralex1810.fantasy.entity.CardMergeOperation
import io.github.mralex1810.fantasy.entity.CardTemplate
import io.github.mralex1810.fantasy.entity.CardTemplatePerk
import io.github.mralex1810.fantasy.entity.FantasyPlayer
import io.github.mralex1810.fantasy.entity.Perk
import io.github.mralex1810.fantasy.entity.Rarity
import io.github.mralex1810.fantasy.entity.TelegramUser
import io.github.mralex1810.fantasy.entity.UserCard
import io.github.mralex1810.fantasy.entity.UserCardMergePreview
import io.github.mralex1810.fantasy.repository.CardTemplateRepository
import io.github.mralex1810.fantasy.repository.FantasyTeamCardRepository
import io.github.mralex1810.fantasy.repository.MarketplaceListingRepository
import io.github.mralex1810.fantasy.repository.PerkRepository
import io.github.mralex1810.fantasy.repository.UserCardMergeInputRepository
import io.github.mralex1810.fantasy.repository.UserCardMergePreviewRepository
import io.github.mralex1810.fantasy.repository.UserCardMergeRepository
import io.github.mralex1810.fantasy.repository.UserCardRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CardMergeServiceTest {

    @Mock private lateinit var userCardRepository: UserCardRepository
    @Mock private lateinit var fantasyTeamCardRepository: FantasyTeamCardRepository
    @Mock private lateinit var marketplaceListingRepository: MarketplaceListingRepository
    @Mock private lateinit var perkRepository: PerkRepository
    @Mock private lateinit var cardPackService: CardPackService
    @Mock private lateinit var cardTemplateRepository: CardTemplateRepository
    @Mock private lateinit var previewRepository: UserCardMergePreviewRepository
    @Mock private lateinit var mergeRepository: UserCardMergeRepository
    @Mock private lateinit var mergeInputRepository: UserCardMergeInputRepository
    @Mock private lateinit var ownershipService: UserCardOwnershipService
    @Mock private lateinit var economyConfigService: EconomyConfigService
    @Mock private lateinit var userService: UserService
    @Mock private lateinit var imageStorageService: ImageStorageService
    @Mock private lateinit var cardValueService: CardValueService
    @Mock private lateinit var productEventService: ProductEventService

    private lateinit var service: CardMergeService
    private lateinit var user: TelegramUser
    private val objectMapper = ObjectMapper().registerKotlinModule()
    private val cardsById = mutableMapOf<Long, UserCard>()
    private val perksById = mutableMapOf<String, Perk>()
    private val previewsByHash = mutableMapOf<String, UserCardMergePreview>()
    private var nextPreviewId = 1L

    @BeforeEach
    fun setUp() {
        user = TelegramUser(telegramId = 5001L).apply { id = 10L }
        service = CardMergeService(
            userCardRepository = userCardRepository,
            fantasyTeamCardRepository = fantasyTeamCardRepository,
            marketplaceListingRepository = marketplaceListingRepository,
            perkRepository = perkRepository,
            cardPackService = cardPackService,
            cardTemplateRepository = cardTemplateRepository,
            userCardMergePreviewRepository = previewRepository,
            userCardMergeRepository = mergeRepository,
            userCardMergeInputRepository = mergeInputRepository,
            userCardOwnershipService = ownershipService,
            economyConfigService = economyConfigService,
            userService = userService,
            imageStorageService = imageStorageService,
            cardValueService = cardValueService,
            productEventService = productEventService,
            applicationEventPublisher = {},
            objectMapper = objectMapper,
        )
        whenever(imageStorageService.publicObjectUrl(anyOrNull())).thenAnswer { it.arguments[0] as String? }
        whenever(economyConfigService.getUsesForRarity(eq(Rarity.RARE))).thenReturn(3)
        whenever(economyConfigService.getUsesForRarity(eq(Rarity.EPIC))).thenReturn(4)
        whenever(economyConfigService.getMaxRenewals()).thenReturn(2)
        whenever(cardValueService.calculateValue(any<CardTemplate>())).thenAnswer {
            when (it.getArgument<CardTemplate>(0).rarity) {
                Rarity.COMMON -> 25L
                Rarity.RARE -> 50L
                Rarity.EPIC -> 100L
                Rarity.LEGENDARY -> 200L
            }
        }
        whenever(cardValueService.calculateValue(eq(Rarity.RARE), eq(1))).thenReturn(50L)
        whenever(cardValueService.calculateValue(eq(Rarity.EPIC), eq(2))).thenReturn(100L)
        whenever(fantasyTeamCardRepository.countInNonFinalizedSeries(any())).thenReturn(0L)
        whenever(marketplaceListingRepository.existsByUserCard_IdAndStatus(any(), any())).thenReturn(false)
        whenever(userCardRepository.findAllByIdInAndTelegramUser_Id(any(), eq(10L))).thenAnswer { invocation ->
            val ids = invocation.getArgument<Collection<Long>>(0)
            ids.mapNotNull { cardsById[it] }
        }
        whenever(perkRepository.findAllByIdIn(any())).thenAnswer { invocation ->
            val ids = invocation.getArgument<Collection<String>>(0)
            ids.mapNotNull { perksById[it] }
        }
        whenever(
            previewRepository.findByTelegramUser_IdAndOperationAndInputSetHashAndConsumedAtIsNull(
                eq(10L),
                any(),
                any(),
            ),
        ).thenAnswer { invocation ->
            previewsByHash[invocation.getArgument(2)]
        }
        whenever(previewRepository.save(any())).thenAnswer { invocation ->
            val preview = invocation.getArgument<UserCardMergePreview>(0)
            if (preview.id == null) {
                preview.id = nextPreviewId++
            }
            previewsByHash[preview.inputSetHash] = preview
            preview
        }
    }

    @Test
    fun `preview reuses COMMON roll for same input set`() {
        val fp = fantasyPlayer(100L)
        val commonTemplate = template(200L, fp, Rarity.COMMON)
        registerCards(
            card(1L, commonTemplate, usesRemaining = 1),
            card(2L, commonTemplate, usesRemaining = 1),
            card(3L, commonTemplate, usesRemaining = 1),
        )
        whenever(perkRepository.findAllByCanAppearOnRandomCardsTrueOrderById()).thenReturn(
            listOf(perk("voteForBlack"), perk("sniper"), perk("sheriffCheck")),
        )

        val first = service.preview(
            user,
            CardMergePreviewRequest(CardMergeOperation.COMMON_TO_RARE, listOf(3L, 2L, 1L)),
        )
        val second = service.preview(
            user,
            CardMergePreviewRequest(CardMergeOperation.COMMON_TO_RARE, listOf(1L, 2L, 3L)),
        )

        assertEquals(first.previewId, second.previewId)
        assertEquals(first.selectablePerks.map { it.id }, second.selectablePerks.map { it.id })
        assertTrue(second.sameRollForInputSet)
        assertEquals(1, first.requiredSelections)
    }

    @Test
    fun `preview applies RARE ABC AAB and AAA perk rules`() {
        val fp = fantasyPlayer(101L)
        val a = perk("voteForBlack")
        val b = perk("sniper")
        val c = perk("sheriffCheck")
        whenever(perkRepository.findAllByCanAppearOnRandomCardsTrueOrderById()).thenReturn(listOf(a, b, c))

        registerCards(
            card(10L, template(210L, fp, Rarity.RARE, a)),
            card(11L, template(211L, fp, Rarity.RARE, b)),
            card(12L, template(212L, fp, Rarity.RARE, c)),
        )
        val abc = service.preview(
            user,
            CardMergePreviewRequest(CardMergeOperation.RARE_TO_EPIC, listOf(10L, 11L, 12L)),
        )
        assertEquals(emptyList<String>(), abc.fixedPerkIds)
        assertEquals(2, abc.requiredSelections)
        assertEquals(setOf(a.id, b.id, c.id), abc.selectablePerks.map { it.id }.toSet())

        registerCards(
            card(20L, template(220L, fp, Rarity.RARE, a)),
            card(21L, template(221L, fp, Rarity.RARE, a)),
            card(22L, template(222L, fp, Rarity.RARE, b)),
        )
        val aab = service.preview(
            user,
            CardMergePreviewRequest(CardMergeOperation.RARE_TO_EPIC, listOf(20L, 21L, 22L)),
        )
        assertEquals(setOf(a.id, b.id), aab.fixedPerkIds.toSet())
        assertEquals(0, aab.requiredSelections)
        assertEquals(emptyList<String>(), aab.selectablePerks.map { it.id })

        registerCards(
            card(30L, template(230L, fp, Rarity.RARE, a)),
            card(31L, template(231L, fp, Rarity.RARE, a)),
            card(32L, template(232L, fp, Rarity.RARE, a)),
        )
        val aaa = service.preview(
            user,
            CardMergePreviewRequest(CardMergeOperation.RARE_TO_EPIC, listOf(30L, 31L, 32L)),
        )
        assertEquals(listOf(a.id), aaa.fixedPerkIds)
        assertEquals(1, aaa.requiredSelections)
        assertTrue(aaa.selectablePerks.isNotEmpty())
        assertTrue(aaa.selectablePerks.none { it.id == a.id })
    }

    private fun fantasyPlayer(id: Long): FantasyPlayer =
        FantasyPlayer(polemicaUserId = id, nickname = "fp-$id").apply { this.id = id }

    private fun perk(id: String): Perk =
        Perk(id = id, name = id, canAppearOnRandomCards = true).also { perksById[id] = it }

    private fun template(id: Long, fp: FantasyPlayer, rarity: Rarity, vararg perks: Perk): CardTemplate {
        val template = CardTemplate(id = id, fantasyPlayer = fp, rarity = rarity)
        perks.forEach { perk ->
            template.perks.add(CardTemplatePerk(cardTemplate = template, perk = perk))
        }
        return template
    }

    private fun card(id: Long, template: CardTemplate, usesRemaining: Int = 3): UserCard =
        UserCard(telegramUser = user, cardTemplate = template, usesRemaining = usesRemaining).apply { this.id = id }

    private fun registerCards(vararg cards: UserCard) {
        cards.forEach { cardsById[it.id!!] = it }
    }
}
