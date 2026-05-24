package io.github.mralex1810.fantasy.repository

import io.github.mralex1810.fantasy.entity.ReleaseNote
import java.time.Instant
import org.springframework.data.jpa.repository.JpaRepository

interface ReleaseNoteRepository : JpaRepository<ReleaseNote, Long> {
    fun findAllByActiveTrueAndPublishedAtLessThanEqualOrderByPublishedAtDesc(publishedAt: Instant): List<ReleaseNote>
    fun findAllByOrderByPublishedAtDesc(): List<ReleaseNote>
}
