package io.github.mralex1810.fantasy.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Instant

enum class LeagueImportAutomationMode { DISABLED, MANUAL, AUTOMATIC }

@ConfigurationProperties(prefix = "telegram.league-import")
data class TelegramLeagueImportProperties(
    var enabled: Boolean = false,
    var ingestEnabled: Boolean = false,
    var operatorNotificationsEnabled: Boolean = false,
    var callbackEnabled: Boolean = false,
    var resultProcessingEnabled: Boolean = false,
    var productionWritesEnabled: Boolean = false,
    var sourceChannelPeerId: Long = 0,
    var operatorChatId: Long = 0,
    var ingestKeyId: String = "current",
    var ingestCurrentSecret: String = "",
    var ingestPreviousKeyId: String = "",
    var ingestPreviousSecret: String = "",
    var callbackSigningSecret: String = "",
    var adminBaseUrl: String = "https://admin.fantasy.maftourbot.ru",
    var ingestClockSkewSeconds: Long = 300,
    var previewTtlSeconds: Long = 3600,
    var confirmationTtlSeconds: Long = 300,
    var automationCutoverAt: Instant? = null,
    var policyGeneration: String = "disabled",
    var policies: Policies = Policies(),
) {
    data class Policies(
        var lp: LeaguePolicy = LeaguePolicy(),
        var zl: LeaguePolicy = LeaguePolicy(),
    )

    data class LeaguePolicy(
        var enabled: Boolean = false,
        var tournamentId: Long = 0,
        var nameTemplate: String = "",
        var namePrefix: String = "",
        var timezone: String = "Europe/Moscow",
        var teamDeadlineOffsetMinutes: Long = 10,
        var expectedGameCount: Int = 5,
        var createMode: LeagueImportAutomationMode = LeagueImportAutomationMode.DISABLED,
        var finalizeMode: LeagueImportAutomationMode = LeagueImportAutomationMode.DISABLED,
    )

    fun policy(league: String): LeaguePolicy? = when (league.uppercase()) {
        "ЛП", "LP" -> policies.lp
        "ЗЛ", "ZL" -> policies.zl
        else -> null
    }?.takeIf { it.enabled }
}
