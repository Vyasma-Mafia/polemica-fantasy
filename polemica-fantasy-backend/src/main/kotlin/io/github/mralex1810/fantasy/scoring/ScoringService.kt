package io.github.mralex1810.fantasy.scoring

/**
 * Calculates fantasy scores for a series.
 * Real implementation: Agent A4.
 */
fun interface ScoringService {
    fun calculateScores(seriesId: Long)
}
