package io.github.mralex1810.fantasy.auth

import jakarta.servlet.http.HttpServletRequest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class UserApiRequestMatcherTest {

    private val matcher = UserApiRequestMatcher()

    @Test
    fun `matches user api path`() {
        val req = mock(HttpServletRequest::class.java)
        `when`(req.requestURI).thenReturn("/api/v1/me")
        assertTrue(matcher.matches(req))
    }

    @Test
    fun `does not match admin path`() {
        val req = mock(HttpServletRequest::class.java)
        `when`(req.requestURI).thenReturn("/api/v1/admin/tournaments")
        assertFalse(matcher.matches(req))
    }

    @Test
    fun `does not match telegram bot webhook`() {
        val req = mock(HttpServletRequest::class.java)
        `when`(req.requestURI).thenReturn("/api/v1/telegram/webhook")
        assertFalse(matcher.matches(req))
    }
}
