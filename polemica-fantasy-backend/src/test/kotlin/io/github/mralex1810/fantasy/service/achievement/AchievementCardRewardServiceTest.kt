package io.github.mralex1810.fantasy.service.achievement

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.mralex1810.fantasy.entity.AchievementReward
import io.github.mralex1810.fantasy.entity.FantasyPlayer
import io.github.mralex1810.fantasy.entity.Perk
import io.github.mralex1810.fantasy.entity.Rarity
import io.github.mralex1810.fantasy.repository.CardSkinRepository
import io.github.mralex1810.fantasy.repository.FantasyPlayerRepository
import io.github.mralex1810.fantasy.repository.PerkRepository
import io.github.mralex1810.fantasy.repository.UserCardRepository
import io.github.mralex1810.fantasy.service.CardPackService
import io.github.mralex1810.fantasy.service.EconomyConfigService
import io.github.mralex1810.fantasy.service.ImageStorageService
import io.github.mralex1810.fantasy.service.UserCardOwnershipService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

class AchievementCardRewardServiceTest {

    private val cardPackService = mock<CardPackService>()
    private val fantasyPlayerRepository = mock<FantasyPlayerRepository>()
    private val perkRepository = mock<PerkRepository>()
    private val cardSkinRepository = mock<CardSkinRepository>()
    private val userCardRepository = mock<UserCardRepository>()
    private val userCardOwnershipService = mock<UserCardOwnershipService>()
    private val economyConfigService = mock<EconomyConfigService>()
    private val imageStorageService = mock<ImageStorageService>()
    private val service = AchievementCardRewardService(
        objectMapper = ObjectMapper(),
        cardPackService = cardPackService,
        fantasyPlayerRepository = fantasyPlayerRepository,
        perkRepository = perkRepository,
        cardSkinRepository = cardSkinRepository,
        userCardRepository = userCardRepository,
        userCardOwnershipService = userCardOwnershipService,
        economyConfigService = economyConfigService,
        imageStorageService = imageStorageService,
    )

    @Test
    fun `choice roll options come from active pack eligible players`() {
        whenever(cardPackService.buildActivePackFantasyPlayerPool(Rarity.COMMON)).thenReturn(
            (1L..6L).map { id ->
                FantasyPlayer(id = id, polemicaUserId = 1000L + id, nickname = "Player $id", photoUrl = "https://img/$id.png")
            },
        )
        whenever(imageStorageService.publicObjectUrl(org.mockito.kotlin.any())).thenAnswer { it.arguments[0] }

        val options = service.generateOptions(
            AchievementReward(
                id = 77L,
                rewardType = "CARD_CHOICE_ROLL",
                metadata = """{"rarity":"COMMON","count":2,"options":5,"skinCode":"challenge_edition","source":"ACTIVE_PACKS"}""",
            ),
        )

        assertThat(options).hasSize(5)
        assertThat(options.map { it.fantasyPlayerId }).doesNotHaveDuplicates()
        assertThat(options.map { it.rarity }).containsOnly("COMMON")
        assertThat(options.map { it.skinCode }).containsOnly("challenge_edition")
        assertThat(options.map { it.playerName }).allSatisfy { assertThat(it).startsWith("Player ") }
    }

    @Test
    fun `active pack source rejects legendary card generation`() {
        val ex = assertThrows(ResponseStatusException::class.java) {
            service.generateOptions(
                AchievementReward(
                    id = 77L,
                    rewardType = "CARD_CHOICE_ROLL",
                    metadata = """{"rarity":"LEGENDARY","count":1,"options":3,"source":"ACTIVE_PACKS"}""",
                ),
            )
        }

        assertThat(ex.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(ex.reason).contains("LEGENDARY")
    }

    @Test
    fun `choice option dto includes perks in persisted option order`() {
        whenever(perkRepository.findAllByIdIn(listOf("voteForBlack", "sniper"))).thenReturn(
            listOf(
                Perk(id = "sniper", name = "Снайпер", bonusPoints = 1.5),
                Perk(id = "voteForBlack", name = "Голос в черного", bonusPoints = 1.0),
            ),
        )

        val dto = service.optionDto(
            AchievementCardRewardOptionInternal(
                optionId = "option-1",
                fantasyPlayerId = 42L,
                playerName = "Player 42",
                playerPhotoUrl = "https://img/42.png",
                rarity = "EPIC",
                skinCode = "winner_edition",
                perkIds = listOf("voteForBlack", "sniper"),
            ),
        )

        assertThat(dto.perks.map { it.perkId }).containsExactly("voteForBlack", "sniper")
        assertThat(dto.perks.map { it.perkName }).containsExactly("Голос в черного", "Снайпер")
        assertThat(dto.perks.map { it.bonusPoints }).containsExactly(1.0, 1.5)
    }
}
