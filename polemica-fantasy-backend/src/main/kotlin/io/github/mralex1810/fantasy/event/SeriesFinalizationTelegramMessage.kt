package io.github.mralex1810.fantasy.event

internal fun buildSeriesFinalizedTelegramMessage(
    tournamentName: String,
    seriesName: String,
    recipient: SeriesFinalizedRecipient,
): String {
    val lines = buildList {
        add("В турнире $tournamentName подсчитаны результаты серии $seriesName.")

        if (recipient.leagueResults.size == 1) {
            val league = recipient.leagueResults.first()
            league.winnerPublicName?.trim()?.takeIf { it.isNotEmpty() }?.let { winner ->
                add("")
                add("Победитель серии: $winner (${league.leagueName}).")
            }
            add("")
            if (league.reward > 0) {
                add("Вы заняли ${league.place}-е место из ${league.total} и получили в награду ${league.reward} фантиков.")
            } else {
                add("Вы заняли ${league.place}-е место из ${league.total}.")
            }
        } else {
            for (league in recipient.leagueResults) {
                add("")
                add("${league.leagueName} (${league.total} участников):")
                league.winnerPublicName?.trim()?.takeIf { it.isNotEmpty() }?.let { winner ->
                    add("Победитель: $winner")
                }
                if (league.reward > 0) {
                    add("Вы заняли ${league.place}-е место и получили ${league.reward} фантиков.")
                } else {
                    add("Вы заняли ${league.place}-е место.")
                }
            }
            add("")
            add("Итого за серию: +${recipient.totalReward} фантиков.")
        }

        add("")
        add("Теперь у вас ${recipient.balanceAfter} фантиков.")
    }
    return lines.joinToString("\n")
}
