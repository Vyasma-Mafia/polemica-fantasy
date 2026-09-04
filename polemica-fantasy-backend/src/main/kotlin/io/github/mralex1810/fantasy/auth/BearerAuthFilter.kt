package io.github.mralex1810.fantasy.auth

import io.github.mralex1810.fantasy.service.ApiCredentialAuthenticationResult
import io.github.mralex1810.fantasy.service.ApiCredentialService
import io.micrometer.core.instrument.MeterRegistry
import com.github.benmanes.caffeine.cache.Caffeine
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

/** User-chain-only Bearer authentication. This filter must never be registered as a servlet Filter bean. */
class BearerAuthFilter(
    private val apiCredentialService: ApiCredentialService,
    private val meterRegistry: MeterRegistry,
    private val failureRateLimiter: BearerAuthFailureRateLimiter = BearerAuthFailureRateLimiter(),
    private val clientAddressResolver: BearerClientAddressResolver = BearerClientAddressResolver(false),
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val header = request.getHeader("Authorization") ?: return filterChain.doFilter(request, response)
        val separator = header.indexOf(' ')
        val scheme = if (separator < 0) header else header.substring(0, separator)
        if (!scheme.equals("Bearer", ignoreCase = true)) {
            filterChain.doFilter(request, response)
            return
        }
        val token = if (separator < 0) "" else header.substring(separator + 1).trim()
        val remoteAddress = clientAddressResolver.resolve(request)
        if (failureRateLimiter.isBlocked(remoteAddress)) {
            return unauthorized(response, "RATE_LIMITED", remoteAddress)
        }
        val result = apiCredentialService.authenticate(token, request.method, request.requestURI)
        val user = when (result) {
            is ApiCredentialAuthenticationResult.Success -> result.user
            ApiCredentialAuthenticationResult.Malformed -> return unauthorized(response, "MALFORMED", remoteAddress)
            ApiCredentialAuthenticationResult.Unknown -> return unauthorized(response, "UNKNOWN", remoteAddress)
            ApiCredentialAuthenticationResult.RecognizedFailure.EXPIRED -> return unauthorized(response, "EXPIRED", remoteAddress)
            ApiCredentialAuthenticationResult.RecognizedFailure.REVOKED -> return unauthorized(response, "REVOKED", remoteAddress)
        }
        failureRateLimiter.recordSuccess(remoteAddress)
        meterRegistry.counter(METRIC, "outcome", "SUCCESS").increment()
        val context = SecurityContextHolder.createEmptyContext()
        context.authentication = TelegramAuthentication(user)
        SecurityContextHolder.setContext(context)
        try {
            filterChain.doFilter(request, response)
        } finally {
            SecurityContextHolder.clearContext()
        }
    }

    private fun unauthorized(response: HttpServletResponse, outcome: String, remoteAddress: String) {
        if (outcome != "RATE_LIMITED") failureRateLimiter.recordFailure(remoteAddress)
        meterRegistry.counter(METRIC, "outcome", outcome).increment()
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid bearer token")
    }

    private companion object {
        const val METRIC = "fantasy.api_credential.authentication"
    }
}

class BearerClientAddressResolver(
    private val trustXRealIp: Boolean,
) {
    fun resolve(request: HttpServletRequest): String {
        if (trustXRealIp) {
            request.getHeader("X-Real-IP")
                ?.trim()
                ?.takeIf { IP_LITERAL.matches(it) }
                ?.let { return it }
        }
        return request.remoteAddr?.take(64) ?: "unknown"
    }

    private companion object {
        val IP_LITERAL = Regex("^[0-9A-Fa-f:.]{2,64}$")
    }
}

class BearerAuthFailureRateLimiter(
    private val maxFailuresPerWindow: Int = 30,
) {
    private val failures = Caffeine.newBuilder()
        .maximumSize(10_000)
        .expireAfterWrite(Duration.ofMinutes(1))
        .build<String, AtomicInteger>()

    fun isBlocked(remoteAddress: String): Boolean = (failures.getIfPresent(remoteAddress)?.get() ?: 0) >= maxFailuresPerWindow

    fun recordFailure(remoteAddress: String) {
        failures.get(remoteAddress) { AtomicInteger() }.incrementAndGet()
    }

    fun recordSuccess(remoteAddress: String) {
        failures.invalidate(remoteAddress)
    }
}
