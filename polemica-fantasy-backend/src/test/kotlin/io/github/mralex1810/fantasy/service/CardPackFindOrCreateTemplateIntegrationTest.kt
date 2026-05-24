package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.entity.FantasyPlayer
import io.github.mralex1810.fantasy.entity.Rarity
import io.github.mralex1810.fantasy.repository.PerkRepository
import io.github.mralex1810.fantasy.repository.CardTemplatePerkRepository
import io.github.mralex1810.fantasy.repository.CardTemplateRepository
import io.github.mralex1810.fantasy.repository.FantasyPlayerRepository
import org.junit.jupiter.api.Assertions.assertEquals
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
@Testcontainers
@ActiveProfiles("test")
class CardPackFindOrCreateTemplateIntegrationTest {

    @Autowired
    private lateinit var cardPackService: CardPackService

    @Autowired
    private lateinit var fantasyPlayerRepository: FantasyPlayerRepository

    @Autowired
    private lateinit var perkRepository: PerkRepository

    @Autowired
    private lateinit var cardTemplateRepository: CardTemplateRepository

    @Autowired
    private lateinit var cardTemplatePerkRepository: CardTemplatePerkRepository

    @Test
    @Transactional
    fun `findOrCreateCardTemplate reuses same template twice in one transaction for RARE`() {
        val fp = fantasyPlayerRepository.save(
            FantasyPlayer(polemicaUserId = 884_001L, nickname = "pack-reuse-rare"),
        )
        val perk = perkRepository.findById("voteForBlack").orElseThrow()

        val first = cardPackService.findOrCreateCardTemplateForPerks(fp, Rarity.RARE, listOf(perk))
        val second = cardPackService.findOrCreateCardTemplateForPerks(fp, Rarity.RARE, listOf(perk))

        assertEquals(first.id, second.id)
        assertEquals(
            listOf(perk.id),
            cardTemplatePerkRepository.findPerkIdsByCardTemplateId(first.id!!).sorted(),
        )
        assertEquals(
            1,
            cardTemplateRepository.findAllByFantasyPlayer_IdAndRarity(fp.id!!, Rarity.RARE).size,
        )
    }

    @Test
    @Transactional
    fun `findOrCreateCardTemplate reuses template for EPIC independent of perk order`() {
        val fp = fantasyPlayerRepository.save(
            FantasyPlayer(polemicaUserId = 884_002L, nickname = "pack-reuse-epic"),
        )
        val a = perkRepository.findById("voteForBlack").orElseThrow()
        val b = perkRepository.findById("sniper").orElseThrow()

        val first = cardPackService.findOrCreateCardTemplateForPerks(fp, Rarity.EPIC, listOf(a, b))
        val second = cardPackService.findOrCreateCardTemplateForPerks(fp, Rarity.EPIC, listOf(b, a))

        assertEquals(first.id, second.id)
        assertEquals(
            setOf(a.id, b.id),
            cardTemplatePerkRepository.findPerkIdsByCardTemplateId(first.id!!).toSet(),
        )
        assertEquals(1, cardTemplateRepository.findAllByFantasyPlayer_IdAndRarity(fp.id!!, Rarity.EPIC).size)
    }

    companion object {
        @JvmField
        @Container
        @ServiceConnection
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")
    }
}
