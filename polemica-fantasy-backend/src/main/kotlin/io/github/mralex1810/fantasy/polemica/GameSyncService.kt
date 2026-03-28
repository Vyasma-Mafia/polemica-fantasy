package io.github.mralex1810.fantasy.polemica

/**
 * Syncs Polemica games into [io.github.mralex1810.fantasy.entity.SeriesGame].
 * Real implementation: Agent A4.
 */
fun interface GameSyncService {
    fun syncGames(seriesId: Long)
}
