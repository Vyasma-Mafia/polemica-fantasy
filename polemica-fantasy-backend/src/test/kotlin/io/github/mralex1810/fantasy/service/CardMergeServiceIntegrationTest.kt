package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.dto.user.request.CardMergeConfirmRequest
import io.github.mralex1810.fantasy.dto.user.request.CardMergePreviewRequest
import io.github.mralex1810.fantasy.entity.CardAcquisitionType
import io.github.mralex1810.fantasy.entity.CardMergeOperation
import io.github.mralex1810.fantasy.entity.FantasyPlayer
import io.github.mralex1810.fantasy.entity.Rarity
import io.github.mralex1810.fantasy.entity.TelegramUser
import io.github.mralex1810.fantasy.entity.UserCard
import io.github.mralex1810.fantasy.repository.FantasyPlayerRepository
import io.github.mralex1810.fantasy.repository.PerkRepository
import io.github.mralex1810.fantasy.repository.TelegramUserRepository
import io.github.mralex1810.fantasy.repository.UserCardOwnershipHistoryRepository
import io.github.mralex1810.fantasy.repository.UserCardRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class CardMergeServiceIntegrationTest {

    @Autowired
    private lateinit var cardMergeService: CardMergeService

    @Autowired
    private lateinit var cardPackService: CardPackService

    @Autowired
    private lateinit var economyConfigService: EconomyConfigService

    @Autowired
    private lateinit var telegramUserRepository: TelegramUserRepository

    @Autowired
    private lateinit var fantasyPlayerRepository: FantasyPlayerRepository

    @Autowired
    private lateinit var perkRepository: PerkRepository

    @Autowired
    private lateinit var userCardRepository: UserCardRepository

    @Autowired
    private lateinit var ownershipHistoryRepository: UserCardOwnershipHistoryRepository

    @Test
    @Transactional
    fun `COMMON preview reuses roll and confirm creates merged card with inherited contract and ownership history`() {
        val user = createUser(991_001L)
        val fp = createFantasyPlayer(991_001L, "merge-common")
        val template = cardPackService.findOrCreateCardTemplateForPerks(fp, Rarity.COMMON, emptyList())
        val cards = listOf(
            saveCard(user, template, usesRemaining = 1, timesRenewed = 0),
            saveCard(user, template, usesRemaining = 1, timesRenewed = 2),
            saveCard(user, template, usesRemaining = 1, timesRenewed = 1),
        )
        val inputIds = cards.map { it.id!! }.reversed()

        val firstPreview = cardMergeService.preview(
            user,
            CardMergePreviewRequest(CardMergeOperation.COMMON_TO_RARE, inputIds),
        )
        val secondPreview = cardMergeService.preview(
            user,
            CardMergePreviewRequest(CardMergeOperation.COMMON_TO_RARE, inputIds),
        )

        assertEquals(firstPreview.previewId, secondPreview.previewId)
        assertEquals(firstPreview.selectablePerks.map { it.id }, secondPreview.selectablePerks.map { it.id })
        assertTrue(secondPreview.sameRollForInputSet)

        val response = cardMergeService.confirm(
            user,
            CardMergeConfirmRequest(
                operation = CardMergeOperation.COMMON_TO_RARE,
                inputUserCardIds = inputIds,
                selectedPerkIds = listOf(firstPreview.selectablePerks.first().id),
                previewId = firstPreview.previewId,
            ),
        )

        assertEquals(Rarity.RARE, response.card.rarity)
        assertEquals(minOf(economyConfigService.getUsesForRarity(Rarity.RARE), 3), response.card.usesRemaining)
        assertEquals(2, response.card.timesRenewed)
        assertEquals(0L, response.spentFantiki)
        cards.forEach { card ->
            assertNotNull(userCardRepository.findById(card.id!!).orElseThrow().deletedAt)
        }
        val history = ownershipHistoryRepository.findAllByUserCard_IdOrderByAcquiredAtAsc(response.card.id)
        assertEquals(CardAcquisitionType.CARD_MERGE, history.single().acquisitionType)
    }

    @Test
    @Transactional
    fun `confirm honors promised preview even when selected offered perk leaves random pool`() {
        val user = createUser(991_003L)
        val fp = createFantasyPlayer(991_003L, "merge-promised-preview")
        val template = cardPackService.findOrCreateCardTemplateForPerks(fp, Rarity.COMMON, emptyList())
        val cards = listOf(
            saveCard(user, template),
            saveCard(user, template),
            saveCard(user, template),
        )

        val preview = cardMergeService.preview(
            user,
            CardMergePreviewRequest(CardMergeOperation.COMMON_TO_RARE, cards.map { it.id!! }),
        )
        val promisedPerkId = preview.selectablePerks.first().id
        val promisedPerk = perkRepository.findById(promisedPerkId).orElseThrow()
        promisedPerk.canAppearOnRandomCards = false
        perkRepository.saveAndFlush(promisedPerk)

        val response = cardMergeService.confirm(
            user,
            CardMergeConfirmRequest(
                operation = CardMergeOperation.COMMON_TO_RARE,
                inputUserCardIds = cards.map { it.id!! },
                selectedPerkIds = listOf(promisedPerkId),
                previewId = preview.previewId,
            ),
        )

        assertEquals(Rarity.RARE, response.card.rarity)
        assertEquals(listOf(promisedPerkId), response.card.perks.map { it.perkId })
    }

    @Test
    @Transactional
    fun `RARE preview handles ABC AAB and AAA perk selection rules`() {
        val user = createUser(991_002L)
        val fp = createFantasyPlayer(991_002L, "merge-rare")
        val a = perkRepository.findById("voteForBlack").orElseThrow()
        val b = perkRepository.findById("sniper").orElseThrow()
        val c = perkRepository.findById("sheriffCheck").orElseThrow()

        val abcCards = listOf(
            saveCard(user, cardPackService.findOrCreateCardTemplateForPerks(fp, Rarity.RARE, listOf(a))),
            saveCard(user, cardPackService.findOrCreateCardTemplateForPerks(fp, Rarity.RARE, listOf(b))),
            saveCard(user, cardPackService.findOrCreateCardTemplateForPerks(fp, Rarity.RARE, listOf(c))),
        )
        val abcPreview = cardMergeService.preview(
            user,
            CardMergePreviewRequest(CardMergeOperation.RARE_TO_EPIC, abcCards.map { it.id!! }),
        )
        assertEquals(emptyList<String>(), abcPreview.fixedPerkIds)
        assertEquals(2, abcPreview.requiredSelections)
        assertEquals(setOf(a.id, b.id, c.id), abcPreview.selectablePerks.map { it.id }.toSet())

        val aabCards = listOf(
            saveCard(user, cardPackService.findOrCreateCardTemplateForPerks(fp, Rarity.RARE, listOf(a))),
            saveCard(user, cardPackService.findOrCreateCardTemplateForPerks(fp, Rarity.RARE, listOf(a))),
            saveCard(user, cardPackService.findOrCreateCardTemplateForPerks(fp, Rarity.RARE, listOf(b))),
        )
        val aabPreview = cardMergeService.preview(
            user,
            CardMergePreviewRequest(CardMergeOperation.RARE_TO_EPIC, aabCards.map { it.id!! }),
        )
        assertEquals(setOf(a.id, b.id), aabPreview.fixedPerkIds.toSet())
        assertEquals(0, aabPreview.requiredSelections)
        assertEquals(emptyList<String>(), aabPreview.selectablePerks.map { it.id })

        val aabResult = cardMergeService.confirm(
            user,
            CardMergeConfirmRequest(
                operation = CardMergeOperation.RARE_TO_EPIC,
                inputUserCardIds = aabCards.map { it.id!! },
                selectedPerkIds = emptyList(),
                previewId = aabPreview.previewId,
            ),
        )
        assertEquals(Rarity.EPIC, aabResult.card.rarity)
        assertEquals(setOf(a.id, b.id), aabResult.card.perks.map { it.perkId }.toSet())

        val aaaCards = listOf(
            saveCard(user, cardPackService.findOrCreateCardTemplateForPerks(fp, Rarity.RARE, listOf(a))),
            saveCard(user, cardPackService.findOrCreateCardTemplateForPerks(fp, Rarity.RARE, listOf(a))),
            saveCard(user, cardPackService.findOrCreateCardTemplateForPerks(fp, Rarity.RARE, listOf(a))),
        )
        val aaaPreview = cardMergeService.preview(
            user,
            CardMergePreviewRequest(CardMergeOperation.RARE_TO_EPIC, aaaCards.map { it.id!! }),
        )
        assertEquals(listOf(a.id), aaaPreview.fixedPerkIds)
        assertEquals(1, aaaPreview.requiredSelections)
        assertTrue(aaaPreview.selectablePerks.isNotEmpty())
        assertTrue(aaaPreview.selectablePerks.none { it.id == a.id })
    }

    private fun createUser(telegramId: Long): TelegramUser =
        telegramUserRepository.save(TelegramUser(telegramId = telegramId, fantiki = 1000L))

    private fun createFantasyPlayer(polemicaUserId: Long, nickname: String): FantasyPlayer =
        fantasyPlayerRepository.save(FantasyPlayer(polemicaUserId = polemicaUserId, nickname = nickname))

    private fun saveCard(
        user: TelegramUser,
        template: io.github.mralex1810.fantasy.entity.CardTemplate,
        usesRemaining: Int = 3,
        timesRenewed: Int = 0,
    ): UserCard =
        userCardRepository.save(
            UserCard(
                telegramUser = user,
                cardTemplate = template,
                usesRemaining = usesRemaining,
                timesRenewed = timesRenewed,
            ),
        )

    companion object {
        @JvmField
        @Container
        @ServiceConnection
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")
    }
}
