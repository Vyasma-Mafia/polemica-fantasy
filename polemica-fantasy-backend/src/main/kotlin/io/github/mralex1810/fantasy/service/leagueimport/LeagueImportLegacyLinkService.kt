package io.github.mralex1810.fantasy.service.leagueimport

import io.github.mralex1810.fantasy.config.LeagueImportAutomationMode
import io.github.mralex1810.fantasy.config.TelegramLeagueImportProperties
import io.github.mralex1810.fantasy.dto.admin.response.LegacySeriesSourcesLinkDto
import io.github.mralex1810.fantasy.entity.SeriesStatus
import io.github.mralex1810.fantasy.entity.TournamentKind
import io.github.mralex1810.fantasy.repository.LeagueImportItemRow
import io.github.mralex1810.fantasy.repository.LeagueImportRepository
import io.github.mralex1810.fantasy.repository.SeriesRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

/** Explicit, audited bridge for series created before backend Telegram evidence existed. */
@Service
class LeagueImportLegacyLinkService(
    private val properties: TelegramLeagueImportProperties,
    private val seriesRepository: SeriesRepository,
    private val repository: LeagueImportRepository,
    private val announcementParser: LeagueAnnouncementParser,
    private val resultParser: LeagueResultParser,
) {
    @Transactional
    fun link(seriesId: Long, announcementMessageId: Long, resultMessageId: Long): LegacySeriesSourcesLinkDto {
        requireOperationalGates()
        if (announcementMessageId == resultMessageId) conflict("announcement and result posts must differ")

        // Global lock order is series -> import evidence.
        val series = seriesRepository.findByIdForUpdate(seriesId) ?: notFound("Series $seriesId not found")
        if (series.finalized || series.status == SeriesStatus.FINISHED) conflict("series is already finalized")
        if (series.status !in setOf(SeriesStatus.ACTIVE, SeriesStatus.SCORING)) {
            conflict("legacy source linking requires ACTIVE or SCORING series")
        }
        val tournament = series.tournament ?: conflict("series tournament is missing")
        if (tournament.kind != TournamentKind.STANDALONE) conflict("only STANDALONE series are supported")

        val locked = listOf(announcementMessageId, resultMessageId).sorted().associateWith { messageId ->
            repository.findItem(properties.sourceChannelPeerId, messageId, lock = true)
                ?: notFound("Telegram import item for message $messageId is missing; reprocess it first")
        }
        val announcementItem = locked.getValue(announcementMessageId)
        val resultItem = locked.getValue(resultMessageId)
        rejectDifferentTarget(announcementItem, seriesId)
        rejectDifferentTarget(resultItem, seriesId)

        val announcementRevision = repository.currentRevision(announcementItem.id)
            ?: conflict("announcement current revision is missing")
        val resultRevision = repository.currentRevision(resultItem.id)
            ?: conflict("result current revision is missing")
        val announcement = announcementParser.parse(
            announcementRevision.rawText,
            announcementRevision.postedAt,
            sourceUrl(announcementMessageId),
        ) as? LeagueAnnouncementParseResult.Ready ?: conflict("announcement post is not strictly parseable")
        val result = resultParser.parse(resultRevision.rawText, sourceUrl(resultMessageId)) as? LeagueResultParseResult.Ready
            ?: conflict("result post is not strictly parseable")

        validateIdentity(seriesId, series, announcement.draft, result.draft)
        val policy = properties.policy(announcement.draft.league) ?: conflict("league policy is disabled")
        if (policy.finalizeMode == LeagueImportAutomationMode.DISABLED) conflict("league finalization mode is disabled")

        validateLinkAvailability(seriesId, announcementItem, "ANNOUNCEMENT")
        validateLinkAvailability(seriesId, resultItem, "RESULT")
        val alreadyLinked =
            repository.linkedSeriesIdForSource(properties.sourceChannelPeerId, announcementMessageId, "ANNOUNCEMENT") == seriesId &&
                repository.linkedSeriesIdForSource(properties.sourceChannelPeerId, resultMessageId, "RESULT") == seriesId &&
                announcementItem.targetSeriesId == seriesId && resultItem.targetSeriesId == seriesId &&
                announcementItem.policyGeneration == properties.policyGeneration && resultItem.policyGeneration == properties.policyGeneration

        val normalizedAnnouncement: LeagueImportItemRow
        val normalizedResult: LeagueImportItemRow
        if (alreadyLinked) {
            normalizedAnnouncement = announcementItem
            normalizedResult = resultItem
        } else {
            repository.cancelOpenActionsForItem(announcementItem.id, "linked to existing legacy series $seriesId")
            repository.cancelOpenActionsForItem(resultItem.id, "linked to existing legacy series $seriesId")
            repository.cancelJobsThroughItemVersion(
                announcementItem.id,
                announcementItem.version,
                "linked to existing legacy series $seriesId",
            )
            repository.cancelJobsThroughItemVersion(
                resultItem.id,
                resultItem.version,
                "linked to existing legacy series $seriesId",
            )
            repository.updateCurrentItem(
                announcementItem.id, announcementItem.currentRevision, announcementItem.currentSourceVersion,
                announcementItem.currentContentHash, "ANNOUNCEMENT", announcement.draft.league, "APPLIED",
                announcement.draft, announcement.checksum, null, properties.policyGeneration,
                announcementItem.currentEvidenceHash,
            )
            repository.updateItemTargetSeries(announcementItem.id, seriesId)
            normalizedAnnouncement = repository.findItemById(announcementItem.id, lock = true)!!
            if (!repository.insertSourceLinkStrict(normalizedAnnouncement, seriesId, "ANNOUNCEMENT") &&
                repository.linkedSeriesIdForSource(properties.sourceChannelPeerId, announcementMessageId, "ANNOUNCEMENT") != seriesId
            ) conflict("announcement source was linked concurrently to another series")

            repository.updateCurrentItem(
                resultItem.id, resultItem.currentRevision, resultItem.currentSourceVersion,
                resultItem.currentContentHash, "RESULT", result.draft.league, "WAITING_FOR_GAMES",
                result.draft, result.checksum, null, properties.policyGeneration,
                resultItem.currentEvidenceHash,
            )
            repository.resetResultItemForReconcile(resultItem.id, seriesId)
            normalizedResult = repository.findItemById(resultItem.id, lock = true)!!
            if (!repository.insertSourceLinkStrict(normalizedResult, seriesId, "RESULT") &&
                repository.linkedSeriesIdForSource(properties.sourceChannelPeerId, resultMessageId, "RESULT") != seriesId
            ) conflict("result source was linked concurrently to another series")

            repository.audit(
                normalizedAnnouncement.id, null, null, "LEGACY_ANNOUNCEMENT_LINKED", "COMMITTED",
                mapOf("seriesId" to seriesId, "messageId" to announcementMessageId, "origin" to "ADMIN"),
            )
            repository.audit(
                normalizedResult.id, null, null, "LEGACY_RESULT_LINKED", "RECONCILE_QUEUED",
                mapOf("seriesId" to seriesId, "messageId" to resultMessageId, "origin" to "ADMIN"),
            )
        }

        val jobId = repository.enqueueJob(
            normalizedResult.id, normalizedResult.version, normalizedResult.currentRevision,
            normalizedResult.draftChecksum ?: result.checksum, properties.policyGeneration,
            "RECONCILE", seriesId,
        )
        return LegacySeriesSourcesLinkDto(
            seriesId = seriesId,
            announcementItemId = normalizedAnnouncement.id,
            resultItemId = normalizedResult.id,
            reconcileJobId = jobId,
            idempotent = alreadyLinked,
        )
    }

    private fun validateIdentity(
        seriesId: Long,
        series: io.github.mralex1810.fantasy.entity.Series,
        announcement: LeagueAnnouncementDraft,
        result: LeagueResultDraft,
    ) {
        val tournamentId = series.tournament?.id ?: conflict("series tournament is missing")
        val expected = series.expectedGameCount ?: conflict("series expected game count is not configured")
        if (announcement.league != result.league || announcement.tournamentId != result.tournamentId ||
            announcement.seriesNumber != result.seriesNumber || announcement.expectedGameCount != result.expectedGameCount
        ) conflict("announcement and result identities do not match")
        if (announcement.tournamentId != tournamentId || announcement.seriesNumber != series.publicNumber ||
            announcement.expectedGameCount != expected || announcement.name != series.name ||
            announcement.namePrefix != series.namePrefix || announcement.gameStartedOn != series.gameStartedOn ||
            announcement.teamDeadline != series.teamDeadline
        ) conflict("Telegram sources do not exactly identify series $seriesId")
    }

    private fun validateLinkAvailability(seriesId: Long, item: LeagueImportItemRow, role: String) {
        repository.linkedSeriesIdForSource(item.sourceChannelId, item.sourceMessageId, role)?.let {
            if (it != seriesId) conflict("$role source is already linked to series $it")
        }
        repository.linkedMessageIdForSeriesRole(seriesId, role)?.let {
            if (it != item.sourceMessageId) conflict("series already has a different $role source: $it")
        }
    }

    private fun rejectDifferentTarget(item: LeagueImportItemRow, seriesId: Long) {
        item.targetSeriesId?.let { if (it != seriesId) conflict("import item ${item.id} targets another series $it") }
    }

    private fun requireOperationalGates() {
        if (!properties.enabled || !properties.ingestEnabled || !properties.productionWritesEnabled ||
            !properties.resultProcessingEnabled || properties.policyGeneration == "disabled"
        ) throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Telegram import production/result gates are not enabled")
    }

    private fun sourceUrl(messageId: Long) = "https://t.me/polemica_closed_league/$messageId"
    private fun notFound(message: String): Nothing = throw ResponseStatusException(HttpStatus.NOT_FOUND, message)
    private fun conflict(message: String): Nothing = throw ResponseStatusException(HttpStatus.CONFLICT, message)
}
