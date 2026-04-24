package io.github.mralex1810.fantasy.util

/**
 * Builds a LIKE/ILIKE pattern for PostgreSQL with [ESCAPE '!'].
 * User input is treated as a literal substring (!, %, _ are escaped).
 */
object LikePattern {
    fun forContains(needle: String): String = "%${escape(needle)}%"

    private fun escape(s: String): String = buildString {
        for (c in s) {
            when (c) {
                '!' -> append("!!")
                '%' -> append("!%")
                '_' -> append("!_")
                else -> append(c)
            }
        }
    }
}
