package io.github.mralex1810.fantasy.event

internal fun buildSeriesFinalizedTelegramMessage(
    tournamentName: String,
    seriesName: String,
    recipient: SeriesFinalizedRecipient,
): String =
    """
    |В турнире $tournamentName подсчитаны результаты серии $seriesName.
    |
    |Вы заняли ${recipient.place}-е место из ${recipient.total} и получили в награду ${recipient.reward} фантиков.
    |
    |Теперь у вас ${recipient.balanceAfter} фантиков.
    """.trimMargin()
