package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.dto.admin.request.CreateReleaseNoteRequest
import io.github.mralex1810.fantasy.dto.admin.request.UpdateReleaseNoteRequest
import io.github.mralex1810.fantasy.dto.admin.response.ReleaseNoteAdminDto
import io.github.mralex1810.fantasy.dto.user.response.ReleaseNoteDto
import io.github.mralex1810.fantasy.dto.user.response.ReleaseNotesResponse
import io.github.mralex1810.fantasy.entity.ProductAudience
import io.github.mralex1810.fantasy.entity.ReleaseNote
import io.github.mralex1810.fantasy.entity.ReleaseNoteView
import io.github.mralex1810.fantasy.entity.ReleaseNoteViewId
import io.github.mralex1810.fantasy.repository.ReleaseNoteRepository
import io.github.mralex1810.fantasy.repository.ReleaseNoteViewRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

@Service
class ReleaseNoteService(
    private val releaseNoteRepository: ReleaseNoteRepository,
    private val releaseNoteViewRepository: ReleaseNoteViewRepository,
    private val userSegmentService: UserSegmentService,
    private val productEventService: ProductEventService,
) {
    @Transactional(readOnly = true)
    fun listForUser(userId: Long, appVersion: String?): ReleaseNotesResponse {
        val segment = userSegmentService.audienceForUser(userId)
        val seenIds = releaseNoteViewRepository.findAllByTelegramUserId(userId)
            .mapNotNull { it.releaseNoteId }
            .toSet()
        val notes = releaseNoteRepository
            .findAllByActiveTrueAndPublishedAtLessThanEqualOrderByPublishedAtDesc(Instant.now())
            .filter { it.audience == ProductAudience.ALL || it.audience == segment }
            .filter { isVersionEligible(appVersion, it.minAppVersion) }
            .map { it.toUserDto(seenIds.contains(it.id!!)) }
        return ReleaseNotesResponse(
            notes = notes,
            unseenCount = notes.count { !it.seen },
        )
    }

    @Transactional
    fun markSeen(userId: Long, releaseNoteId: Long) {
        if (!releaseNoteRepository.existsById(releaseNoteId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Release note $releaseNoteId not found")
        }
        val id = ReleaseNoteViewId(userId, releaseNoteId)
        if (!releaseNoteViewRepository.existsById(id)) {
            releaseNoteViewRepository.save(
                ReleaseNoteView(
                    telegramUserId = userId,
                    releaseNoteId = releaseNoteId,
                    seenAt = Instant.now(),
                ),
            )
        }
        productEventService.record(
            userId = userId,
            eventType = "RELEASE_NOTE_SEEN",
            releaseNoteId = releaseNoteId,
            subjectType = "RELEASE_NOTE",
            subjectId = releaseNoteId,
            source = "SERVER",
        )
    }

    @Transactional(readOnly = true)
    fun listAdmin(): List<ReleaseNoteAdminDto> = releaseNoteRepository.findAllByOrderByPublishedAtDesc()
        .map { it.toAdminDto() }

    @Transactional
    fun createAdmin(request: CreateReleaseNoteRequest): ReleaseNoteAdminDto {
        val audience = userSegmentService.parseAudience(request.audience)
        val note = releaseNoteRepository.save(
            ReleaseNote(
                title = request.title.trim(),
                body = request.body.trim(),
                buttonText = request.buttonText?.trim()?.takeIf { it.isNotEmpty() },
                buttonUrl = request.buttonUrl?.trim()?.takeIf { it.isNotEmpty() },
                audience = audience,
                minAppVersion = request.minAppVersion?.trim()?.takeIf { it.isNotEmpty() },
                active = request.active,
                publishedAt = Instant.now(),
                createdAt = Instant.now(),
            ),
        )
        return note.toAdminDto()
    }

    @Transactional
    fun updateAdmin(id: Long, request: UpdateReleaseNoteRequest): ReleaseNoteAdminDto {
        val note = releaseNoteRepository.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Release note $id not found")
        }
        request.title?.let { note.title = it.trim() }
        request.body?.let { note.body = it.trim() }
        if (request.buttonText != null) {
            note.buttonText = request.buttonText.trim().takeIf { it.isNotEmpty() }
        }
        if (request.buttonUrl != null) {
            note.buttonUrl = request.buttonUrl.trim().takeIf { it.isNotEmpty() }
        }
        request.audience?.let { note.audience = userSegmentService.parseAudience(it) }
        if (request.minAppVersion != null) {
            note.minAppVersion = request.minAppVersion.trim().takeIf { it.isNotEmpty() }
        }
        request.active?.let { note.active = it }
        return releaseNoteRepository.save(note).toAdminDto()
    }

    @Transactional
    fun setActiveAdmin(id: Long, active: Boolean): ReleaseNoteAdminDto {
        val note = releaseNoteRepository.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Release note $id not found")
        }
        note.active = active
        return releaseNoteRepository.save(note).toAdminDto()
    }

    private fun ReleaseNote.toUserDto(seen: Boolean): ReleaseNoteDto = ReleaseNoteDto(
        id = id!!,
        title = title,
        body = body,
        buttonText = buttonText,
        buttonUrl = buttonUrl,
        audience = audience.name,
        minAppVersion = minAppVersion,
        publishedAt = publishedAt,
        seen = seen,
    )

    private fun ReleaseNote.toAdminDto(): ReleaseNoteAdminDto = ReleaseNoteAdminDto(
        id = id!!,
        title = title,
        body = body,
        buttonText = buttonText,
        buttonUrl = buttonUrl,
        audience = audience.name,
        minAppVersion = minAppVersion,
        active = active,
        publishedAt = publishedAt,
        createdAt = createdAt,
    )

    private fun isVersionEligible(appVersion: String?, minVersion: String?): Boolean {
        if (minVersion.isNullOrBlank()) return true
        val actual = appVersion?.takeIf { it.isNotBlank() } ?: return false
        return compareVersion(actual, minVersion) >= 0
    }

    private fun compareVersion(left: String, right: String): Int {
        val l = left.split('.', '-', '+').mapNotNull { it.toIntOrNull() }
        val r = right.split('.', '-', '+').mapNotNull { it.toIntOrNull() }
        val max = maxOf(l.size, r.size)
        for (i in 0 until max) {
            val a = l.getOrElse(i) { 0 }
            val b = r.getOrElse(i) { 0 }
            if (a != b) return a.compareTo(b)
        }
        return 0
    }
}
