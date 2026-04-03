package io.github.mralex1810.fantasy.controller

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.mralex1810.fantasy.config.TelegramSupportProperties
import io.github.mralex1810.fantasy.service.TelegramSupportUpdateService
import com.fasterxml.jackson.databind.JsonNode
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class TelegramWebhookControllerTest {

    private lateinit var mockMvc: org.springframework.test.web.servlet.MockMvc
    private lateinit var properties: TelegramSupportProperties
    private lateinit var updateService: TelegramSupportUpdateService

    @BeforeEach
    fun setup() {
        properties = TelegramSupportProperties()
        updateService = org.mockito.kotlin.mock<TelegramSupportUpdateService>()
        val controller = TelegramWebhookController(properties, updateService, ObjectMapper())
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build()
    }

    @Test
    fun `returns ok when support disabled without processing`() {
        properties.enabled = false
        mockMvc.perform(
            post("/api/v1/telegram/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"update_id":1}"""),
        )
            .andExpect(status().isOk)
            .andExpect(content().string("ok"))
        verify(updateService, never()).processUpdate(any<JsonNode>())
    }

    @Test
    fun `returns 503 when secret not configured`() {
        properties.enabled = true
        properties.webhookSecret = ""
        mockMvc.perform(
            post("/api/v1/telegram/webhook")
                .header(TelegramWebhookController.X_TELEGRAM_SECRET, "x")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"),
        )
            .andExpect(status().isServiceUnavailable)
    }

    @Test
    fun `returns 401 when secret mismatch`() {
        properties.enabled = true
        properties.webhookSecret = "good"
        mockMvc.perform(
            post("/api/v1/telegram/webhook")
                .header(TelegramWebhookController.X_TELEGRAM_SECRET, "bad")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"),
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `calls service when secret valid`() {
        properties.enabled = true
        properties.webhookSecret = "good"
        val body = """{"update_id":1}"""
        mockMvc.perform(
            post("/api/v1/telegram/webhook")
                .header(TelegramWebhookController.X_TELEGRAM_SECRET, "good")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body),
        )
            .andExpect(status().isOk)
        verify(updateService).processUpdate(any<JsonNode>())
    }
}
