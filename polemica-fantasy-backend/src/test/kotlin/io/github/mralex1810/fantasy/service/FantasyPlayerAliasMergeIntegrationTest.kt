package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.dto.admin.request.AddFantasyPlayerAliasRequest
import io.github.mralex1810.fantasy.dto.admin.request.MergeFantasyPlayersRequest
import io.github.mralex1810.fantasy.entity.CardPack
import io.github.mralex1810.fantasy.entity.CardPackPlayer
import io.github.mralex1810.fantasy.entity.CardTemplate
import io.github.mralex1810.fantasy.entity.Rarity
import io.github.mralex1810.fantasy.entity.Series
import io.github.mralex1810.fantasy.entity.SeriesPlayer
import io.github.mralex1810.fantasy.entity.SeriesStatus
import io.github.mralex1810.fantasy.entity.TelegramUser
import io.github.mralex1810.fantasy.entity.Tournament
import io.github.mralex1810.fantasy.entity.TournamentPlayer
import io.github.mralex1810.fantasy.entity.TournamentStatus
import io.github.mralex1810.fantasy.entity.UserCard
import io.github.mralex1810.fantasy.repository.CardPackPlayerRepository
import io.github.mralex1810.fantasy.repository.CardPackRepository
import io.github.mralex1810.fantasy.repository.CardTemplateRepository
import io.github.mralex1810.fantasy.repository.FantasyPlayerAliasRepository
import io.github.mralex1810.fantasy.repository.FantasyPlayerRepository
import io.github.mralex1810.fantasy.repository.SeriesPlayerRepository
import io.github.mralex1810.fantasy.repository.SeriesRepository
import io.github.mralex1810.fantasy.repository.TelegramUserRepository
import io.github.mralex1810.fantasy.repository.TournamentPlayerRepository
import io.github.mralex1810.fantasy.repository.TournamentRepository
import io.github.mralex1810.fantasy.repository.UserCardRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.server.ResponseStatusException
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class FantasyPlayerAliasMergeIntegrationTest {

    @Autowired
    private lateinit var fantasyPlayerAdminService: FantasyPlayerAdminService

    @Autowired
    private lateinit var resolverService: FantasyPlayerResolverService

    @Autowired
    private lateinit var fantasyPlayerRepository: FantasyPlayerRepository

    @Autowired
    private lateinit var fantasyPlayerAliasRepository: FantasyPlayerAliasRepository

    @Autowired
    private lateinit var tournamentRepository: TournamentRepository

    @Autowired
    private lateinit var tournamentPlayerRepository: TournamentPlayerRepository

    @Autowired
    private lateinit var seriesRepository: SeriesRepository

    @Autowired
    private lateinit var seriesPlayerRepository: SeriesPlayerRepository

    @Autowired
    private lateinit var cardPackRepository: CardPackRepository

    @Autowired
    private lateinit var cardPackPlayerRepository: CardPackPlayerRepository

    @Autowired
    private lateinit var cardTemplateRepository: CardTemplateRepository

    @Autowired
    private lateinit var telegramUserRepository: TelegramUserRepository

    @Autowired
    private lateinit var userCardRepository: UserCardRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `resolver returns alias owner and merge transfers direct references without losing user cards`() {
        val target = resolverService.createWithPrimaryAlias(77_001L, "target")
        val source = resolverService.createWithPrimaryAlias(77_002L, "source")
        fantasyPlayerAdminService.addAlias(source.id!!, AddFantasyPlayerAliasRequest(77_003L))

        assertEquals(source.id, resolverService.requireByPolemicaUserId(77_003L).id)

        val tournament = tournamentRepository.save(Tournament(name = "Alias Test", status = TournamentStatus.ACTIVE))
        val targetTp = tournamentPlayerRepository.save(TournamentPlayer(tournament = tournament, fantasyPlayer = target))
        val sourceTp = tournamentPlayerRepository.save(TournamentPlayer(tournament = tournament, fantasyPlayer = source))
        val series = seriesRepository.save(
            Series(
                tournament = tournament,
                name = "Alias Series",
                namePrefix = "Alias",
                status = SeriesStatus.UPCOMING,
                startsAt = Instant.now(),
                teamDeadline = Instant.now(),
            ),
        )
        seriesPlayerRepository.save(SeriesPlayer(series = series, tournamentPlayer = targetTp))
        seriesPlayerRepository.save(SeriesPlayer(series = series, tournamentPlayer = sourceTp))

        val pack = cardPackRepository.save(CardPack(name = "Alias Pack", tournament = tournament))
        cardPackPlayerRepository.save(CardPackPlayer(cardPack = pack, fantasyPlayer = target))
        cardPackPlayerRepository.save(CardPackPlayer(cardPack = pack, fantasyPlayer = source))

        val sourceTemplate = cardTemplateRepository.save(CardTemplate(fantasyPlayer = source, rarity = Rarity.COMMON))
        val user = telegramUserRepository.save(TelegramUser(telegramId = 77_000L))
        val sourceCard = userCardRepository.save(UserCard(telegramUser = user, cardTemplate = sourceTemplate, usesRemaining = 1))

        jdbcTemplate.update(
            """
            INSERT INTO marketplace_watch_filter (
                telegram_user_id, fantasy_player_id, tournament_id, rarity, max_price,
                perk_ids_key, min_times_renewed, max_times_renewed
            )
            VALUES (?, ?, NULL, 'COMMON', 100, '', NULL, NULL),
                   (?, ?, NULL, 'COMMON', 100, '', NULL, NULL)
            """.trimIndent(),
            user.id!!,
            target.id!!,
            user.id!!,
            source.id!!,
        )
        jdbcTemplate.update(
            "INSERT INTO user_profile_customization (telegram_user_id, favorite_badge_fantasy_player_id) VALUES (?, ?)",
            user.id!!,
            source.id!!,
        )
        jdbcTemplate.update(
            """
            INSERT INTO user_card_merge (
                telegram_user_id, result_user_card_id, operation, source_rarity, result_rarity,
                fantasy_player_id, selected_perk_ids, fixed_perk_ids, offered_perk_ids
            )
            VALUES (?, ?, 'COMMON_TO_RARE', 'COMMON', 'RARE', ?, '[]'::jsonb, '[]'::jsonb, '[]'::jsonb)
            """.trimIndent(),
            user.id!!,
            sourceCard.id!!,
            source.id!!,
        )

        val preview = fantasyPlayerAdminService.mergePreview(target.id!!, source.id!!)
        assertTrue(preview.canMerge)
        assertTrue(preview.warnings.any { it.code == "ROSTER_CONFLICTS" })

        fantasyPlayerAdminService.mergeConfirm(
            target.id!!,
            MergeFantasyPlayersRequest(sourceFantasyPlayerId = source.id!!, reason = "same real player"),
        )

        assertFalse(fantasyPlayerRepository.existsById(source.id!!))
        assertEquals(target.id, resolverService.requireByPolemicaUserId(77_002L).id)
        assertEquals(target.id, resolverService.requireByPolemicaUserId(77_003L).id)
        assertEquals(1L, tournamentPlayerRepository.countByFantasyPlayer_Id(target.id!!))
        assertEquals(1, seriesPlayerRepository.findAllBySeries_Id(series.id!!).size)
        assertEquals(1, cardPackPlayerRepository.findAllByCardPack_Id(pack.id!!).size)
        assertEquals(target.id, cardTemplateRepository.findById(sourceTemplate.id!!).orElseThrow().fantasyPlayer!!.id)
        assertEquals(sourceCard.id, userCardRepository.findById(sourceCard.id!!).orElseThrow().id)
        assertEquals(
            1L,
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM marketplace_watch_filter WHERE fantasy_player_id = ?",
                Long::class.java,
                target.id!!,
            ),
        )
        assertEquals(
            target.id,
            jdbcTemplate.queryForObject(
                "SELECT favorite_badge_fantasy_player_id FROM user_profile_customization WHERE telegram_user_id = ?",
                Long::class.java,
                user.id!!,
            ),
        )
        assertEquals(
            target.id,
            jdbcTemplate.queryForObject(
                "SELECT fantasy_player_id FROM user_card_merge WHERE result_user_card_id = ?",
                Long::class.java,
                sourceCard.id!!,
            ),
        )
        assertEquals(3, fantasyPlayerAliasRepository.findAllByFantasyPlayer_IdOrderByPrimaryAliasDescPolemicaUserIdAsc(target.id!!).size)
    }

    @Test
    fun `merge blocks duplicate series rows with different replacements`() {
        val target = resolverService.createWithPrimaryAlias(78_001L, "target replacement")
        val source = resolverService.createWithPrimaryAlias(78_002L, "source replacement")
        val tournament = tournamentRepository.save(Tournament(name = "Replacement Conflict", status = TournamentStatus.ACTIVE))
        val targetTp = tournamentPlayerRepository.save(TournamentPlayer(tournament = tournament, fantasyPlayer = target))
        val sourceTp = tournamentPlayerRepository.save(TournamentPlayer(tournament = tournament, fantasyPlayer = source))
        val series = seriesRepository.save(
            Series(
                tournament = tournament,
                name = "Replacement Conflict Series",
                namePrefix = "Replacement Conflict",
                status = SeriesStatus.UPCOMING,
                startsAt = Instant.now(),
                teamDeadline = Instant.now(),
            ),
        )
        seriesPlayerRepository.save(SeriesPlayer(series = series, tournamentPlayer = targetTp, replacementPolemicaUserId = null))
        seriesPlayerRepository.save(SeriesPlayer(series = series, tournamentPlayer = sourceTp, replacementPolemicaUserId = 78_099L))

        val preview = fantasyPlayerAdminService.mergePreview(target.id!!, source.id!!)

        assertFalse(preview.canMerge)
        assertTrue(preview.blockers.any { it.code == "SERIES_REPLACEMENT_CONFLICTS" })
        val ex = assertThrows(ResponseStatusException::class.java) {
            fantasyPlayerAdminService.mergeConfirm(
                target.id!!,
                MergeFantasyPlayersRequest(sourceFantasyPlayerId = source.id!!, reason = "same real player"),
            )
        }
        assertEquals(409, ex.statusCode.value())
    }

    @Test
    fun `merge blocks duplicate tournament rows with different pack-pool exclusion flags`() {
        val target = resolverService.createWithPrimaryAlias(79_001L, "target pack pool")
        val source = resolverService.createWithPrimaryAlias(79_002L, "source pack pool")
        val tournament = tournamentRepository.save(Tournament(name = "Pack Pool Conflict", status = TournamentStatus.ACTIVE))
        tournamentPlayerRepository.save(
            TournamentPlayer(tournament = tournament, fantasyPlayer = target, excludedFromPackPool = false),
        )
        tournamentPlayerRepository.save(
            TournamentPlayer(tournament = tournament, fantasyPlayer = source, excludedFromPackPool = true),
        )

        val preview = fantasyPlayerAdminService.mergePreview(target.id!!, source.id!!)

        assertFalse(preview.canMerge)
        assertTrue(preview.blockers.any { it.code == "TOURNAMENT_PACK_POOL_CONFLICTS" })
        val ex = assertThrows(ResponseStatusException::class.java) {
            fantasyPlayerAdminService.mergeConfirm(
                target.id!!,
                MergeFantasyPlayersRequest(sourceFantasyPlayerId = source.id!!, reason = "same real player"),
            )
        }
        assertEquals(409, ex.statusCode.value())
    }

    companion object {
        @JvmField
        @Container
        @ServiceConnection
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")
    }
}
