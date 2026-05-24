package io.github.mralex1810.fantasy.repository

import io.github.mralex1810.fantasy.entity.ReleaseNoteView
import io.github.mralex1810.fantasy.entity.ReleaseNoteViewId
import org.springframework.data.jpa.repository.JpaRepository

interface ReleaseNoteViewRepository : JpaRepository<ReleaseNoteView, ReleaseNoteViewId> {
    fun findAllByTelegramUserId(telegramUserId: Long): List<ReleaseNoteView>
}
