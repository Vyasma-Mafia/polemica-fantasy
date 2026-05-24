package io.github.mralex1810.fantasy.dto.user.response

import java.time.Instant

data class ReleaseNoteDto(
    val id: Long,
    val title: String,
    val body: String,
    val audience: String,
    val minAppVersion: String?,
    val publishedAt: Instant,
    val seen: Boolean,
)

data class ReleaseNotesResponse(
    val notes: List<ReleaseNoteDto>,
    val unseenCount: Int,
)
