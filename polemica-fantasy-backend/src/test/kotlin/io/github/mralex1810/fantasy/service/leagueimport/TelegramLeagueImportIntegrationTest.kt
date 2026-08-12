package io.github.mralex1810.fantasy.service.leagueimport

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.mralex1810.fantasy.config.TelegramLeagueImportProperties
import io.github.mralex1810.fantasy.dto.admin.request.CreateSeriesRequest
import io.github.mralex1810.fantasy.dto.admin.request.AssignSeriesPlayersRequest
import io.github.mralex1810.fantasy.dto.internal.TelegramLeagueImportEventRequest
import io.github.mralex1810.fantasy.entity.SeriesStatus
import io.github.mralex1810.fantasy.entity.SeriesGame
import io.github.mralex1810.fantasy.entity.Tournament
import io.github.mralex1810.fantasy.entity.TournamentKind
import io.github.mralex1810.fantasy.entity.TournamentStatus
import io.github.mralex1810.fantasy.repository.LeagueImportRepository
import io.github.mralex1810.fantasy.repository.SeriesRepository
import io.github.mralex1810.fantasy.repository.SeriesGameRepository
import io.github.mralex1810.fantasy.repository.TournamentRepository
import io.github.mralex1810.fantasy.schedule.LeagueImportActionScheduler
import io.github.mralex1810.fantasy.schedule.LeagueImportJobScheduler
import io.github.mralex1810.fantasy.service.SeriesService
import io.github.mralex1810.fantasy.service.SeriesCompletionService
import io.github.mralex1810.fantasy.service.SeriesScoringContextFingerprintService
import io.github.mralex1810.fantasy.service.SeriesGameSelectorFingerprintService
import io.github.mralex1810.fantasy.config.LeagueImportAutomationMode
import com.github.mafia.vyasma.polemica.library.model.game.PolemicaGame
import com.github.mafia.vyasma.polemica.library.model.game.PolemicaGameResult
import com.github.mafia.vyasma.polemica.library.model.game.PolemicaPlayer
import com.github.mafia.vyasma.polemica.library.model.game.PolemicaUser
import com.github.mafia.vyasma.polemica.library.model.game.Position
import com.github.mafia.vyasma.polemica.library.model.game.Role
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

