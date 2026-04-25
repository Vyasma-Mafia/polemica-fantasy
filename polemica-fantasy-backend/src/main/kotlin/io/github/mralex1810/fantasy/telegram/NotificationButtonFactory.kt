package io.github.mralex1810.fantasy.telegram

import io.github.mralex1810.fantasy.config.AppProperties
import org.springframework.stereotype.Component

@Component
class NotificationButtonFactory(
    private val appProperties: AppProperties,
) {
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
}
