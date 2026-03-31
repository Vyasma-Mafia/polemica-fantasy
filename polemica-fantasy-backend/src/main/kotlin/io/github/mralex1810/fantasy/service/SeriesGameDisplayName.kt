package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.entity.SeriesGame

/** User-visible title for a series game row (sync may store synthetic names for empty API titles). */
fun formatSeriesGameDisplayName(g: SeriesGame): String {
    val stored = g.gameName.trim()
    if (stored.isNotEmpty() && stored != "(no name)") return stored
    val node = g.gameDataCache
    if (node != null && !node.isNull) {
        val numNode = node.path("num")
        val num = if (numNode.isMissingNode || numNode.isNull) null else numNode.asInt()
        if (num != null) return "Игра $num"
        val tableNode = node.path("table")
        val table = if (tableNode.isMissingNode || tableNode.isNull) null else tableNode.asInt()
        if (table != null) return "Стол $table"
    }
    return "Игра #${g.polemicaGameId}"
}
