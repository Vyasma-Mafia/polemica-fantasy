package io.github.mralex1810.fantasy.telegram

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.web.client.RestClient
import org.springframework.web.client.ResourceAccessException

class TelegramBotApiClientTest {

    @Test
    fun `chat not found is classified as permanently unavailable recipient`() {
        val fixture = fixture(
            status = HttpStatus.BAD_REQUEST,
            responseBody = """{"ok":false,"error_code":400,"description":"Bad Request: chat not found"}""",
        )

        val result = fixture.client.sendMessageSafe(
            botToken = "token",
            chatId = 123L,
            text = "hello",
        )

        assertEquals(
            TelegramSendResult.BotBlocked("Bad Request: chat not found"),
            result,
        )
        fixture.server.verify()
    }

    @Test
    fun `other bad requests remain delivery errors`() {
        val fixture = fixture(
            status = HttpStatus.BAD_REQUEST,
            responseBody = """{"ok":false,"error_code":400,"description":"Bad Request: message text is empty"}""",
        )

        val result = fixture.client.sendMessageSafe(
            botToken = "token",
            chatId = 123L,
            text = "hello",
        )

        assertEquals(
            TelegramSendResult.OtherError(400, "Bad Request: message text is empty"),
            result,
        )
        fixture.server.verify()
    }

    @Test
    fun `transport exception never retains bot token in message cause or stack trace`() {
        val secret = "123456:super-secret-token"
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        server.expect(requestTo("https://api.telegram.org/bot$secret/sendMessage"))
            .andExpect(method(HttpMethod.POST))
            .andRespond {
                throw ResourceAccessException(
                    "I/O error on POST request for https://api.telegram.org/bot$secret/sendMessage",
                )
            }
        val client = TelegramBotApiClient(builder.build(), ObjectMapper())

        val error = assertThrows(IllegalStateException::class.java) {
            client.sendMessage(secret, 123L, "hello")
        }

        assertFalse(error.stackTraceToString().contains(secret))
        assertNull(error.cause)
        server.verify()
    }

    private fun fixture(status: HttpStatus, responseBody: String): Fixture {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        server.expect(requestTo("https://api.telegram.org/bottoken/sendMessage"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(
                withStatus(status)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(responseBody),
            )
        return Fixture(
            client = TelegramBotApiClient(builder.build(), ObjectMapper()),
            server = server,
        )
    }

    private data class Fixture(
        val client: TelegramBotApiClient,
        val server: MockRestServiceServer,
    )
}
