package io.github.mralex1810.fantasy.telegram

import io.github.mralex1810.fantasy.config.AppProperties
import org.springframework.stereotype.Component

@Component
class NotificationButtonFactory(
    private val appProperties: AppProperties,
) {
    fun openAppButton(): InlineKeyboardMarkup = InlineKeyboardMarkup(
        inlineKeyboard = listOf(
            listOf(
                InlineKeyboardButton.WebApp(
                    text = "Открыть игру",
                    url = appProperties.webappBaseUrl,
                ),
            ),
        ),
    )

    fun startMenu(submitTeamPath: String? = null): InlineKeyboardMarkup {
        val submitPath = submitTeamPath ?: "/"
        return InlineKeyboardMarkup(
            inlineKeyboard = listOf(
                listOf(
                    InlineKeyboardButton.WebApp(
                        text = "Открыть игру",
                        url = appProperties.webappBaseUrl,
                    ),
                    InlineKeyboardButton.WebApp(
                        text = "Как начать",
                        url = "${appProperties.webappBaseUrl}/help",
                    ),
                ),
                listOf(
                    InlineKeyboardButton.WebApp(
                        text = "Собрать команду",
                        url = "${appProperties.webappBaseUrl}$submitPath",
                    ),
                    InlineKeyboardButton.Callback(
                        text = "Написать в поддержку",
                        callbackData = SUPPORT_HELP_CALLBACK,
                    ),
                ),
            ),
        )
    }

    fun singleButton(text: String, url: String): InlineKeyboardMarkup {
        val normalized = url.trim()
        val button = if (normalized.startsWith("/")) {
            InlineKeyboardButton.WebApp(text = text, url = "${appProperties.webappBaseUrl}$normalized")
        } else if (normalized.startsWith(appProperties.webappBaseUrl)) {
            InlineKeyboardButton.WebApp(text = text, url = normalized)
        } else {
            InlineKeyboardButton.Url(text = text, url = normalized)
        }
        return InlineKeyboardMarkup(inlineKeyboard = listOf(listOf(button)))
    }

    fun submitTeamButton(seriesId: Long): InlineKeyboardMarkup = InlineKeyboardMarkup(
        inlineKeyboard = listOf(
            listOf(
                InlineKeyboardButton.WebApp(
                    text = "\uD83D\uDCDD Подать команду",
                    url = "${appProperties.webappBaseUrl}/series/$seriesId/team",
                ),
            ),
        ),
    )

    fun openSeriesLeaderboardButton(seriesId: Long): InlineKeyboardMarkup = InlineKeyboardMarkup(
        inlineKeyboard = listOf(
            listOf(
                InlineKeyboardButton.WebApp(
                    text = "\uD83C\uDFC6 Результаты серии",
                    url = "${appProperties.webappBaseUrl}/series/$seriesId/leaderboard",
                ),
            ),
        ),
    )

    fun openCardsButton(): InlineKeyboardMarkup = InlineKeyboardMarkup(
        inlineKeyboard = listOf(
            listOf(
                InlineKeyboardButton.WebApp(
                    text = "\uD83C\uDCCF Моя коллекция",
                    url = "${appProperties.webappBaseUrl}/cards",
                ),
            ),
        ),
    )

    fun openMarketplaceButton(): InlineKeyboardMarkup = InlineKeyboardMarkup(
        inlineKeyboard = listOf(
            listOf(
                InlineKeyboardButton.WebApp(
                    text = "\uD83D\uDED2 Открыть маркетплейс",
                    url = "${appProperties.webappBaseUrl}/marketplace",
                ),
            ),
        ),
    )

    fun openMarketplaceFilteredButton(fantasyPlayerId: Long): InlineKeyboardMarkup = InlineKeyboardMarkup(
        inlineKeyboard = listOf(
            listOf(
                InlineKeyboardButton.WebApp(
                    text = "\uD83D\uDED2 Посмотреть на маркетплейсе",
                    url = "${appProperties.webappBaseUrl}/marketplace?fantasyPlayerId=$fantasyPlayerId",
                ),
            ),
        ),
    )

    fun openTransactionButton(listingId: Long): InlineKeyboardMarkup = InlineKeyboardMarkup(
        inlineKeyboard = listOf(
            listOf(
                InlineKeyboardButton.WebApp(
                    text = "\uD83D\uDCCB Посмотреть сделку",
                    url = "${appProperties.webappBaseUrl}/marketplace/transactions/$listingId",
                ),
            ),
        ),
    )

    companion object {
        const val SUPPORT_HELP_CALLBACK = "support_help"
    }
}
