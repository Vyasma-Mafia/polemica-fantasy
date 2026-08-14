package io.github.mralex1810.fantasy.service.leagueimport

import io.github.mralex1810.fantasy.config.TelegramLeagueImportProperties
import io.github.mralex1810.fantasy.config.LeagueImportAutomationMode
import io.github.mralex1810.fantasy.dto.admin.request.CreateSeriesRequest
import io.github.mralex1810.fantasy.dto.admin.request.AssignSeriesPlayersRequest
import io.github.mralex1810.fantasy.dto.internal.TelegramLeagueImportMediaEvidence
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.mralex1810.fantasy.entity.SeriesStatus
import io.github.mralex1810.fantasy.entity.TournamentKind
import io.github.mralex1810.fantasy.entity.TournamentStatus
import io.github.mralex1810.fantasy.repository.LeagueImportRepository
import io.github.mralex1810.fantasy.repository.TournamentRepository
import io.github.mralex1810.fantasy.service.SeriesService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import java.time.Instant

@Service
class LeagueImportCreateService(
    private val properties: TelegramLeagueImportProperties,
    private val repository: LeagueImportRepository,
    private val parser: LeagueAnnouncementParser,
    private val tournamentRepository: TournamentRepository,
    private val seriesService: SeriesService,
    private val rosterResolver: LeagueImportRosterResolver,
    private val objectMapper: ObjectMapper,
) {
    @Transactional
    fun create(actionId: UUID) {
        check(properties.enabled && properties.productionWritesEnabled) { "Telegram league import production writes are disabled" }
        val action = repository.findAction(actionId, lock = true) ?: error("Import action not found")
        check(action.actionType == "CREATE_CONFIRM" && action.status == "CREATING") { "Import action is not claimable" }
        check(action.policyGeneration == properties.policyGeneration) { "Import policy generation changed" }
        val item = repository.findItemById(action.itemId, lock = true) ?: error("Import item not found")
        check(item.policyGeneration == properties.policyGeneration) { "Import item policy generation changed" }
        check(item.targetSeriesId == null && item.currentRevision == action.sourceRevision && item.version == action.itemVersion) {
            "Import item changed before create"
        }
        val revision = repository.currentRevision(item.id) ?: error("Current import revision not found")
        check(revision.contentHash == item.currentContentHash && revision.evidenceHash == item.currentEvidenceHash) {
            "Import revision evidence changed"
        }
        val ready = parser.parse(
            revision.rawText,
            revision.postedAt,
            "https://t.me/polemica_closed_league/${item.sourceMessageId}",
        ) as? LeagueAnnouncementParseResult.Ready ?: error("Announcement is no longer parseable")
        check(ready.checksum == action.draftChecksum && ready.checksum == item.draftChecksum) { "Announcement checksum changed" }
        val draft = ready.draft
        val tournament = tournamentRepository.findByIdForUpdate(draft.tournamentId)
            ?: error("Configured tournament not found")
        check(tournament.status == TournamentStatus.ACTIVE) { "Configured tournament is not ACTIVE" }
        check(tournament.kind == TournamentKind.STANDALONE) { "Only STANDALONE tournaments are supported" }
        val roster = resolveRoster(item, revision, draft)
        val expectedRosterChecksum = roster.takeIf { it.draft.ready }?.checksum
        check(action.rosterChecksum == expectedRosterChecksum &&
            (expectedRosterChecksum == null || item.rosterChecksum == expectedRosterChecksum)) { "Roster checksum changed" }
        check(revision.mediaEvidence == null || roster.draft.ready) { "Roster requires manual review" }
        check(!roster.draft.ready || properties.rosterWritesEnabled) { "OCR roster production writes are disabled" }
        val policy = properties.policy(draft.league) ?: error("League policy is disabled")
        check(policy.createMode == LeagueImportAutomationMode.MANUAL) { "Manual series creation is disabled by policy" }

        check(draft.teamDeadline.isAfter(java.time.Instant.now())) { "Team deadline has passed" }

        val created = seriesService.createSeries(
            draft.tournamentId,
            CreateSeriesRequest(
                name = draft.name,
                namePrefix = draft.namePrefix,
                gameStartedOn = draft.gameStartedOn,
                status = SeriesStatus.UPCOMING,
                startsAt = draft.startsAt,
                teamDeadline = draft.teamDeadline,
                expectedGameCount = draft.expectedGameCount,
            ),
        )
        check(created.publicNumber == draft.seriesNumber) { "Created public number differs from announcement" }
        if (roster.draft.ready) {
            seriesService.assignPlayers(
                created.id,
                AssignSeriesPlayersRequest(roster.draft.resolved.map { it.tournamentPlayerId }),
            )
        }
        repository.insertSourceLink(item, created.id)
        repository.updateItemApplied(item.id, created.id)
        repository.updateActionStatus(action.id, "APPLIED")
        repository.audit(
            item.id, action.id, action.actorTelegramId, "SERIES_CREATED", "COMMITTED",
            mapOf("seriesId" to created.id, "tournamentId" to draft.tournamentId, "publicNumber" to draft.seriesNumber,
                "rosterCount" to roster.draft.resolved.size),
        )
        repository.enqueueOutbox(
            eventKey = "league-import:${item.id}:series-created:${created.id}",
            itemId = item.id,
            actionId = action.id,
            eventType = "CREATED",
            payload = mapOf(
                "seriesId" to created.id,
                "tournamentId" to draft.tournamentId,
                "name" to draft.name,
                "startsAt" to draft.startsAt,
                "teamDeadline" to draft.teamDeadline,
                "sourceUrl" to draft.sourceUrl,
                "rosterCount" to roster.draft.resolved.size,
                "expectedRosterCount" to roster.draft.expectedCount,
            ),
        )
    }

    @Transactional
    fun createFromJob(jobId: UUID, leaseToken: UUID) {
        check(properties.enabled && properties.productionWritesEnabled) { "Telegram league import production writes are disabled" }
        val job = repository.findJob(jobId, lock = true) ?: error("Import job not found")
        check(job.operation == "CREATE" && job.status == "RUNNING") { "Import create job is not claimable" }
        check(job.leaseToken == leaseToken) { "Import job lease was lost" }
        check(job.policyGeneration == properties.policyGeneration) { "Import policy generation changed" }
        val item = repository.findItemById(job.itemId, lock = true) ?: error("Import item not found")
        check(item.targetSeriesId == null && item.currentRevision == job.sourceRevision && item.version == job.itemVersion) {
            "Import item changed before automatic create"
        }
        val revision = repository.currentRevision(item.id) ?: error("Current import revision not found")
        check(revision.mediaEvidence == null) { "Automatic OCR roster creation requires MANUAL mode" }
        check(revision.contentHash == item.currentContentHash && revision.evidenceHash == item.currentEvidenceHash &&
            revision.observedAt.plusSeconds(120) <= Instant.now()) {
            "Automatic create stability window has not elapsed"
        }
        val cutover = properties.automationCutoverAt ?: error("Automation cutover is not configured")
        check(!revision.postedAt.isBefore(cutover) && !revision.observedAt.isBefore(cutover)) { "Source predates automation cutover" }
        val ready = parser.parse(
            revision.rawText, revision.postedAt,
            "https://t.me/polemica_closed_league/${item.sourceMessageId}",
        ) as? LeagueAnnouncementParseResult.Ready ?: error("Announcement is no longer parseable")
        check(ready.checksum == job.evidenceChecksum && ready.checksum == item.draftChecksum) { "Announcement checksum changed" }
        val draft = ready.draft
        val roster = resolveRoster(item, revision, draft)
        val expectedRosterChecksum = roster.takeIf { it.draft.ready }?.checksum
        check(job.rosterChecksum == expectedRosterChecksum &&
            (expectedRosterChecksum == null || item.rosterChecksum == expectedRosterChecksum)) { "Roster checksum changed" }
        val policy = properties.policy(draft.league) ?: error("League policy is disabled")
        check(policy.createMode == LeagueImportAutomationMode.AUTOMATIC) { "Automatic creation is disabled by policy" }
        validateTournamentAndDeadline(draft)

        val created = createShell(draft)
        if (roster.draft.ready) {
            seriesService.assignPlayers(
                created.id,
                AssignSeriesPlayersRequest(roster.draft.resolved.map { it.tournamentPlayerId }),
            )
        }
        repository.insertSourceLink(item, created.id)
        repository.updateItemApplied(item.id, created.id)
        check(repository.finishJob(job.id, "APPLIED", expectedLeaseToken = leaseToken)) { "Import job lease was lost" }
        repository.audit(item.id, null, null, "SERIES_AUTO_CREATED", "COMMITTED",
            mapOf("seriesId" to created.id, "rosterCount" to roster.draft.resolved.size))
        enqueueCreated(item, created.id, draft, roster.draft.resolved.size, roster.draft.expectedCount)
    }

    @Transactional
    fun fail(actionId: UUID, reason: String) {
        val action = repository.findAction(actionId, lock = true) ?: return
        if (action.status == "APPLIED") return
        val item = repository.findItemById(action.itemId, lock = true) ?: return
        val conflict = reason.contains("already exists", ignoreCase = true)
        repository.updateActionStatus(actionId, "FAILED", reason)
        if (item.targetSeriesId == null && item.version == action.itemVersion && item.currentRevision == action.sourceRevision) {
            repository.updateItemState(action.itemId, if (conflict) "CONFLICT" else "FAILED", reason)
        }
        repository.audit(action.itemId, actionId, action.actorTelegramId, "SERIES_CREATE", "FAILED", mapOf("reason" to reason.take(256)))
        repository.enqueueOutbox(
            eventKey = "league-import:${action.itemId}:create-failed:$actionId",
            itemId = action.itemId,
            actionId = actionId,
            eventType = if (conflict) "CREATE_CONFLICT" else "CREATE_FAILED",
            payload = mapOf(
                "reason" to reason.take(256),
                "sourceUrl" to "https://t.me/polemica_closed_league/${item.sourceMessageId}",
            ),
        )
    }

    @Transactional
    fun failJob(jobId: UUID, leaseToken: UUID, reason: String) {
        val job = repository.findJob(jobId, lock = true) ?: return
        if (job.leaseToken != leaseToken) return
        if (job.status in setOf("APPLIED", "CANCELLED")) return
        val item = repository.findItemById(job.itemId, lock = true) ?: return
        val conflict = reason.contains("already exists", ignoreCase = true)
        val cancelled = reason.isNonRetryablePolicyFailure()
        if (cancelled) {
            repository.finishJob(jobId, "CANCELLED", reason, expectedLeaseToken = leaseToken)
            repository.enqueueOutbox(
                "league-import:${item.id}:auto-cancelled:$jobId", item.id, null, "AUTO_CANCELLED",
                mapOf("operation" to "CREATE", "reason" to reason.take(256), "policyGeneration" to job.policyGeneration),
            )
            return
        }
        if (!conflict && job.attempts < 10) {
            repository.rescheduleJob(jobId, Instant.now().plusSeconds(30), reason,
                expectedLeaseToken = leaseToken)
            if (item.targetSeriesId == null && item.version == job.itemVersion && item.currentRevision == job.sourceRevision) {
                repository.updateItemState(item.id, "CREATE_PENDING", reason)
            }
            return
        }
        repository.finishJob(jobId, if (conflict) "BLOCKED" else "FAILED", reason,
            expectedLeaseToken = leaseToken)
        if (item.targetSeriesId == null && item.version == job.itemVersion && item.currentRevision == job.sourceRevision) {
            repository.updateItemState(item.id, if (conflict) "CONFLICT" else "FAILED", reason)
        }
        repository.audit(item.id, null, null, "SERIES_AUTO_CREATE", "FAILED", mapOf("reason" to reason.take(256)))
        repository.enqueueOutbox(
            "league-import:${item.id}:auto-create-failed:$jobId", item.id, null,
            if (conflict) "CREATE_CONFLICT" else "CREATE_FAILED",
            mapOf("reason" to reason.take(256), "sourceUrl" to "https://t.me/polemica_closed_league/${item.sourceMessageId}"),
        )
    }

    private fun validateTournamentAndDeadline(draft: LeagueAnnouncementDraft) {
        val tournament = tournamentRepository.findByIdForUpdate(draft.tournamentId)
            ?: error("Configured tournament not found")
        check(tournament.status == TournamentStatus.ACTIVE) { "Configured tournament is not ACTIVE" }
        check(tournament.kind == TournamentKind.STANDALONE) { "Only STANDALONE tournaments are supported" }
        check(draft.teamDeadline.isAfter(Instant.now())) { "Team deadline has passed" }
    }

    private fun createShell(draft: LeagueAnnouncementDraft) = seriesService.createSeries(
        draft.tournamentId,
        CreateSeriesRequest(
            name = draft.name,
            namePrefix = draft.namePrefix,
            gameStartedOn = draft.gameStartedOn,
            status = SeriesStatus.UPCOMING,
            startsAt = draft.startsAt,
            teamDeadline = draft.teamDeadline,
            expectedGameCount = draft.expectedGameCount,
        ),
    ).also { check(it.publicNumber == draft.seriesNumber) { "Created public number differs from announcement" } }

    private fun enqueueCreated(
        item: io.github.mralex1810.fantasy.repository.LeagueImportItemRow,
        seriesId: Long,
        draft: LeagueAnnouncementDraft,
        rosterCount: Int = 0,
        expectedRosterCount: Int = 0,
    ) {
        repository.enqueueOutbox(
            eventKey = "league-import:${item.id}:series-created:$seriesId",
            itemId = item.id,
            actionId = null,
            eventType = "CREATED",
            payload = mapOf(
                "seriesId" to seriesId,
                "tournamentId" to draft.tournamentId,
                "name" to draft.name,
                "startsAt" to draft.startsAt,
                "teamDeadline" to draft.teamDeadline,
                "sourceUrl" to draft.sourceUrl,
                "rosterCount" to rosterCount,
                "expectedRosterCount" to expectedRosterCount,
            ),
        )
    }

    private fun resolveRoster(
        item: io.github.mralex1810.fantasy.repository.LeagueImportItemRow,
        revision: io.github.mralex1810.fantasy.repository.LeagueImportRevisionRow,
        draft: LeagueAnnouncementDraft,
    ): LeagueImportRosterResolution {
        val media = revision.mediaEvidence?.let {
            objectMapper.treeToValue(it, TelegramLeagueImportMediaEvidence::class.java)
        }
        return rosterResolver.resolve(
            draft.league, draft.tournamentId, revision.evidenceHash, media,
            announcementText = revision.rawText,
        ).also {
            check(it.draft.status == item.rosterStatus && it.checksum == item.rosterChecksum) { "Stored roster draft changed" }
        }
    }

    private fun String.isNonRetryablePolicyFailure(): Boolean =
        contains("disabled", ignoreCase = true) || contains("mode changed", ignoreCase = true) ||
            contains("generation changed", ignoreCase = true) || contains("predates automation cutover", ignoreCase = true)
}
