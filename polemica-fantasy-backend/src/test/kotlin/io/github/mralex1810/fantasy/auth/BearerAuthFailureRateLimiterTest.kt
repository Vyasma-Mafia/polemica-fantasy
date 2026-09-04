package io.github.mralex1810.fantasy.auth

import jakarta.servlet.http.HttpServletRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class BearerAuthFailureRateLimiterTest {
    @Test
    fun `client address ignores spoofed forwarded-for and trusts x-real-ip only when configured`() {
        val request = mock<HttpServletRequest>()
        whenever(request.remoteAddr).thenReturn("172.18.0.1")
        whenever(request.getHeader("X-Forwarded-For")).thenReturn("198.51.100.7, 203.0.113.10")
        whenever(request.getHeader("X-Real-IP")).thenReturn("203.0.113.10")

        assertThat(BearerClientAddressResolver(false).resolve(request)).isEqualTo("172.18.0.1")
        assertThat(BearerClientAddressResolver(true).resolve(request)).isEqualTo("203.0.113.10")
    }

    @Test
    fun `blocks repeated failures and resets after success`() {
        val limiter = BearerAuthFailureRateLimiter(maxFailuresPerWindow = 2)

        assertThat(limiter.isBlocked("127.0.0.1")).isFalse()
        limiter.recordFailure("127.0.0.1")
        assertThat(limiter.isBlocked("127.0.0.1")).isFalse()
        limiter.recordFailure("127.0.0.1")
        assertThat(limiter.isBlocked("127.0.0.1")).isTrue()

        limiter.recordSuccess("127.0.0.1")
        assertThat(limiter.isBlocked("127.0.0.1")).isFalse()
    }
}
