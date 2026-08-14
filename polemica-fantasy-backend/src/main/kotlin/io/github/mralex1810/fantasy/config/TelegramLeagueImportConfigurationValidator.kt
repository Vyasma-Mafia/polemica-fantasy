package io.github.mralex1810.fantasy.config

import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Component

@Component
class TelegramLeagueImportConfigurationValidator(
    private val properties: TelegramLeagueImportProperties,
    private val telegramProperties: TelegramProperties,
    private val telegramSupportProperties: TelegramSupportProperties,
) {
    @PostConstruct
    fun validate() {
        if (!properties.enabled) return
        require(properties.sourceChannelPeerId != 0L) { "Telegram league import source channel id is required" }
        if (properties.ingestEnabled) {
            require(properties.ingestKeyId.isNotBlank() && properties.ingestCurrentSecret.isNotBlank()) {
                "Telegram league import current ingest key id/secret are required"
            }
            val previousConfigured = properties.ingestPreviousKeyId.isNotBlank() || properties.ingestPreviousSecret.isNotBlank()
            if (previousConfigured) {
                require(properties.ingestPreviousKeyId.isNotBlank() && properties.ingestPreviousSecret.isNotBlank()) {
                    "Telegram league import previous ingest key id/secret must be configured together"
                }
                require(properties.ingestPreviousKeyId != properties.ingestKeyId) {
                    "Telegram league import current and previous ingest key ids must differ"
                }
            }
        }
        if (properties.operatorNotificationsEnabled || properties.callbackEnabled) {
            require(properties.operatorChatId != 0L) { "Telegram league import operator chat id is required" }
            require(telegramProperties.token.isNotBlank()) { "Telegram bot token is required for league import operator messaging" }
            require(properties.adminBaseUrl.startsWith("https://")) { "Telegram league import admin base URL must use HTTPS" }
        }
        if (properties.callbackEnabled) {
            require(properties.callbackSigningSecret.isNotBlank()) { "Telegram league import callback signing secret is required" }
            require(telegramSupportProperties.webhookSecret.isNotBlank()) {
                "Telegram webhook secret is required for league import callbacks"
            }
        }
        val enabledPolicies = listOf(properties.policies.lp, properties.policies.zl).filter { it.enabled }
        enabledPolicies.forEach { policy ->
            require(policy.tournamentId > 0 && policy.namePrefix.isNotBlank() && policy.nameTemplate.contains("%d")) {
                "Enabled Telegram league import policy is incomplete"
            }
            require(policy.expectedGameCount in 1..32) { "League expected game count must be between 1 and 32" }
            require(policy.expectedRosterCount in 1..32) { "League expected roster count must be between 1 and 32" }
            require(policy.timezone == "Europe/Moscow") { "Telegram league import currently supports only Europe/Moscow" }
        }
        val automaticCreateConfigured = enabledPolicies.any { it.createMode == LeagueImportAutomationMode.AUTOMATIC }
        val automaticFinalizeConfigured = enabledPolicies.any { it.finalizeMode == LeagueImportAutomationMode.AUTOMATIC }
        val automaticConfigured = automaticCreateConfigured || automaticFinalizeConfigured
        if (automaticConfigured) {
            require(properties.automationCutoverAt != null) { "Automation cutover timestamp is required for automatic league import" }
            require(properties.policyGeneration.isNotBlank() && properties.policyGeneration != "disabled") {
                "A non-disabled policy generation is required for automatic league import"
            }
        }
        if (properties.productionWritesEnabled) {
            require(properties.policyGeneration.isNotBlank() && properties.policyGeneration != "disabled") {
                "A non-disabled policy generation is required for league import production writes"
            }
        }
        if (properties.rosterWritesEnabled) {
            require(properties.ocrRosterEnabled && properties.productionWritesEnabled) {
                "Telegram OCR roster writes require OCR roster processing and production writes"
            }
        }
    }
}
