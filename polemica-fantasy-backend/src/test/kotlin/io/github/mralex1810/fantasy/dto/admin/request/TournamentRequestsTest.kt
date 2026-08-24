package io.github.mralex1810.fantasy.dto.admin.request

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import jakarta.validation.Validation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TournamentRequestsTest {
    private val objectMapper = ObjectMapper().registerKotlinModule()
    private val validator = Validation.buildDefaultValidatorFactory().validator

    @Test
    fun `update default expected game count distinguishes omitted null and value`() {
        val omitted = objectMapper.readValue("{}", UpdateTournamentRequest::class.java)
        assertFalse(omitted.defaultExpectedGameCountSpecified)
        assertNull(omitted.defaultExpectedGameCount)

        val cleared = objectMapper.readValue(
            """{"defaultExpectedGameCount":null}""",
            UpdateTournamentRequest::class.java,
        )
        assertTrue(cleared.defaultExpectedGameCountSpecified)
        assertNull(cleared.defaultExpectedGameCount)

        val configured = objectMapper.readValue(
            """{"defaultExpectedGameCount":12}""",
            UpdateTournamentRequest::class.java,
        )
        assertTrue(configured.defaultExpectedGameCountSpecified)
        assertEquals(12, configured.defaultExpectedGameCount)
    }

    @Test
    fun `tournament default expected game count uses series bounds`() {
        val minimum = objectMapper.readValue(
            """{"defaultExpectedGameCount":1}""",
            UpdateTournamentRequest::class.java,
        )
        val maximum = objectMapper.readValue(
            """{"defaultExpectedGameCount":1000}""",
            UpdateTournamentRequest::class.java,
        )
        val below = objectMapper.readValue(
            """{"defaultExpectedGameCount":0}""",
            UpdateTournamentRequest::class.java,
        )
        val above = objectMapper.readValue(
            """{"defaultExpectedGameCount":1001}""",
            UpdateTournamentRequest::class.java,
        )

        assertTrue(validator.validate(minimum).isEmpty())
        assertTrue(validator.validate(maximum).isEmpty())
        assertEquals(
            "defaultExpectedGameCount",
            validator.validate(below).single().propertyPath.toString(),
        )
        assertEquals(
            "defaultExpectedGameCount",
            validator.validate(above).single().propertyPath.toString(),
        )
    }
}
