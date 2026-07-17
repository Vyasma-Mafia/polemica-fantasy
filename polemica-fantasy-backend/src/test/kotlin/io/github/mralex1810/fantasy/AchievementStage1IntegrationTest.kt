package io.github.mralex1810.fantasy

import com.jayway.jsonpath.JsonPath
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.containsInAnyOrder
import org.hamcrest.Matchers.greaterThan
import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.sql.Timestamp
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class AchievementStage1IntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Test
    @Order(1)
    fun `seed definitions have launch policy and stage one visibility`() {
        assertSqlLong("SELECT COUNT(*) FROM achievement_definition", EXPECTED_ACHIEVEMENT_COUNT)
        assertSqlLong(
            "SELECT COUNT(*) FROM achievement_definition WHERE history_policy = 'FROM_ACHIEVEMENTS_LAUNCH'",
            EXPECTED_ACHIEVEMENT_COUNT,
        )
        assertSqlLong("SELECT COUNT(*) FROM achievement_definition WHERE enabled = TRUE", EXPECTED_ACHIEVEMENT_COUNT)
        assertSqlLong(
            "SELECT COUNT(*) FROM achievement_definition WHERE enabled = TRUE AND tracking_started_at IS NOT NULL",
            EXPECTED_ACHIEVEMENT_COUNT,
        )
        assertSqlLong(
            "SELECT COUNT(*) FROM achievement_definition WHERE enabled = FALSE AND tracking_started_at IS NULL",
            0,
        )
        assertSqlLong("SELECT COUNT(*) FROM achievement_definition WHERE code IN ('team_submit_50', 'budget_win_10', 'series_win_50', 'market_watch_5', 'view_public_profile_25')", 5)
    }

    @Test
    @Order(2)
    fun `seed reward rework uses valid card reward metadata`() {
        assertSqlLong("SELECT COUNT(*) FROM achievement_reward WHERE reward_type = 'CARD_SKIN_UNLOCK'", 0)
        assertSqlLong("SELECT COUNT(*) FROM achievement_reward WHERE reward_type = 'CARD_CHOICE_CATALOG'", 0)
        assertSqlLong(
            """
            SELECT COUNT(*)
            FROM card_skin
            WHERE code IN (
                'budget_edition',
                'common_challenge_edition',
                'winner_edition',
                'crafter_edition',
                'pack_hunter_edition'
            )
            """.trimIndent(),
            5,
        )
        assertSqlLong(
            """
            SELECT COUNT(*)
            FROM achievement_reward
            WHERE reward_type IN ('RANDOM_CARD', 'CARD_CHOICE_ROLL')
              AND (
                metadata IS NULL
                OR metadata->>'source' <> 'ACTIVE_PACKS'
                OR metadata->>'rarity' NOT IN ('COMMON', 'RARE', 'EPIC')
                OR COALESCE((metadata->>'count')::int, 0) <= 0
                OR (
                  reward_type = 'CARD_CHOICE_ROLL'
                  AND COALESCE((metadata->>'options')::int, 0) < COALESCE((metadata->>'count')::int, 0)
                )
              )
            """.trimIndent(),
            0,
        )
        assertSqlLong(
            """
            SELECT COUNT(*)
            FROM achievement_reward ar
            JOIN achievement_definition d ON d.id = ar.achievement_id
            WHERE d.code = 'team_submit_5'
              AND ar.reward_type = 'CARD_CHOICE_ROLL'
              AND ar.metadata @> '{"rarity":"COMMON","count":1,"options":3,"source":"ACTIVE_PACKS"}'::jsonb
            """.trimIndent(),
            1,
        )
        assertSqlLong(
            """
            SELECT COUNT(*)
            FROM achievement_reward ar
            JOIN achievement_definition d ON d.id = ar.achievement_id
            WHERE d.code = 'series_win_50'
              AND ar.reward_type = 'CARD_CHOICE_ROLL'
              AND ar.metadata @> '{"rarity":"EPIC","count":2,"options":5,"source":"ACTIVE_PACKS"}'::jsonb
            """.trimIndent(),
            1,
        )
        assertSqlLong(
            """
            SELECT COUNT(*)
            FROM achievement_reward ar
            JOIN achievement_definition d ON d.id = ar.achievement_id
            WHERE ar.reward_type = 'CARD_CHOICE_ROLL'
              AND (
                (d.code = 'budget_team_30' AND ar.metadata @> '{"skinCode":"budget_edition"}'::jsonb)
                OR (d.code = 'top_quarter_10' AND ar.metadata @> '{"skinCode":"common_challenge_edition"}'::jsonb)
                OR (d.code = 'series_win_10' AND ar.metadata @> '{"skinCode":"winner_edition"}'::jsonb)
                OR (d.code = 'legendary_upgrade_10' AND ar.metadata @> '{"skinCode":"crafter_edition"}'::jsonb)
                OR (d.code = 'pack_open_150' AND ar.metadata @> '{"skinCode":"pack_hunter_edition"}'::jsonb)
              )
            """.trimIndent(),
            5,
        )
        assertSqlLong(
            """
            SELECT COUNT(*)
            FROM achievement_definition
            WHERE code = 'same_player_3_rarities'
              AND condition_type = 'SAME_PLAYER_4_RARITIES'
              AND description = 'Собрать активные карты одного игрока во всех 4 редкостях'
            """.trimIndent(),
            1,
        )
        assertSqlLong(
            """
            SELECT COUNT(*)
            FROM achievement_reward ar
            JOIN achievement_definition d ON d.id = ar.achievement_id
            WHERE d.code = 'same_player_3_rarities'
              AND ar.reward_type = 'CARD_CHOICE_ROLL'
              AND ar.metadata @> '{"rarity":"RARE","count":2,"options":5,"source":"ACTIVE_PACKS"}'::jsonb
            """.trimIndent(),
            1,
        )
    }

    @Test
    @Order(3)
    fun `GET achievements requires TMA auth and returns enabled catalog only`() {
        mockMvc.perform(get("/api/v1/achievements"))
            .andExpect(status().isUnauthorized)

        val tma = tmaAuth(910_000_001L, "CatalogUser")
        mockMvc.perform(get("/api/v1/achievements").header("Authorization", tma))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.summary.totalVisible").value(EXPECTED_ACHIEVEMENT_COUNT.toInt()))
            .andExpect(jsonPath("$.summary.completed").value(0))
            .andExpect(jsonPath("$.summary.claimed").value(0))
            .andExpect(jsonPath("$.categories[*].code", containsInAnyOrder("PARTICIPATION", "BUDGET", "RESULTS", "PERIODIC_RATING", "COLLECTION", "PACKS", "MARKETPLACE", "SOCIAL")))
            .andExpect(jsonPath("$.categories[*].achievements[*].code", hasItem("team_submit_1")))
            .andExpect(jsonPath("$.categories[*].achievements[*].code", hasItem("series_win_50")))
            .andExpect(jsonPath("$.categories[*].achievements[*].code", hasItem("pack_open_1")))
            .andExpect(jsonPath("$.categories[*].achievements[*].code", hasItem("market_buy_1")))
            .andExpect(jsonPath("$.categories[*].achievements[*].rewards[*].type", hasItem("FANTIKI")))
            .andExpect(jsonPath("$.categories[*].achievements[*].rewards[*].type", hasItem("CARD_CHOICE_ROLL")))
    }

    @Test
    @Order(3)
    fun `admin dry run requires basic auth and V1 seed has zero instant payout`() {
        mockMvc.perform(post("/api/v1/admin/achievements/backfill/dry-run"))
            .andExpect(status().isUnauthorized)

        mockMvc.perform(
            post("/api/v1/admin/achievements/backfill/dry-run")
                .header("Authorization", basicAuth("admin", "test-admin-secret"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.instantCompleted").value(0))
            .andExpect(jsonPath("$.instantFantikiLiability").value(0))
            .andExpect(jsonPath("$.rows", hasSize<Any>(EXPECTED_ACHIEVEMENT_COUNT.toInt())))
    }

    @Test
    @Order(5)
    fun `periodic rating achievements use finalized snapshots cutoff and competition ranks`() {
        val primaryTelegramId = 910_000_040L
        val tiedTelegramId = 910_000_041L
        val primaryTma = tmaAuth(primaryTelegramId, "RatingMilestones")
        val tiedTma = tmaAuth(tiedTelegramId, "RatingTie")
        mockMvc.perform(get("/api/v1/achievements").header("Authorization", primaryTma)).andExpect(status().isOk)
        mockMvc.perform(get("/api/v1/achievements").header("Authorization", tiedTma)).andExpect(status().isOk)
        val primaryUserId = internalUserId(primaryTelegramId)
        val tiedUserId = internalUserId(tiedTelegramId)
        val cutoff = jdbcTemplate.queryForObject(
            "SELECT tracking_started_at FROM achievement_definition WHERE code='periodic_rating_period_1'",
            Timestamp::class.java,
        )!!.toInstant()

        fun createPeriod(code: String, periodStatus: String, finalizedAt: Instant): Long = jdbcTemplate.queryForObject(
            """
            INSERT INTO periodic_rating_period(code,title,starts_at,ends_at,status,finalized_at)
            VALUES (?, ?, ?, ?, ?, ?) RETURNING id
            """.trimIndent(),
            Long::class.java,
            code,
            code,
            Timestamp.from(finalizedAt.minusSeconds(7200)),
            Timestamp.from(finalizedAt.minusSeconds(3600)),
            periodStatus,
            Timestamp.from(finalizedAt),
        )!!

        fun addEntry(periodId: Long, userId: Long, rank: Int) {
            jdbcTemplate.update(
                """
                INSERT INTO periodic_rating_entry(
                    period_id,telegram_user_id,rank,total_score,series_count,average_score,best_series_score
                ) VALUES (?, ?, ?, 100, 1, 100, 100)
                """.trimIndent(),
                periodId,
                userId,
                rank,
            )
        }

        addEntry(createPeriod("achievement-before-cutoff", "FINALIZED", cutoff.minusSeconds(1)), primaryUserId, 1)
        listOf("OPEN", "SETTLING", "CANCELLED").forEachIndexed { index, periodStatus ->
            addEntry(
                createPeriod("achievement-${periodStatus.lowercase()}", periodStatus, cutoff.plusSeconds(100L + index)),
                primaryUserId,
                1,
            )
        }
        val firstPlacePeriod = createPeriod("achievement-rank-1", "FINALIZED", cutoff.plusSeconds(1000))
        addEntry(firstPlacePeriod, primaryUserId, 1)
        addEntry(firstPlacePeriod, tiedUserId, 1)
        addEntry(createPeriod("achievement-rank-3", "FINALIZED", cutoff.plusSeconds(2000)), primaryUserId, 3)
        addEntry(createPeriod("achievement-rank-10", "FINALIZED", cutoff.plusSeconds(3000)), primaryUserId, 10)
        addEntry(createPeriod("achievement-rank-11", "FINALIZED", cutoff.plusSeconds(4000)), primaryUserId, 11)

        assertAchievement(primaryTma, "periodic_rating_period_1", 4, "COMPLETED_UNCLAIMED")
        assertAchievement(primaryTma, "periodic_rating_period_5", 4, "IN_PROGRESS")
        assertAchievement(primaryTma, "periodic_rating_top10_1", 3, "COMPLETED_UNCLAIMED")
        assertAchievement(primaryTma, "periodic_rating_top10_5", 3, "IN_PROGRESS")
        assertAchievement(primaryTma, "periodic_rating_podium_1", 2, "COMPLETED_UNCLAIMED")
        assertAchievement(primaryTma, "periodic_rating_champion_1", 1, "COMPLETED_UNCLAIMED")
        assertAchievement(tiedTma, "periodic_rating_champion_1", 1, "COMPLETED_UNCLAIMED")

        val balanceBefore = jdbcTemplate.queryForObject(
            "SELECT fantiki FROM telegram_user WHERE id=?",
            Long::class.java,
            primaryUserId,
        )!!
        mockMvc.perform(post("/api/v1/achievements/periodic_rating_champion_1/claim").header("Authorization", primaryTma))
            .andExpect(status().isOk)
        assertSqlLong("SELECT fantiki FROM telegram_user WHERE id=$primaryUserId", balanceBefore + 600)
    }

    @Test
    @Order(4)
    fun `hidden enabled achievement is excluded from user catalog and cannot be claimed`() {
        val tma = tmaAuth(910_000_004L, "HiddenCatalog")
        configureAchievementRewards(
            code = "market_buy_1",
            rewardsSql = "('FANTIKI', 123, NULL, NULL::jsonb, 10)",
        )
        jdbcTemplate.update(
            "UPDATE achievement_definition SET enabled = TRUE, visibility = 'HIDDEN' WHERE code = 'market_buy_1'",
        )

        mockMvc.perform(get("/api/v1/achievements").header("Authorization", tma))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.categories[*].achievements[*].code", not(hasItem("market_buy_1"))))

        val internalUserId = internalUserId(910_000_004L)
        jdbcTemplate.update(
            """
            INSERT INTO user_achievement (telegram_user_id, achievement_id, progress_value, completed_at, updated_at)
            SELECT ?, id, target_value, now(), now()
            FROM achievement_definition
            WHERE code = 'market_buy_1'
            ON CONFLICT (telegram_user_id, achievement_id) DO UPDATE
            SET progress_value = EXCLUDED.progress_value,
                completed_at = EXCLUDED.completed_at,
                updated_at = EXCLUDED.updated_at
            """.trimIndent(),
            internalUserId,
        )

        mockMvc.perform(post("/api/v1/achievements/market_buy_1/claim").header("Authorization", tma))
            .andExpect(status().isNotFound)
        assertSqlLong(
            """
            SELECT COUNT(*)
            FROM user_achievement ua
            JOIN achievement_definition d ON d.id = ua.achievement_id
            WHERE ua.telegram_user_id = $internalUserId
              AND d.code = 'market_buy_1'
              AND ua.claimed_at IS NOT NULL
            """.trimIndent(),
            0,
        )
        assertSqlLong(
            """
            SELECT COUNT(*)
            FROM fantiki_transaction
            WHERE telegram_user_id = $internalUserId
              AND reason = 'ACHIEVEMENT_REWARD'
              AND amount = 123
            """.trimIndent(),
            0,
        )
    }

    @Test
    @Order(10)
    fun `team creation updates participation and budget achievements post commit`() {
        val auth = basicAuth("admin", "test-admin-secret")
        val fixture = createTeamFixture(auth, suffix = "team-progress")
        val tma = tmaAuth(fixture.telegramPlatformId, "TeamProgress")

        createFantasyTeam(tma, fixture.seriesId, "MAIN", fixture.userCardIds.take(1))
        assertAchievement(tma, "team_submit_1", 1, "COMPLETED_UNCLAIMED")
        assertAchievement(tma, "budget_team_1", 0, "LOCKED")

        mockMvc.perform(
            put("/api/v1/series/${fixture.seriesId}/leagues/MAIN/fantasy-team")
                .header("Authorization", tma)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"userCardIds":[${fixture.userCardIds[1]}]}"""),
        ).andExpect(status().isOk)
        assertAchievement(tma, "team_submit_5", 1, "IN_PROGRESS")

        createFantasyTeam(tma, fixture.seriesId, "BUDGET", fixture.userCardIds.take(1))
        assertAchievement(tma, "budget_team_1", 1, "COMPLETED_UNCLAIMED")
        assertAchievement(tma, "dual_league_1", 1, "COMPLETED_UNCLAIMED")

        jdbcTemplate.update(
            "UPDATE fantasy_team SET created_at = (SELECT tracking_started_at FROM achievement_definition WHERE code = 'team_submit_1') - interval '1 hour'",
        )
        mockMvc.perform(post("/api/v1/achievements/team_submit_5/claim").header("Authorization", tma))
            .andExpect(status().isBadRequest)
    }

    @Test
    @Order(20)
    fun `finalization sets finalizedAt and updates result achievements from leaderboard order`() {
        val auth = basicAuth("admin", "test-admin-secret")
        val fixture = createTeamFixture(auth, suffix = "results")
        val winnerTma = tmaAuth(fixture.telegramPlatformId, "Winner")
        val loserTelegramId = fixture.telegramPlatformId + 1
        val loserTma = tmaAuth(loserTelegramId, "Loser")
        giveCards(auth, loserTelegramId, fixture.templateIds)

        createFantasyTeam(winnerTma, fixture.seriesId, "MAIN", listOf(fixture.userCardIds[0]))
        createFantasyTeam(loserTma, fixture.seriesId, "MAIN", listOf(fixture.loserCardIds(auth, loserTelegramId)[0]))
        createFantasyTeam(winnerTma, fixture.seriesId, "BUDGET", listOf(fixture.userCardIds[0]))
        createFantasyTeam(loserTma, fixture.seriesId, "BUDGET", listOf(fixture.loserCardIds(auth, loserTelegramId)[1]))

        val winnerInternalId = internalUserId(fixture.telegramPlatformId)
        jdbcTemplate.update("UPDATE fantasy_team SET total_score = CASE WHEN telegram_user_id = ? THEN 100 ELSE 10 END", winnerInternalId)

        mockMvc.perform(post("/api/v1/admin/series/${fixture.seriesId}/finalize").header("Authorization", auth))
            .andExpect(status().isOk)

        assertSqlLong("SELECT COUNT(*) FROM series WHERE id = ${fixture.seriesId} AND finalized_at IS NOT NULL", 1)
        assertAchievement(winnerTma, "series_win_1", 2, "COMPLETED_UNCLAIMED")
        assertAchievement(winnerTma, "budget_win_1", 1, "COMPLETED_UNCLAIMED")
        assertAchievement(winnerTma, "top3_5", 2, "IN_PROGRESS")
    }

    @Test
    @Order(30)
    fun `pack open inserts event and updates pack and collection achievements`() {
        val auth = basicAuth("admin", "test-admin-secret")
        val packId = createAutoPack(auth, suffix = "pack-open", rarity = "EPIC")
        val telegramId = 910_000_030L
        val tma = tmaAuth(telegramId, "PackUser")

        mockMvc.perform(post("/api/v1/store/packs/$packId/buy").header("Authorization", tma))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.cards[0].rarity").value("EPIC"))

        assertSqlLong("SELECT COUNT(*) FROM user_card_pack_open_event WHERE telegram_user_id = ${internalUserId(telegramId)}", 1)
        assertSqlLong(
            """
            SELECT COUNT(*)
            FROM user_card uc
            JOIN card_template ct ON ct.id = uc.card_template_id
            WHERE uc.telegram_user_id = ${internalUserId(telegramId)}
              AND uc.deleted_at IS NULL
              AND ct.rarity = 'EPIC'
            """.trimIndent(),
            1,
        )
        assertAchievement(tma, "pack_open_1", 1, "COMPLETED_UNCLAIMED")
        assertAchievement(tma, "first_epic", 1, "COMPLETED_UNCLAIMED")
        assertAchievement(tma, "pack_epic_drop_1", 1, "COMPLETED_UNCLAIMED")
    }

    @Test
    @Order(31)
    fun `pack epic drop ignores old pack card after post launch open of same pack`() {
        val auth = basicAuth("admin", "test-admin-secret")
        val packId = createAutoPack(auth, suffix = "old-pack-epic", rarity = "COMMON")
        val telegramId = 910_000_031L
        val tma = tmaAuth(telegramId, "OldPackEpic")
        val tournamentId = jdbcTemplate.queryForObject("SELECT tournament_id FROM card_pack WHERE id = ?", Long::class.java, packId)!!
        val fantasyPlayerId = createTournamentPlayer(auth, tournamentId, 920_031_001L, "OldEpicPlayer")
        val epicTemplateId = createCardTemplate(auth, fantasyPlayerId, "EPIC")
        val oldCardId = giveCards(auth, telegramId, listOf(epicTemplateId)).single()
        jdbcTemplate.update(
            """
            UPDATE user_card
            SET source_card_pack_id = ?,
                acquired_at = (
                    SELECT tracking_started_at AT TIME ZONE 'UTC'
                    FROM achievement_definition
                    WHERE code = 'pack_epic_drop_1'
                ) - interval '1 hour'
            WHERE id = ?
            """.trimIndent(),
            packId,
            oldCardId,
        )
        jdbcTemplate.update(
            """
            UPDATE user_card_ownership_history
            SET acquired_at = (
                SELECT tracking_started_at AT TIME ZONE 'UTC'
                FROM achievement_definition
                WHERE code = 'pack_epic_drop_1'
            ) - interval '1 hour'
            WHERE user_card_id = ?
            """.trimIndent(),
            oldCardId,
        )

        mockMvc.perform(post("/api/v1/store/packs/$packId/buy").header("Authorization", tma))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.cards[0].rarity").value("COMMON"))

        assertAchievement(tma, "pack_open_1", 1, "COMPLETED_UNCLAIMED")
        assertAchievement(tma, "pack_epic_drop_1", 0, "LOCKED")
    }

    @Test
    @Order(32)
    fun `same player rarities requires all four rarities and lets user choose badge player`() {
        val auth = basicAuth("admin", "test-admin-secret")
        val tournamentId = createTournament(auth, "Favorite player")
        val defaultPlayerId = createTournamentPlayer(auth, tournamentId, 920_032_001L, "AlphaChoice")
        val favoritePlayerId = createTournamentPlayer(auth, tournamentId, 920_032_002L, "ZFavorite")
        val defaultTemplateIds = listOf("COMMON", "RARE", "EPIC", "LEGENDARY").map { rarity ->
            createCardTemplate(auth, defaultPlayerId, rarity)
        }
        val favoriteTemplateIds = listOf("COMMON", "RARE", "EPIC", "LEGENDARY").map { rarity ->
            createCardTemplate(auth, favoritePlayerId, rarity)
        }
        val telegramId = 910_000_032L
        val tma = tmaAuth(telegramId, "FavoriteCollector")

        giveCards(auth, telegramId, defaultTemplateIds.take(3))
        assertAchievement(tma, "same_player_3_rarities", 0, "LOCKED")

        giveCards(auth, telegramId, listOf(defaultTemplateIds[3]) + favoriteTemplateIds)

        val achievementsJson = mockMvc.perform(get("/api/v1/achievements").header("Authorization", tma))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val achievement = singleAchievement(achievementsJson, "same_player_3_rarities")
        assertThat((achievement["progressValue"] as Number).toInt()).isEqualTo(1)
        assertThat(achievement["state"]).isEqualTo("COMPLETED_UNCLAIMED")
        assertThat(achievement["title"]).isEqualTo("Любимый игрок: AlphaChoice")

        val internalId = internalUserId(telegramId)
        jdbcTemplate.update(
            """
            INSERT INTO user_achievement (telegram_user_id, achievement_id, progress_value, completed_at, claimed_at, updated_at)
            SELECT ?, id, 1, now(), now(), now()
            FROM achievement_definition
            WHERE code = 'same_player_3_rarities'
            """.trimIndent(),
            internalId,
        )
        jdbcTemplate.update(
            """
            INSERT INTO user_profile_featured_achievement (telegram_user_id, achievement_id, display_order)
            SELECT ?, id, 0
            FROM achievement_definition
            WHERE code = 'same_player_3_rarities'
            """.trimIndent(),
            internalId,
        )

        mockMvc.perform(get("/api/v1/me/profile-customization").header("Authorization", tma))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.availableFeaturedAchievements[0].title").value("Любимый игрок: AlphaChoice"))
            .andExpect(jsonPath("$.favoriteBadgePlayerOptions[*].fantasyPlayerId", containsInAnyOrder(defaultPlayerId.toInt(), favoritePlayerId.toInt())))
            .andExpect(jsonPath("$.favoriteBadgePlayerOptions[*].nickname", containsInAnyOrder("AlphaChoice", "ZFavorite")))

        mockMvc.perform(
            put("/api/v1/me/profile-customization")
                .header("Authorization", tma)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"profileFrameCode":null,"featuredAchievementCodes":["same_player_3_rarities"],"favoriteBadgeFantasyPlayerId":$favoritePlayerId}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.favoriteBadgeFantasyPlayerId").value(favoritePlayerId.toInt()))
            .andExpect(jsonPath("$.availableFeaturedAchievements[0].title").value("Любимый игрок: ZFavorite"))

        val selectedAchievementsJson = mockMvc.perform(get("/api/v1/achievements").header("Authorization", tma))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        assertThat(singleAchievement(selectedAchievementsJson, "same_player_3_rarities")["title"])
            .isEqualTo("Любимый игрок: ZFavorite")

        mockMvc.perform(get("/api/v1/players/$telegramId/profile").header("Authorization", tma))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.featuredAchievements[0].title").value("Любимый игрок: ZFavorite"))

        mockMvc.perform(
            put("/api/v1/me/profile-customization")
                .header("Authorization", tma)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"profileFrameCode":null,"featuredAchievementCodes":["same_player_3_rarities"],"favoriteBadgeFantasyPlayerId":999999999}"""),
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    @Order(40)
    fun `claim rejects incomplete achievement and rewards completed achievement once`() {
        val auth = basicAuth("admin", "test-admin-secret")
        val fixture = createTeamFixture(auth, suffix = "claim")
        val tma = tmaAuth(fixture.telegramPlatformId, "ClaimUser")

        mockMvc.perform(post("/api/v1/achievements/team_submit_5/claim").header("Authorization", tma))
            .andExpect(status().isBadRequest)

        createFantasyTeam(tma, fixture.seriesId, "MAIN", fixture.userCardIds.take(1))
        mockMvc.perform(post("/api/v1/achievements/team_submit_1/claim").header("Authorization", tma))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.achievementCode").value("team_submit_1"))
            .andExpect(jsonPath("$.fantikiDelta").value(10))
            .andExpect(jsonPath("$.newFantikiBalance", greaterThan(1000)))

        mockMvc.perform(post("/api/v1/achievements/team_submit_1/claim").header("Authorization", tma))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.fantikiDelta").value(0))

        assertSqlLong(
            """
            SELECT COUNT(*)
            FROM fantiki_transaction ft
            JOIN telegram_user u ON u.id = ft.telegram_user_id
            WHERE u.telegram_id = ${fixture.telegramPlatformId}
              AND ft.reason = 'ACHIEVEMENT_REWARD'
              AND ft.amount = 10
            """.trimIndent(),
            1,
        )
    }

    @Test
    @Order(41)
    fun `claim writes badge style cosmetic unlock`() {
        val auth = basicAuth("admin", "test-admin-secret")
        val fixture = createTeamFixture(auth, suffix = "badge-claim")
        val winnerTma = tmaAuth(fixture.telegramPlatformId, "BadgeWinner")
        val loserTelegramId = fixture.telegramPlatformId + 1
        val loserTma = tmaAuth(loserTelegramId, "BadgeLoser")
        val loserCardIds = giveCards(auth, loserTelegramId, fixture.templateIds)

        createFantasyTeam(winnerTma, fixture.seriesId, "MAIN", listOf(fixture.userCardIds[0]))
        createFantasyTeam(loserTma, fixture.seriesId, "MAIN", listOf(loserCardIds[0]))
        val winnerInternalId = internalUserId(fixture.telegramPlatformId)
        jdbcTemplate.update("UPDATE fantasy_team SET total_score = CASE WHEN telegram_user_id = ? THEN 100 ELSE 10 END", winnerInternalId)
        jdbcTemplate.update(
            """
            DELETE FROM achievement_reward ar
            USING achievement_definition d
            WHERE ar.achievement_id = d.id
              AND d.code = 'series_win_1'
              AND ar.reward_type = 'CARD_CHOICE_ROLL'
            """.trimIndent(),
        )

        mockMvc.perform(post("/api/v1/admin/series/${fixture.seriesId}/finalize").header("Authorization", auth))
            .andExpect(status().isOk)
        mockMvc.perform(post("/api/v1/achievements/series_win_1/claim").header("Authorization", winnerTma))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.cosmeticUnlocks[0].type").value("BADGE_STYLE"))
            .andExpect(jsonPath("$.cosmeticUnlocks[0].code").value("series_winner"))

        assertSqlLong(
            """
            SELECT COUNT(*)
            FROM user_cosmetic_unlock
            WHERE telegram_user_id = $winnerInternalId
              AND cosmetic_type = 'BADGE_STYLE'
              AND cosmetic_code = 'series_winner'
              AND source_code = 'series_win_1'
            """.trimIndent(),
            1,
        )
    }

    @Test
    @Order(50)
    fun `admin dry run evaluates current aggregates and ignores old finalized series`() {
        val auth = basicAuth("admin", "test-admin-secret")
        val liabilityFixture = createTeamFixture(auth, suffix = "dry-run-liability")
        val liabilityTma = tmaAuth(liabilityFixture.telegramPlatformId, "DryRunLiability")
        createFantasyTeam(liabilityTma, liabilityFixture.seriesId, "MAIN", listOf(liabilityFixture.userCardIds[0]))

        val beforeOldSeriesJson = mockMvc.perform(
            post("/api/v1/admin/achievements/backfill/dry-run")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val seriesWinBefore = dryRunInstantCompleted(beforeOldSeriesJson, "series_win_1")

        val fixture = createTeamFixture(auth, suffix = "dry-run-old-finalized")
        val tma = tmaAuth(fixture.telegramPlatformId, "DryRunOld")
        createFantasyTeam(tma, fixture.seriesId, "MAIN", listOf(fixture.userCardIds[0]))

        jdbcTemplate.update(
            """
            UPDATE fantasy_team
            SET created_at = (SELECT tracking_started_at FROM achievement_definition WHERE code = 'series_win_1') - interval '1 hour',
                total_score = 100
            WHERE telegram_user_id = ?
            """.trimIndent(),
            internalUserId(fixture.telegramPlatformId),
        )
        jdbcTemplate.update(
            """
            UPDATE series
            SET status = 'FINISHED',
                finalized = TRUE,
                finalized_at = (SELECT tracking_started_at FROM achievement_definition WHERE code = 'series_win_1')
            WHERE id = ?
            """.trimIndent(),
            fixture.seriesId,
        )

        val response = mockMvc.perform(
            post("/api/v1/admin/achievements/backfill/dry-run")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.instantCompleted", greaterThan(0)))
            .andExpect(jsonPath("$.instantFantikiLiability", greaterThan(0)))
            .andReturn().response.contentAsString

        val rows = JsonPath.parse(response).read<List<Map<String, Any?>>>("$.rows")
        val teamSubmit1 = rows.single { it["code"] == "team_submit_1" }
        check((teamSubmit1["instantCompleted"] as Number).toLong() >= 1L) {
            "Expected team_submit_1 liability in $response"
        }
        val seriesWin1 = rows.single { it["code"] == "series_win_1" }
        check((seriesWin1["instantCompleted"] as Number).toLong() == seriesWinBefore) {
            "Old finalized series must not increase series_win_1 dry-run: before=$seriesWinBefore response=$response"
        }
    }

    @Test
    @Order(60)
    fun `card choice claim creates pending choice and select finalizes without duplicate cards`() {
        val auth = basicAuth("admin", "test-admin-secret")
        createAutoPack(auth, suffix = "choice-lifecycle-pool", rarity = "COMMON", playerCount = 3)
        configureAchievementRewards(
            code = "team_submit_5",
            rewardsSql = """
                ('FANTIKI', 7, NULL, NULL::jsonb, 10),
                ('PROFILE_FRAME', NULL, 'choice_frame', NULL::jsonb, 20),
                ('CARD_CHOICE_ROLL', NULL, NULL, '{"rarity":"COMMON","count":1,"options":3,"source":"ACTIVE_PACKS","skinCode":"budget_edition"}'::jsonb, 30)
            """.trimIndent(),
        )
        jdbcTemplate.update("UPDATE achievement_definition SET target_value = 1 WHERE code = 'team_submit_5'")
        val fixture = createTeamFixture(auth, suffix = "choice-lifecycle")
        val tma = tmaAuth(fixture.telegramPlatformId, "ChoiceLifecycle")
        createFantasyTeam(tma, fixture.seriesId, "MAIN", fixture.userCardIds.take(1))

        val firstClaim = mockMvc.perform(post("/api/v1/achievements/team_submit_5/claim").header("Authorization", tma))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.claimedAt").doesNotExist())
            .andExpect(jsonPath("$.fantikiDelta").value(0))
            .andExpect(jsonPath("$.pendingChoices", hasSize<Any>(1)))
            .andExpect(jsonPath("$.pendingChoices[0].options", hasSize<Any>(3)))
            .andExpect(jsonPath("$.pendingChoices[0].options[0].skinCode").value("budget_edition"))
            .andReturn().response.contentAsString
        val firstJson = JsonPath.parse(firstClaim)
        val rewardId = firstJson.read<Number>("$.pendingChoices[0].rewardId").toLong()
        val firstOptionIds = firstJson.read<List<String>>("$.pendingChoices[0].options[*].optionId")
        assertSqlLong("SELECT COUNT(*) FROM user_achievement_card_choice WHERE reward_id = $rewardId", 1)

        val repeatClaim = mockMvc.perform(post("/api/v1/achievements/team_submit_5/claim").header("Authorization", tma))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.claimedAt").doesNotExist())
            .andExpect(jsonPath("$.pendingChoices", hasSize<Any>(1)))
            .andReturn().response.contentAsString
        val repeatOptionIds = JsonPath.parse(repeatClaim).read<List<String>>("$.pendingChoices[0].options[*].optionId")
        assertThat(repeatOptionIds).containsExactlyElementsOf(firstOptionIds)
        assertSqlLong("SELECT COUNT(*) FROM user_achievement_card_choice WHERE reward_id = $rewardId", 1)

        val beforeCards = achievementRewardCardCount(fixture.telegramPlatformId)
        val selectedOptionId = firstOptionIds.first()
        val selectResponse = mockMvc.perform(
            post("/api/v1/achievements/team_submit_5/choices/$rewardId/select")
                .header("Authorization", tma)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"optionIds":["$selectedOptionId"]}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.claimedAt").isString)
            .andExpect(jsonPath("$.fantikiDelta").value(7))
            .andExpect(jsonPath("$.grantedCards", hasSize<Any>(1)))
            .andExpect(jsonPath("$.grantedCards[0].skinCode").value("budget_edition"))
            .andExpect(jsonPath("$.pendingChoices", hasSize<Any>(0)))
            .andReturn().response.contentAsString
        val grantedCardId = JsonPath.parse(selectResponse).read<Number>("$.grantedCards[0].userCardId").toLong()
        assertThat(achievementRewardCardCount(fixture.telegramPlatformId)).isEqualTo(beforeCards + 1)
        assertSqlLong(
            """
            SELECT COUNT(*)
            FROM user_card uc
            JOIN card_skin cs ON cs.id = uc.card_skin_id
            WHERE uc.id = $grantedCardId
              AND cs.code = 'budget_edition'
            """.trimIndent(),
            1,
        )

        mockMvc.perform(
            post("/api/v1/achievements/team_submit_5/choices/$rewardId/select")
                .header("Authorization", tma)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"optionIds":["$selectedOptionId"]}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.fantikiDelta").value(0))
            .andExpect(jsonPath("$.grantedCards", hasSize<Any>(0)))
        mockMvc.perform(post("/api/v1/achievements/team_submit_5/claim").header("Authorization", tma))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.fantikiDelta").value(0))
            .andExpect(jsonPath("$.grantedCards", hasSize<Any>(0)))
        assertThat(achievementRewardCardCount(fixture.telegramPlatformId)).isEqualTo(beforeCards + 1)

        val snapshot = rewardSnapshot(fixture.telegramPlatformId, "team_submit_5")
        val fantiki = snapshot.single { it["type"] == "FANTIKI" }
        val cosmetic = snapshot.single { it["type"] == "PROFILE_FRAME" }
        val choice = snapshot.single { it["type"] == "CARD_CHOICE_ROLL" }
        assertThat(cardIds(fantiki)).isEmpty()
        assertThat(cardIds(cosmetic)).isEmpty()
        assertThat(cardIds(choice)).containsExactly(grantedCardId)
    }

    @Test
    @Order(61)
    fun `random card snapshot maps cards to their reward only`() {
        val auth = basicAuth("admin", "test-admin-secret")
        createAutoPack(auth, suffix = "random-snapshot-pool", rarity = "COMMON", playerCount = 4)
        configureAchievementRewards(
            code = "team_submit_15",
            rewardsSql = """
                ('FANTIKI', 5, NULL, NULL::jsonb, 10),
                ('RANDOM_CARD', NULL, NULL, '{"rarity":"COMMON","count":1,"source":"ACTIVE_PACKS"}'::jsonb, 20),
                ('PROFILE_FRAME', NULL, 'random_frame', NULL::jsonb, 30),
                ('RANDOM_CARD', NULL, NULL, '{"rarity":"COMMON","count":1,"source":"ACTIVE_PACKS"}'::jsonb, 40)
            """.trimIndent(),
        )
        jdbcTemplate.update("UPDATE achievement_definition SET target_value = 1 WHERE code = 'team_submit_15'")
        val fixture = createTeamFixture(auth, suffix = "random-snapshot")
        val tma = tmaAuth(fixture.telegramPlatformId, "RandomSnapshot")
        createFantasyTeam(tma, fixture.seriesId, "MAIN", fixture.userCardIds.take(1))

        val response = mockMvc.perform(post("/api/v1/achievements/team_submit_15/claim").header("Authorization", tma))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.grantedCards", hasSize<Any>(2)))
            .andReturn().response.contentAsString
        val responseCardIds = JsonPath.parse(response).read<List<Number>>("$.grantedCards[*].userCardId").map { it.toLong() }
        val snapshot = rewardSnapshot(fixture.telegramPlatformId, "team_submit_15")
        val randomEntries = snapshot.filter { it["type"] == "RANDOM_CARD" }
        assertThat(randomEntries).hasSize(2)
        val firstRandomIds = cardIds(randomEntries[0])
        val secondRandomIds = cardIds(randomEntries[1])
        assertThat(firstRandomIds).hasSize(1)
        assertThat(secondRandomIds).hasSize(1)
        assertThat(firstRandomIds).doesNotContainAnyElementsOf(secondRandomIds)
        assertThat((firstRandomIds + secondRandomIds).toSet()).isEqualTo(responseCardIds.toSet())
        assertThat(cardIds(snapshot.single { it["type"] == "FANTIKI" })).isEmpty()
        assertThat(cardIds(snapshot.single { it["type"] == "PROFILE_FRAME" })).isEmpty()
    }

    private fun dryRunInstantCompleted(response: String, code: String): Long {
        val rows = JsonPath.parse(response).read<List<Map<String, Any?>>>("$.rows")
        return (rows.single { it["code"] == code }["instantCompleted"] as Number).toLong()
    }

    private fun assertAchievement(tma: String, code: String, progressValue: Int, state: String) {
        val json = mockMvc.perform(get("/api/v1/achievements").header("Authorization", tma))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val item = singleAchievement(json, code)
        check((item["progressValue"] as Number).toInt() == progressValue) {
            "Expected $code progress $progressValue, got ${item["progressValue"]}"
        }
        check(item["state"] == state) { "Expected $code state $state, got ${item["state"]}" }
    }

    private fun singleAchievement(json: String, code: String): Map<String, Any?> {
        val items = JsonPath.parse(json).read<List<Map<String, Any?>>>("$..achievements[?(@.code == '$code')]")
        check(items.size == 1) { "Expected one achievement $code in $json" }
        return items.first()
    }

    private fun createTeamFixture(auth: String, suffix: String): TeamFixture {
        val tournamentId = createTournament(auth, "Achievements $suffix")
        val fpIds = (1..2).map { index ->
            createTournamentPlayer(auth, tournamentId, 910_100_000L + suffix.hashCode().toLong().mod(100_000L) * 10 + index, "P$suffix$index")
        }
        val templateIds = fpIds.map { createCardTemplate(auth, it, "COMMON") }
        val telegramId = 910_000_000L + suffix.hashCode().toLong().mod(100_000L)
        val userCardIds = giveCards(auth, telegramId, templateIds)
        val seriesId = createSeries(auth, tournamentId, "Series $suffix")
        val tournamentPlayerIds = fpIds.map { fantasyPlayerId ->
            jdbcTemplate.queryForObject(
                "SELECT id FROM tournament_player WHERE tournament_id = ? AND fantasy_player_id = ?",
                Long::class.java,
                tournamentId,
                fantasyPlayerId,
            )!!
        }
        mockMvc.perform(
            post("/api/v1/admin/series/$seriesId/players")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"tournamentPlayerIds":[${tournamentPlayerIds.joinToString(",")}]}"""),
        ).andExpect(status().isOk)
        return TeamFixture(telegramId, seriesId, templateIds, userCardIds)
    }

    private fun TeamFixture.loserCardIds(auth: String, loserTelegramId: Long): List<Long> =
        giveCards(auth, loserTelegramId, templateIds)

    private fun createTournament(auth: String, name: String): Long {
        val json = mockMvc.perform(
            post("/api/v1/admin/tournaments")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"$name","status":"DRAFT"}"""),
        ).andExpect(status().isOk).andReturn().response.contentAsString
        return JsonPath.parse(json).read<Number>("$.id").toLong()
    }

    private fun createTournamentPlayer(auth: String, tournamentId: Long, polemicaUserId: Long, nickname: String): Long {
        val json = mockMvc.perform(
            post("/api/v1/admin/tournaments/$tournamentId/players")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"polemicaUserId":$polemicaUserId,"nickname":"$nickname"}"""),
        ).andExpect(status().isOk).andReturn().response.contentAsString
        return JsonPath.parse(json).read<Number>("$.fantasyPlayerId").toLong()
    }

    private fun createCardTemplate(auth: String, fantasyPlayerId: Long, rarity: String): Long {
        val json = mockMvc.perform(
            post("/api/v1/admin/card-templates")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"fantasyPlayerId":$fantasyPlayerId,"rarity":"$rarity"}"""),
        ).andExpect(status().isOk).andReturn().response.contentAsString
        return JsonPath.parse(json).read<Number>("$.id").toLong()
    }

    private fun giveCards(auth: String, telegramId: Long, templateIds: List<Long>): List<Long> {
        val json = mockMvc.perform(
            post("/api/v1/admin/users/$telegramId/give-cards")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"cardTemplateIds":[${templateIds.joinToString(",")}]}"""),
        ).andExpect(status().isOk).andReturn().response.contentAsString
        return JsonPath.parse(json).read<List<Number>>("$[*].id").map { it.toLong() }
    }

    private fun createSeries(auth: String, tournamentId: Long, name: String): Long {
        val json = mockMvc.perform(
            post("/api/v1/admin/tournaments/$tournamentId/series")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"$name","namePrefix":"$name","status":"UPCOMING",
                    "startsAt":"2030-06-01T12:00:00Z",
                    "teamDeadline":"2030-06-15T12:00:00Z"}
                    """.trimIndent(),
                ),
        ).andExpect(status().isOk).andReturn().response.contentAsString
        return JsonPath.parse(json).read<Number>("$.id").toLong()
    }

    private fun createFantasyTeam(tma: String, seriesId: Long, leagueCode: String, userCardIds: List<Long>) {
        mockMvc.perform(
            post("/api/v1/series/$seriesId/leagues/$leagueCode/fantasy-team")
                .header("Authorization", tma)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"userCardIds":[${userCardIds.joinToString(",")}]}"""),
        ).andExpect(status().isOk)
    }

    private fun createAutoPack(auth: String, suffix: String, rarity: String, playerCount: Int = 1): Long {
        val tournamentId = createTournament(auth, "Pack T $suffix")
        repeat(playerCount) { index ->
            createTournamentPlayer(
                auth,
                tournamentId,
                920_000_000L + suffix.hashCode().toLong().mod(100_000L) * 10 + index,
                "PackP$suffix$index",
            )
        }
        val json = mockMvc.perform(
            post("/api/v1/admin/card-packs")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"Pack $suffix","tournamentId":$tournamentId,"active":true,"autoGenerated":true,
                    "priceFantiki":0,"freeOpensPerUser":0,"maxOpensPerUser":0,"useAllTournamentPlayers":true,
                    "rarityConfigs":[{"rarity":"$rarity","cardsCount":1}]}
                    """.trimIndent(),
                ),
        ).andExpect(status().isOk).andReturn().response.contentAsString
        return JsonPath.parse(json).read<Number>("$.id").toLong()
    }

    private fun configureAchievementRewards(code: String, rewardsSql: String) {
        jdbcTemplate.update(
            """
            DELETE FROM achievement_reward
            WHERE achievement_id = (SELECT id FROM achievement_definition WHERE code = ?)
            """.trimIndent(),
            code,
        )
        jdbcTemplate.update(
            """
            INSERT INTO achievement_reward (achievement_id, reward_type, amount, reward_code, metadata, display_order)
            SELECT d.id, r.reward_type, r.amount, r.reward_code, r.metadata, r.display_order
            FROM achievement_definition d
            CROSS JOIN (
                VALUES
                $rewardsSql
            ) AS r(reward_type, amount, reward_code, metadata, display_order)
            WHERE d.code = ?
            """.trimIndent(),
            code,
        )
    }

    private fun achievementRewardCardCount(telegramId: Long): Long =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM user_card_ownership_history h
            JOIN telegram_user u ON u.id = h.telegram_user_id
            WHERE u.telegram_id = ?
              AND h.acquisition_type = 'ACHIEVEMENT_REWARD'
            """.trimIndent(),
            Long::class.java,
            telegramId,
        )!!

    private fun rewardSnapshot(telegramId: Long, code: String): List<Map<String, Any?>> {
        val json = jdbcTemplate.queryForObject(
            """
            SELECT ua.reward_snapshot
            FROM user_achievement ua
            JOIN telegram_user u ON u.id = ua.telegram_user_id
            JOIN achievement_definition d ON d.id = ua.achievement_id
            WHERE u.telegram_id = ?
              AND d.code = ?
            """.trimIndent(),
            String::class.java,
            telegramId,
            code,
        )!!
        return JsonPath.parse(json).read("$")
    }

    private fun cardIds(snapshotEntry: Map<String, Any?>): List<Long> =
        (snapshotEntry["grantedCardIds"] as? List<*>)
            .orEmpty()
            .map { (it as Number).toLong() }

    private fun internalUserId(telegramId: Long): Long =
        jdbcTemplate.queryForObject("SELECT id FROM telegram_user WHERE telegram_id = ?", Long::class.java, telegramId)!!

    private fun assertSqlLong(sql: String, expected: Long) {
        val actual = jdbcTemplate.queryForObject(sql, Long::class.java)
        check(actual == expected) { "Expected $expected for SQL [$sql], got $actual" }
    }

    private data class TeamFixture(
        val telegramPlatformId: Long,
        val seriesId: Long,
        val templateIds: List<Long>,
        val userCardIds: List<Long>,
    )

    private fun tmaAuth(telegramId: Long, firstName: String): String {
        val initData = buildSignedInitData(
            botToken = "test-token",
            authDate = Instant.now().epochSecond,
            userJson = """{"id":$telegramId,"first_name":"$firstName"}""",
        )
        return "tma $initData"
    }

    private fun buildSignedInitData(botToken: String, authDate: Long, userJson: String): String {
        val userEncoded = URLEncoder.encode(userJson, StandardCharsets.UTF_8)
        val pairs = linkedMapOf(
            "auth_date" to authDate.toString(),
            "user" to userJson,
        )
        val dataCheckString = pairs.keys.sorted().joinToString("\n") { k -> "$k=${pairs[k]}" }
        val secretKey = hmacSha256("WebAppData".toByteArray(StandardCharsets.UTF_8), botToken.toByteArray(StandardCharsets.UTF_8))
        val hashBytes = hmacSha256(secretKey, dataCheckString.toByteArray(StandardCharsets.UTF_8))
        val hashHex = hashBytes.joinToString("") { b -> "%02x".format(b) }
        return "auth_date=$authDate&user=$userEncoded&hash=$hashHex"
    }

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    companion object {
        @JvmField
        @Container
        @ServiceConnection
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")

        private fun basicAuth(user: String, password: String): String {
            val token = Base64.getEncoder().encodeToString("$user:$password".toByteArray(Charsets.UTF_8))
            return "Basic $token"
        }

        private const val EXPECTED_ACHIEVEMENT_COUNT = 87L
    }
}
