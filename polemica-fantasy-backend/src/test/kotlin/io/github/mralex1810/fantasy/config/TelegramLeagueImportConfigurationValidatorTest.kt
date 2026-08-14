package io.github.mralex1810.fantasy.config

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Instant

class TelegramLeagueImportConfigurationValidatorTest {
    @Test
    fun `automatic policy can stay armed while global execution gates are closed`() {
        val properties = automaticProperties().also {
            it.productionWritesEnabled = false
            it.operatorNotificationsEnabled = false
            it.ingestEnabled = false
        }

        assertDoesNotThrow { validator(properties).validate() }
    }

    @Test
    fun `automatic finalize can be disabled by the independent result processing gate`() {
        val properties = automaticProperties().also {
            it.policies.lp.createMode = LeagueImportAutomationMode.DISABLED
            it.policies.lp.finalizeMode = LeagueImportAutomationMode.AUTOMATIC
            it.resultProcessingEnabled = false
        }

        assertDoesNotThrow { validator(properties).validate() }
    }

    @Test
    fun `roster writes require both OCR processing and global production writes`() {
        val properties = automaticProperties().also {
            it.rosterWritesEnabled = true
            it.ocrRosterEnabled = false
        }
        assertThrows(IllegalArgumentException::class.java) { validator(properties).validate() }

        properties.ocrRosterEnabled = true
        properties.productionWritesEnabled = false
        assertThrows(IllegalArgumentException::class.java) { validator(properties).validate() }

        properties.productionWritesEnabled = true
        assertDoesNotThrow { validator(properties).validate() }
    }

    @Test
    fun `OCR alias sources must be nonblank and normalization-unique`() {
        val properties = automaticProperties()
        properties.policies.lp.rosterNicknameAliases = linkedMapOf(" Градиент " to "Gradient", "градиент" to "Another")
        assertThrows(IllegalArgumentException::class.java) { validator(properties).validate() }

        properties.policies.lp.rosterNicknameAliases = linkedMapOf("Градиент" to "")
        assertThrows(IllegalArgumentException::class.java) { validator(properties).validate() }

        properties.policies.lp.rosterNicknameAliases = linkedMapOf("Градиент" to "Gradient")
        assertDoesNotThrow { validator(properties).validate() }
    }

    private fun automaticProperties() = TelegramLeagueImportProperties(
        enabled = true,
        ingestEnabled = true,
        operatorNotificationsEnabled = true,
        productionWritesEnabled = true,
        sourceChannelPeerId = -100123,
        operatorChatId = -100456,
        ingestKeyId = "current",
        ingestCurrentSecret = "secret",
        automationCutoverAt = Instant.parse("2026-08-11T00:00:00Z"),
        policyGeneration = "generation-1",
    ).also {
        it.policies.lp = TelegramLeagueImportProperties.LeaguePolicy(
            enabled = true,
            tournamentId = 1,
            nameTemplate = "ЛП %d",
            namePrefix = "Лига Претендентов",
            createMode = LeagueImportAutomationMode.AUTOMATIC,
        )
    }

    private fun validator(properties: TelegramLeagueImportProperties) =
        TelegramLeagueImportConfigurationValidator(
            properties,
            TelegramProperties(token = "bot-token"),
            TelegramSupportProperties(),
        )
}
