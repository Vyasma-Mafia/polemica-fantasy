package io.github.mralex1810.fantasy.event

internal fun buildSeriesFinalizedTelegramMessage(
    tournamentName: String,
    seriesName: String,
    winnerPublicName: String?,
    recipient: SeriesFinalizedRecipient,
): String {
    val lines = buildList {
        add("В турнире $tournamentName подсчитаны результаты серии $seriesName.")
        winnerPublicName?.trim()?.takeIf { it.isNotEmpty() }?.let { w ->
            add("")
            add("Победитель серии: $w.")
        }
        add("")
        add("Вы заняли ${recipient.place}-е место из ${recipient.total} и получили в награду ${recipient.reward} фантиков.")
        add("")
        add("Теперь у вас ${recipient.balanceAfter} фантиков.")
    }
    return lines.joinToString("\n")
}