@SpringBootTest(
    properties = [
        "telegram.league-import.enabled=true",
        "telegram.league-import.ingest-enabled=true",
        "telegram.league-import.operator-notifications-enabled=false",
        "telegram.league-import.callback-enabled=true",
        "telegram.league-import.production-writes-enabled=true",
        "telegram.league-import.policy-generation=test-manual-v1",
        "telegram.league-import.source-channel-peer-id=-1001112223334",
        "telegram.league-import.operator-chat-id=-5166305654",
        "telegram.league-import.ingest-key-id=test-current",
        "telegram.league-import.ingest-current-secret=test-ingest-secret",
        "telegram.league-import.callback-signing-secret=test-callback-secret",
        "telegram.support.webhook-secret=test-webhook-secret",
        "telegram.league-import.policies.lp.enabled=true",
        "telegram.league-import.policies.lp.tournament-id=1",
        "telegram.league-import.policies.lp.name-template=Лига Претендентов: Серия %d.",
        "telegram.league-import.policies.lp.name-prefix=Лига Претендентов",
        "telegram.league-import.policies.lp.create-mode=MANUAL",
    ],
)
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class TelegramLeagueImportIntegrationTest {

    @Autowired private lateinit var properties: TelegramLeagueImportProperties
    @Autowired private lateinit var ingestService: LeagueImportIngestService
    @Autowired private lateinit var callbackService: TelegramLeagueImportCallbackService
    @Autowired private lateinit var tokenCodec: LeagueImportActionTokenCodec
    @Autowired private lateinit var createService: LeagueImportCreateService
    @Autowired private lateinit var importRepository: LeagueImportRepository
    @Autowired private lateinit var tournamentRepository: TournamentRepository
    @Autowired private lateinit var seriesRepository: SeriesRepository
    @Autowired private lateinit var seriesGameRepository: SeriesGameRepository
    @Autowired private lateinit var seriesService: SeriesService
    @Autowired private lateinit var completionService: SeriesCompletionService
    @Autowired private lateinit var scoringContextFingerprintService: SeriesScoringContextFingerprintService
    @Autowired private lateinit var selectorFingerprintService: SeriesGameSelectorFingerprintService
    @Autowired private lateinit var resultProcessingService: LeagueImportResultProcessingService
    @Autowired private lateinit var legacyLinkService: LeagueImportLegacyLinkService
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var transactionManager: PlatformTransactionManager

    private lateinit var tournament: Tournament

    @BeforeEach
    fun createConfiguredTournament() {
        tournament = tournamentRepository.saveAndFlush(
            Tournament(
                name = "League import test ${messageSequence.incrementAndGet()}",
                status = TournamentStatus.ACTIVE,
                kind = TournamentKind.STANDALONE,
            ),
        )
        properties.policies.lp.tournamentId = tournament.id!!
    }

    @Test
    fun `ingest replay is idempotent and newer revision invalidates old action and outbox`() {
        val messageId = messageSequence.incrementAndGet()
        val first = request(messageId, 1, announcement(41, "11 августа 2030"))
        val deliveryId = UUID.randomUUID()

        val accepted = ingestService.ingest("test-current", deliveryId, Instant.now(), first)
        val itemId = accepted.itemId!!
        val firstAction = actionId(itemId, "CREATE_PREVIEW")
        assertEquals("PREVIEW_PENDING", accepted.state)
        assertEquals(1, count("league_import_revision", "item_id", itemId))
        assertEquals(1, count("league_import_action", "item_id", itemId))
        assertEquals(1, count("league_import_operator_outbox", "item_id", itemId))

        val replay = ingestService.ingest("test-current", deliveryId, Instant.now(), first)
        assertTrue(replay.duplicate)
        assertEquals(1, count("league_import_delivery", "item_id", itemId))
        assertEquals(1, count("league_import_revision", "item_id", itemId))
        assertEquals(1, count("league_import_action", "item_id", itemId))

        assertThrows(IllegalArgumentException::class.java) {
            ingestService.ingest("test-current", deliveryId, Instant.now(), first.copy(classification = "RESULT"))
        }

        val edited = request(messageId, 2, announcement(42, "12 августа 2030"), editedAt = Instant.now())
        val editResult = ingestService.ingest("test-current", UUID.randomUUID(), Instant.now(), edited)

        assertEquals(itemId, editResult.itemId)
        assertEquals("STALE", importRepository.findAction(firstAction)!!.status)
        assertEquals("SUPERSEDED", stringValue("SELECT status FROM league_import_operator_outbox WHERE action_id=?", firstAction))
        assertEquals(2, count("league_import_revision", "item_id", itemId))
        assertEquals(1, intValue("SELECT count(*) FROM league_import_revision WHERE item_id=? AND is_current", itemId))
        assertEquals(2, intValue("SELECT current_revision FROM league_import_item WHERE id=?", itemId))
        assertEquals("NOTIFY_PENDING", stringValue("SELECT status FROM league_import_action WHERE item_id=? AND source_revision=2", itemId))

        val actionBeforeWorkerReset = actionId(itemId, "CREATE_PREVIEW")
        val resetWorkerEdit = request(messageId, 1, announcement(43, "13 августа 2030"), editedAt = Instant.now())
            .copy(sourceVersion = "telethon-v2")
        ingestService.ingest("test-current", UUID.randomUUID(), Instant.now(), resetWorkerEdit)

        assertEquals("STALE", importRepository.findAction(actionBeforeWorkerReset)!!.status)
        assertEquals(1, intValue("SELECT current_revision FROM league_import_item WHERE id=?", itemId))
        assertEquals(3, count("league_import_revision", "item_id", itemId))
        assertEquals(sha256(resetWorkerEdit.rawText), stringValue("SELECT current_content_hash FROM league_import_item WHERE id=?", itemId))
    }

    @Test
    fun `callback requires exact chat and recorded message and confirm requires same actor`() {
        val itemId = ingest(43)
        val previewId = actionId(itemId, "CREATE_PREVIEW")
        importRepository.markActionOffered(previewId, 7001)
        val previewToken = tokenCodec.encode(previewId)

        callbackService.tryHandle(callback(101, "wrong-chat", previewToken, 42, properties.operatorChatId - 1, 7001))
        callbackService.tryHandle(callback(102, "wrong-message", previewToken, 42, properties.operatorChatId, 7002))
        assertEquals("OFFERED", importRepository.findAction(previewId)!!.status)
        assertEquals(0, intValue("SELECT count(*) FROM league_import_action WHERE item_id=? AND action_type='CREATE_CONFIRM'", itemId))

        val previewResult = callbackService.tryHandle(callback(103, "preview-ok", previewToken, 42, properties.operatorChatId, 7001))
        assertTrue(previewResult.handled)
        assertEquals("CALLBACK_RECEIVED", importRepository.findAction(previewId)!!.status)
        val confirmId = actionId(itemId, "CREATE_CONFIRM")
        assertEquals(42L, importRepository.findAction(confirmId)!!.boundActorTelegramId)
        importRepository.markActionOffered(confirmId, 7003)
        val confirmToken = tokenCodec.encode(confirmId)

        callbackService.tryHandle(callback(104, "confirm-wrong-actor", confirmToken, 43, properties.operatorChatId, 7003))
        assertEquals("OFFERED", importRepository.findAction(confirmId)!!.status)

        callbackService.tryHandle(callback(105, "confirm-ok", confirmToken, 42, properties.operatorChatId, 7003))
        val confirmed = importRepository.findAction(confirmId)!!
        assertEquals("CREATE_PENDING", confirmed.status)
        assertEquals(42L, confirmed.actorTelegramId)
        assertEquals(105L, longValue("SELECT callback_update_id FROM league_import_action WHERE id=?", confirmId))

        val expiringItemId = ingest(46)
        val expiringActionId = actionId(expiringItemId, "CREATE_PREVIEW")
        importRepository.markActionOffered(expiringActionId, 7004)
        jdbc.update("UPDATE league_import_operator_outbox SET status='DELIVERED' WHERE action_id=?", expiringActionId)
        jdbc.update("UPDATE league_import_action SET expires_at=now()-interval '1 second' WHERE id=?", expiringActionId)
        callbackService.tryHandle(
            callback(106, "expired", tokenCodec.encode(expiringActionId), 42, properties.operatorChatId, 7004),
        )
        assertEquals("EXPIRED", importRepository.findAction(expiringActionId)!!.status)
        assertEquals(2, intValue("SELECT count(*) FROM league_import_action WHERE item_id=? AND action_type='CREATE_PREVIEW'", expiringItemId))
        assertEquals(1, intValue("SELECT count(*) FROM league_import_operator_outbox WHERE item_id=? AND event_type='CREATE_PREVIEW' AND status='PENDING'", expiringItemId))
    }

    @Test
    fun `manual action is cancelled when policy generation changes`() {
        val itemId = ingest(47)
        val previewId = actionId(itemId, "CREATE_PREVIEW")
        importRepository.markActionOffered(previewId, 7101)
        val previousGeneration = properties.policyGeneration
        try {
            properties.policyGeneration = "test-manual-v2"
            LeagueImportActionScheduler(properties, importRepository, createService, transactionManager).process()

            assertEquals("CANCELLED", importRepository.findAction(previewId)!!.status)
            assertEquals(
                1,
                intValue(
                    "SELECT count(*) FROM league_import_operator_outbox WHERE action_id=? AND event_type='AUTO_CANCELLED'",
                    previewId,
                ),
            )
            val result = callbackService.tryHandle(
                callback(107, "stale-generation", tokenCodec.encode(previewId), 42, properties.operatorChatId, 7101),
            )
            assertTrue(result.answer.contains("устарело"))
        } finally {
            properties.policyGeneration = previousGeneration
        }
    }

    @Test
    fun `automatic write waits for delivered pending notification and source edit clears lease`() {
        val previousMode = properties.policies.lp.createMode
        val previousCutover = properties.automationCutoverAt
        val previousNotifications = properties.operatorNotificationsEnabled
        try {
            properties.operatorNotificationsEnabled = true
            properties.policies.lp.createMode = LeagueImportAutomationMode.AUTOMATIC
            properties.automationCutoverAt = Instant.EPOCH
            val messageId = messageSequence.incrementAndGet()
            val original = request(messageId, 1, announcement(48, "11 августа 2030"))
            val itemId = ingestService.ingest("test-current", UUID.randomUUID(), Instant.now(), original).itemId!!
            val jobId = jdbc.queryForObject(
                "SELECT id FROM league_import_job WHERE item_id=? AND operation='CREATE'",
                UUID::class.java,
                itemId,
            )!!
            val outboxId = longValue(
                "SELECT pending_notification_outbox_id FROM league_import_job WHERE id=?",
                jobId,
            )
            assertEquals(null, importRepository.findJob(jobId)!!.availableAt)
            assertEquals(null, importRepository.findJob(jobId)!!.notificationDeliveredAt)

            importRepository.finishOutbox(outboxId, delivered = true, messageId = 9901)
            assertEquals(1, importRepository.releaseJobsAfterNotificationDelivery(outboxId, 120))
            val released = importRepository.findJob(jobId)!!
            assertTrue(released.availableAt!!.isAfter(released.notificationDeliveredAt!!.plusSeconds(119)))

            val leaseToken = UUID.randomUUID()
            jdbc.update(
                "UPDATE league_import_job SET status='RUNNING',lease_token=?,lease_until=now()+interval '5 minutes' WHERE id=?",
                leaseToken,
                jobId,
            )
            val edited = request(messageId, 2, announcement(49, "12 августа 2030"), editedAt = Instant.now())
            ingestService.ingest("test-current", UUID.randomUUID(), Instant.now(), edited)

            val cancelled = importRepository.findJob(jobId)!!
            assertEquals("CANCELLED", cancelled.status)
            assertEquals(null, cancelled.leaseToken)
        } finally {
            properties.policies.lp.createMode = previousMode
            properties.automationCutoverAt = previousCutover
            properties.operatorNotificationsEnabled = previousNotifications
        }
    }

    @Test
    fun `disabled sweep is terminal in same generation while write-off preserves manual preview`() {
        val itemId = ingest(50)
        val previewId = actionId(itemId, "CREATE_PREVIEW")
        val previousWrites = properties.productionWritesEnabled
        val previousEnabled = properties.enabled
        val previousMode = properties.policies.lp.createMode
        val previousCutover = properties.automationCutoverAt
        val previousNotifications = properties.operatorNotificationsEnabled
        try {
            properties.operatorNotificationsEnabled = true
            properties.productionWritesEnabled = false
            LeagueImportActionScheduler(properties, importRepository, createService, transactionManager).process()
            assertEquals("NOTIFY_PENDING", importRepository.findAction(previewId)!!.status)

            properties.productionWritesEnabled = true
            properties.policies.lp.createMode = LeagueImportAutomationMode.AUTOMATIC
            properties.automationCutoverAt = Instant.EPOCH
            val autoMessageId = messageSequence.incrementAndGet()
            val autoRequest = request(autoMessageId, 1, announcement(51, "11 августа 2030"))
            val autoItemId = ingestService.ingest("test-current", UUID.randomUUID(), Instant.now(), autoRequest).itemId!!
            val jobId = jdbc.queryForObject(
                "SELECT id FROM league_import_job WHERE item_id=? AND operation='CREATE'",
                UUID::class.java,
                autoItemId,
            )!!
            properties.enabled = false
            LeagueImportJobScheduler(properties, importRepository, createService, resultProcessingService, transactionManager).process()
            assertEquals("CANCELLED", importRepository.findJob(jobId)!!.status)
            assertEquals(
                "SUPERSEDED",
                stringValue("SELECT status FROM league_import_operator_outbox WHERE id=(SELECT pending_notification_outbox_id FROM league_import_job WHERE id=?)", jobId),
            )

            properties.enabled = true
            ingestService.ingest("test-current", UUID.randomUUID(), Instant.now(), autoRequest)
            LeagueImportJobScheduler(properties, importRepository, createService, resultProcessingService, transactionManager).process()
            assertEquals("CANCELLED", importRepository.findJob(jobId)!!.status)
            assertEquals(1, intValue("SELECT count(*) FROM league_import_job WHERE item_id=?", autoItemId))
        } finally {
            properties.productionWritesEnabled = previousWrites
            properties.enabled = previousEnabled
            properties.policies.lp.createMode = previousMode
            properties.automationCutoverAt = previousCutover
            properties.operatorNotificationsEnabled = previousNotifications
        }
    }

    @Test
    fun `late duplicate rolls back source audit and outcome writes`() {
        val itemId = ingest(45)
        val previewId = actionId(itemId, "CREATE_PREVIEW")
        importRepository.markActionOffered(previewId, 7201)
        callbackService.tryHandle(callback(301, "preview-late-duplicate", tokenCodec.encode(previewId), 61, properties.operatorChatId, 7201))
        val confirmId = actionId(itemId, "CREATE_CONFIRM")
        importRepository.markActionOffered(confirmId, 7202)
        callbackService.tryHandle(callback(302, "confirm-late-duplicate", tokenCodec.encode(confirmId), 61, properties.operatorChatId, 7202))
        assertEquals(confirmId, TransactionTemplate(transactionManager).execute { importRepository.claimNextCreateAction() })

        seriesService.createSeries(
            tournament.id!!,
            CreateSeriesRequest(
                name = "Лига Претендентов: Серия 45.",
                namePrefix = "Лига Претендентов",
                gameStartedOn = LocalDate.of(2030, 8, 11),
                status = SeriesStatus.UPCOMING,
                startsAt = Instant.parse("2030-08-11T16:00:00Z"),
                teamDeadline = Instant.parse("2030-08-11T16:10:00Z"),
            ),
        )

        assertThrows(Exception::class.java) { createService.create(confirmId) }
        assertEquals(null, importRepository.findItemById(itemId)!!.targetSeriesId)
        assertEquals(0, intValue("SELECT count(*) FROM series_external_post_link WHERE import_item_id=?", itemId))
        assertEquals(0, intValue("SELECT count(*) FROM league_import_audit_event WHERE action_id=? AND event_type='SERIES_CREATED'", confirmId))
        assertEquals(0, intValue("SELECT count(*) FROM league_import_operator_outbox WHERE action_id=? AND event_type='CREATED'", confirmId))
        assertEquals(1, intValue("SELECT count(*) FROM series WHERE tournament_id=? AND public_number=45", tournament.id!!))

        createService.fail(confirmId, "Series public number 45 already exists in tournament")
        assertEquals("CONFLICT", importRepository.findItemById(itemId)!!.state)
        assertEquals(1, intValue("SELECT count(*) FROM league_import_operator_outbox WHERE action_id=? AND event_type='CREATE_CONFLICT' AND status='PENDING'", confirmId))
    }

    @Test
    fun `confirmed create atomically writes shell source audit and outcome while duplicate is blocked`() {
        val itemId = ingest(44)
        val previewId = actionId(itemId, "CREATE_PREVIEW")
        importRepository.markActionOffered(previewId, 7101)
        callbackService.tryHandle(callback(201, "preview-create", tokenCodec.encode(previewId), 51, properties.operatorChatId, 7101))
        val confirmId = actionId(itemId, "CREATE_CONFIRM")
        importRepository.markActionOffered(confirmId, 7102)
        callbackService.tryHandle(callback(202, "confirm-create", tokenCodec.encode(confirmId), 51, properties.operatorChatId, 7102))
        val claimed = TransactionTemplate(transactionManager).execute { importRepository.claimNextCreateAction() }
        assertEquals(confirmId, claimed)

        createService.create(confirmId)

        val item = importRepository.findItemById(itemId)!!
        val seriesId = item.targetSeriesId!!
        val created = seriesRepository.findById(seriesId).orElseThrow()
        assertEquals(44L, created.publicNumber)
        assertEquals(SeriesStatus.UPCOMING, created.status)
        assertEquals(LocalDate.of(2030, 8, 11), created.gameStartedOn)
        assertEquals(0, intValue("SELECT count(*) FROM series_player WHERE series_id=?", seriesId))
        assertEquals(1, intValue("SELECT count(*) FROM series_external_post_link WHERE series_id=? AND import_item_id=?", seriesId, itemId))
        assertEquals(1, intValue("SELECT count(*) FROM league_import_audit_event WHERE action_id=? AND event_type='SERIES_CREATED' AND outcome='COMMITTED'", confirmId))
        assertEquals(1, intValue("SELECT count(*) FROM league_import_operator_outbox WHERE action_id=? AND event_type='CREATED' AND status='PENDING'", confirmId))
        assertEquals("APPLIED", importRepository.findAction(confirmId)!!.status)
        assertEquals("APPLIED", importRepository.findItemById(itemId)!!.state)

        seriesService.createSeries(
            tournament.id!!,
            CreateSeriesRequest(
                name = "Лига Претендентов: Серия 34.",
                namePrefix = "Лига Претендентов",
                gameStartedOn = LocalDate.of(2030, 8, 11),
                status = SeriesStatus.UPCOMING,
                startsAt = Instant.parse("2030-08-11T16:00:00Z"),
                teamDeadline = Instant.parse("2030-08-11T16:10:00Z"),
            ),
        )
        val duplicateMessageId = messageSequence.incrementAndGet()
        val duplicate = ingestService.ingest(
            "test-current",
            UUID.randomUUID(),
            Instant.now(),
            request(duplicateMessageId, 1, announcement(34, "11 августа 2030")),
        )
        val duplicateItemId = duplicate.itemId!!
        assertEquals("BLOCKED", duplicate.state)
        assertEquals(0, intValue("SELECT count(*) FROM league_import_action WHERE item_id=?", duplicateItemId))
        assertEquals("BLOCKED", stringValue("SELECT event_type FROM league_import_operator_outbox WHERE item_id=?", duplicateItemId))
        assertTrue(stringValue("SELECT blocked_reason FROM league_import_item WHERE id=?", duplicateItemId).contains("already exists"))
        assertEquals(1, intValue("SELECT count(*) FROM series WHERE tournament_id=? AND public_number=34", tournament.id!!))
    }

    @Test
    fun `legacy existing series can atomically claim exact announcement and result evidence`() {
        val number = 52L
        val created = seriesService.createSeries(
            tournament.id!!,
            CreateSeriesRequest(
                name = "Лига Претендентов: Серия $number.",
                namePrefix = "Лига Претендентов",
                gameStartedOn = LocalDate.of(2030, 8, 11),
                status = SeriesStatus.UPCOMING,
                startsAt = Instant.parse("2030-08-11T10:58:54Z"),
                teamDeadline = Instant.parse("2030-08-11T16:10:00Z"),
                expectedGameCount = 5,
            ),
        )
        val legacySeries = seriesRepository.findById(created.id).orElseThrow().also {
            it.status = SeriesStatus.SCORING
            seriesRepository.saveAndFlush(it)
        }
        val announcementMessageId = messageSequence.incrementAndGet()
        val resultMessageId = messageSequence.incrementAndGet()
        val announcementItemId = ingestService.ingest(
            "test-current", UUID.randomUUID(), Instant.now(),
            request(announcementMessageId, 1, announcement(number, "11 августа 2030")),
        ).itemId!!
        val resultItemId = ingestService.ingest(
            "test-current", UUID.randomUUID(), Instant.now(), request(resultMessageId, 1, result(number)),
        ).itemId!!
        assertEquals("BLOCKED", importRepository.findItemById(announcementItemId)!!.state)
        assertEquals("BLOCKED_MISMATCH", importRepository.findItemById(resultItemId)!!.state)

        val previousResultProcessing = properties.resultProcessingEnabled
        val previousFinalizeMode = properties.policies.lp.finalizeMode
        try {
            properties.resultProcessingEnabled = true
            properties.policies.lp.finalizeMode = LeagueImportAutomationMode.MANUAL

            val linked = legacyLinkService.link(legacySeries.id!!, announcementMessageId, resultMessageId)
            assertFalse(linked.idempotent)
            assertEquals(announcementItemId, linked.announcementItemId)
            assertEquals(resultItemId, linked.resultItemId)
            assertEquals("APPLIED", importRepository.findItemById(announcementItemId)!!.state)
            assertEquals("WAITING_FOR_GAMES", importRepository.findItemById(resultItemId)!!.state)
            assertEquals(legacySeries.id, importRepository.findItemById(resultItemId)!!.targetSeriesId)
            assertEquals(
                2,
                intValue("SELECT count(*) FROM series_external_post_link WHERE series_id=?", legacySeries.id!!),
            )
            assertEquals(
                1,
                intValue(
                    "SELECT count(*) FROM league_import_job WHERE item_id=? AND operation='RECONCILE' AND status='PENDING'",
                    resultItemId,
                ),
            )
            assertEquals(
                2,
                intValue(
                    "SELECT count(*) FROM league_import_audit_event WHERE item_id IN (?,?) AND event_type LIKE 'LEGACY_%_LINKED'",
                    announcementItemId,
                    resultItemId,
                ),
            )

            val replay = legacyLinkService.link(legacySeries.id!!, announcementMessageId, resultMessageId)
            assertTrue(replay.idempotent)
            assertEquals(linked.reconcileJobId, replay.reconcileJobId)
            assertEquals(2, intValue("SELECT count(*) FROM series_external_post_link WHERE series_id=?", legacySeries.id!!))
            assertEquals(1, intValue("SELECT count(*) FROM league_import_job WHERE item_id=?", resultItemId))
            assertEquals(
                2,
                intValue(
                    "SELECT count(*) FROM league_import_audit_event WHERE item_id IN (?,?) AND event_type LIKE 'LEGACY_%_LINKED'",
                    announcementItemId,
                    resultItemId,
                ),
            )
        } finally {
            properties.resultProcessingEnabled = previousResultProcessing
            properties.policies.lp.finalizeMode = previousFinalizeMode
        }
    }

    @Test
    fun `manual competition series without Telegram links can become ready from Polemica evidence only`() {
        val number = 53L
        tournament.kind = TournamentKind.POLEMICA_COMPETITION
        tournament.polemicaCompetitionId = 999_053L
        tournamentRepository.saveAndFlush(tournament)
        val created = seriesService.createSeries(
            tournament.id!!,
            CreateSeriesRequest(
                name = "Manual series $number",
                namePrefix = "Manual",
                gameNumFrom = 1,
                gameNumTo = 5,
                status = SeriesStatus.UPCOMING,
                startsAt = Instant.parse("2030-08-11T16:00:00Z"),
                teamDeadline = Instant.parse("2030-08-11T16:10:00Z"),
                expectedGameCount = 5,
            ),
        )
        val series = seriesRepository.findById(created.id).orElseThrow().also {
            it.status = SeriesStatus.SCORING
            it.teamDeadline = Instant.now().minusSeconds(1)
            seriesRepository.saveAndFlush(it)
        }
        val contextChecksum = scoringContextFingerprintService.fingerprint(series.id!!)
        val base = Instant.parse("2030-08-11T16:00:00Z")
        (1..5).forEach { index ->
            seriesGameRepository.save(
                SeriesGame(
                    series = series,
                    polemicaGameId = 2000L + index,
                    gameName = "Manual game $index",
                    gameDataCache = objectMapper.valueToTree(
                        polemicaGame(2000L + index, if (index % 2 == 0) PolemicaGameResult.RED_WIN else PolemicaGameResult.BLACK_WIN),
                    ),
                    playedAt = base.plus(index.toLong(), ChronoUnit.MINUTES),
                    scored = true,
                    pointsStatus = "COMPLETE",
                    scoringInputChecksum = sha256("manual-points-$index"),
                    scoringContextChecksum = contextChecksum,
                    scoredAt = Instant.now(),
                ),
            )
        }
        seriesGameRepository.flush()
        val selectorChecksum = selectorFingerprintService.fingerprint(series.id!!)
        seriesRepository.findById(series.id!!).orElseThrow().also {
            it.lastSyncedSelectorChecksum = selectorChecksum
            it.lastScoredSelectorChecksum = selectorChecksum
            seriesRepository.saveAndFlush(it)
        }

        assertEquals(0, intValue("SELECT count(*) FROM series_external_post_link WHERE series_id=?", series.id!!))
        val completion = completionService.evaluate(series.id!!)
        assertTrue(completion.ready, completion.reason)
        assertTrue(completion.checksum!!.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun `result links only through announcement and manual confirmation finalizes once with atomic outcome`() {
        val number = 47L
        val announcementItemId = ingest(number)
        val previewId = actionId(announcementItemId, "CREATE_PREVIEW")
        importRepository.markActionOffered(previewId, 8101)
        callbackService.tryHandle(callback(401, "create-preview", tokenCodec.encode(previewId), 71, properties.operatorChatId, 8101))
        val confirmId = actionId(announcementItemId, "CREATE_CONFIRM")
        importRepository.markActionOffered(confirmId, 8102)
        callbackService.tryHandle(callback(402, "create-confirm", tokenCodec.encode(confirmId), 71, properties.operatorChatId, 8102))
        assertEquals(confirmId, TransactionTemplate(transactionManager).execute { importRepository.claimNextCreateAction() })
        createService.create(confirmId)
        val seriesId = importRepository.findItemById(announcementItemId)!!.targetSeriesId!!
        val series = seriesRepository.findById(seriesId).orElseThrow().apply {
            status = SeriesStatus.SCORING
            teamDeadline = Instant.now().minusSeconds(1)
        }
        seriesRepository.saveAndFlush(series)

        properties.resultProcessingEnabled = true
        properties.policies.lp.finalizeMode = LeagueImportAutomationMode.MANUAL
        try {
            val text = result(number)
            val resultMessageId = messageSequence.incrementAndGet()
            val resultItemId = ingestService.ingest(
                "test-current", UUID.randomUUID(), Instant.now(), request(resultMessageId, 1, text),
            ).itemId!!
            val resultItem = importRepository.findItemById(resultItemId)!!
            assertEquals(seriesId, resultItem.targetSeriesId)
            assertEquals(1, intValue("SELECT count(*) FROM series_external_post_link WHERE series_id=? AND link_role='RESULT'", seriesId))
            assertEquals(1, intValue("SELECT count(*) FROM league_import_job WHERE item_id=? AND operation='RECONCILE'", resultItemId))
            jdbc.update(
                "UPDATE league_import_job SET status='RUNNING',lease_until=now()-interval '1 second' WHERE item_id=? AND operation='RECONCILE'",
                resultItemId,
            )
            val recoveredLease = TransactionTemplate(transactionManager).execute { importRepository.leaseJob() }!!
            assertEquals("RECONCILE", recoveredLease.operation)
            jdbc.update("UPDATE league_import_job SET lease_until=now()-interval '1 second' WHERE id=?", recoveredLease.id)
            val fencedLease = TransactionTemplate(transactionManager).execute { importRepository.leaseJob() }!!
            assertEquals(recoveredLease.id, fencedLease.id)
            assertFalse(
                importRepository.rescheduleJob(
                    recoveredLease.id, Instant.now().plusSeconds(3600), "stale worker",
                    expectedLeaseToken = recoveredLease.leaseToken,
                ),
            )
            assertTrue(
                importRepository.rescheduleJob(
                    fencedLease.id, Instant.now().plusSeconds(3600), "test lease recovery",
                    expectedLeaseToken = fencedLease.leaseToken,
                ),
            )

            val contextChecksum = scoringContextFingerprintService.fingerprint(seriesId)
            val base = Instant.parse("2030-08-11T16:00:00Z")
            (1..5).forEach { index ->
                val gameResult = if (index % 2 == 0) PolemicaGameResult.RED_WIN else PolemicaGameResult.BLACK_WIN
                seriesGameRepository.save(
                    SeriesGame(
                        series = series,
                        polemicaGameId = 1000L + index,
                        gameName = "Result game $index",
                        gameDataCache = objectMapper.valueToTree(polemicaGame(1000L + index, gameResult)),
                        playedAt = base.plus(index.toLong(), ChronoUnit.MINUTES),
                        scored = true,
                        pointsStatus = "COMPLETE",
                        scoringInputChecksum = sha256("points-$index"),
                        scoringContextChecksum = contextChecksum,
                        scoredAt = Instant.now(),
                    ),
                )
            }
            seriesGameRepository.flush()
            val selectorChecksum = selectorFingerprintService.fingerprint(seriesId)
            seriesRepository.findById(seriesId).orElseThrow().also {
                it.lastSyncedSelectorChecksum = selectorChecksum
                it.lastScoredSelectorChecksum = selectorChecksum
                seriesRepository.saveAndFlush(it)
            }
            val completion = completionService.evaluate(seriesId)
            assertTrue(completion.ready, completion.reason)
            seriesService.assignPlayers(seriesId, AssignSeriesPlayersRequest(emptyList()))
            assertFalse(completionService.evaluate(seriesId).ready)
            val restoredGames = seriesGameRepository.findAllBySeries_Id(seriesId).onEach { game ->
                game.scored = true
                game.pointsStatus = "COMPLETE"
                game.scoringInputChecksum = sha256("points-${game.polemicaGameId}")
                game.scoringContextChecksum = contextChecksum
                game.scoredAt = Instant.now()
                game.scoringError = null
            }
            seriesGameRepository.saveAllAndFlush(restoredGames)
            val restoredCompletion = completionService.evaluate(seriesId)
            assertTrue(restoredCompletion.ready, restoredCompletion.reason)
            val selectorMutated = seriesRepository.findById(seriesId).orElseThrow()
            val originalPrefix = selectorMutated.namePrefix
            selectorMutated.namePrefix = "$originalPrefix changed"
            seriesRepository.saveAndFlush(selectorMutated)
            assertFalse(completionService.evaluate(seriesId).ready)
            selectorMutated.namePrefix = originalPrefix
            seriesRepository.saveAndFlush(selectorMutated)

            val draft = objectMapper.treeToValue(resultItem.draftJson, LeagueResultDraft::class.java)
            resultProcessingService.offerFinalizeAction(
                resultItemId, resultItem.version, resultItem.currentRevision, restoredCompletion.checksum!!, draft,
                "FINALIZE_PREVIEW", null, properties.previewTtlSeconds,
            )
            val finalizePreviewId = actionId(resultItemId, "FINALIZE_PREVIEW")
            importRepository.markActionOffered(finalizePreviewId, 8201)
            callbackService.tryHandle(
                callback(403, "finalize-preview", tokenCodec.encode(finalizePreviewId), 72, properties.operatorChatId, 8201),
            )
            val finalizeConfirmId = actionId(resultItemId, "FINALIZE_CONFIRM")
            importRepository.markActionOffered(finalizeConfirmId, 8202)
            callbackService.tryHandle(
                callback(404, "finalize-wrong-actor", tokenCodec.encode(finalizeConfirmId), 73, properties.operatorChatId, 8202),
            )
            assertEquals("OFFERED", importRepository.findAction(finalizeConfirmId)!!.status)
            callbackService.tryHandle(
                callback(405, "finalize-confirm", tokenCodec.encode(finalizeConfirmId), 72, properties.operatorChatId, 8202),
            )
            jdbc.update("UPDATE league_import_job SET status='APPLIED',completed_at=now() WHERE item_id=? AND operation='RECONCILE'", resultItemId)
            val finalizeJob = TransactionTemplate(transactionManager).execute { importRepository.leaseJob() }!!
            assertEquals("FINALIZE", finalizeJob.operation)
            resultProcessingService.finalize(finalizeJob.id, finalizeJob.leaseToken!!)

            assertEquals(true, seriesRepository.findById(seriesId).orElseThrow().finalized)
            assertEquals("FINALIZED", importRepository.findItemById(resultItemId)!!.state)
            assertEquals(1, intValue("SELECT count(*) FROM league_import_audit_event WHERE item_id=? AND event_type='SERIES_FINALIZED' AND outcome='COMMITTED'", resultItemId))
            assertEquals(1, intValue("SELECT count(*) FROM league_import_operator_outbox WHERE item_id=? AND event_type='FINALIZED'", resultItemId))
            val corrected = result(number).replaceFirst("Победа: Мафия", "Победа: Мирные")
            val correction = ingestService.ingest(
                "test-current", UUID.randomUUID(), Instant.now(),
                request(resultMessageId, 2, corrected, editedAt = Instant.now()),
            )
            assertEquals("INCIDENT", correction.state)
            assertEquals(1, intValue("SELECT count(*) FROM league_import_operator_outbox WHERE item_id=? AND event_type='INCIDENT'", resultItemId))
        } finally {
            properties.resultProcessingEnabled = false
            properties.policies.lp.finalizeMode = LeagueImportAutomationMode.DISABLED
        }
    }

    private fun ingest(seriesNumber: Long): Long {
        val messageId = messageSequence.incrementAndGet()
        return ingestService.ingest(
            "test-current",
            UUID.randomUUID(),
            Instant.now(),
            request(messageId, 1, announcement(seriesNumber, "11 августа 2030")),
        ).itemId!!
    }

    private fun request(messageId: Long, revision: Int, text: String, editedAt: Instant? = null): TelegramLeagueImportEventRequest =
        TelegramLeagueImportEventRequest(
            sourceChannelPeerId = properties.sourceChannelPeerId,
            messageId = messageId,
            revision = revision,
            sourceVersion = "telethon-v1",
            postedAt = Instant.parse("2026-08-11T10:00:00Z"),
            editedAt = editedAt,
            contentHash = sha256(text),
            rawText = text,
        )

    private fun announcement(number: Long, date: String) = """
        **Лига Претендентов: Серия $number.**

        Дата: $date
        Время: 19:00 МСК.

        #анонс_ЛП
    """.trimIndent()

    private fun result(number: Long) = """
        Лига Претендентов: Серия $number. Результаты
        Игра 1
        Мафия: Alpha, Houston, TX, Gamma
        Шериф: Sheriff
        Победа: Мафия
        Игра 2
        Мафия: Alpha, Houston, TX, Gamma
        Шериф: Sheriff
        Победа: Мирные
        Игра 3
        Мафия: Alpha, Houston, TX, Gamma
        Шериф: Sheriff
        Победа: Мафия
        Игра 4
        Мафия: Alpha, Houston, TX, Gamma
        Шериф: Свой
        Победа: Мирные
        Игра 5
        Мафия: Alpha, Houston, TX, Gamma
        Шериф: Sheriff
        Победа: Мафия
        #результаты_ЛП
    """.trimIndent()

    private fun polemicaGame(id: Long, result: PolemicaGameResult) = PolemicaGame(
        id = id, name = "Game $id", master = 1L, referee = null, scoringVersion = null,
        scoringType = 0, version = 0, zeroVoting = null, tags = emptyList(), players = listOf(
            resultPlayer(1, "Alpha", Role.DON),
            resultPlayer(2, "Houston, TX", Role.MAFIA),
            resultPlayer(3, "Gamma", Role.MAFIA),
            resultPlayer(4, if (id == 1004L) "Cвой" else "Sheriff", Role.SHERIFF),
        ),
        checks = emptyList(), shots = emptyList(), stage = null, votes = emptyList(), comKiller = null,
        bonuses = emptyList(), started = LocalDateTime.parse("2030-08-11T19:00:00"), stop = null,
        isLive = false, result = result, num = null, table = null, phase = null, factor = null,
    )

    private fun resultPlayer(position: Int, username: String, role: Role) = PolemicaPlayer(
        position = Position.entries.first { it.value == position },
        username = username,
        role = role,
        techs = emptyList(),
        fouls = emptyList(),
        guess = null,
        player = PolemicaUser(position.toLong(), username),
        disqual = null,
        award = null,
    )

    private fun callback(updateId: Long, queryId: String, data: String, actorId: Long, chatId: Long, messageId: Long) =
        objectMapper.readTree(
            """
            {"update_id":$updateId,"callback_query":{"id":"$queryId","data":"$data",
             "from":{"id":$actorId,"is_bot":false,"username":"operator"},
             "message":{"message_id":$messageId,"chat":{"id":$chatId}}}}
            """.trimIndent(),
        )

    private fun actionId(itemId: Long, actionType: String): UUID = jdbc.queryForObject(
        "SELECT id FROM league_import_action WHERE item_id=? AND action_type=? ORDER BY created_at DESC LIMIT 1",
        UUID::class.java,
        itemId,
        actionType,
    )!!

    private fun count(table: String, column: String, value: Long): Int =
        intValue("SELECT count(*) FROM $table WHERE $column=?", value)

    private fun intValue(sql: String, vararg args: Any): Int = jdbc.queryForObject(sql, Int::class.java, *args)!!
    private fun longValue(sql: String, vararg args: Any): Long = jdbc.queryForObject(sql, Long::class.java, *args)!!
    private fun stringValue(sql: String, vararg args: Any): String = jdbc.queryForObject(sql, String::class.java, *args)!!
    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    private companion object {
        val messageSequence = AtomicLong(90_000)

        @JvmField
        @Container
        @ServiceConnection
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")
    }
}
