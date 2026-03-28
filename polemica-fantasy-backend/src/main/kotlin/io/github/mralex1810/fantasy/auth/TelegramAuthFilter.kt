package io.github.mralex1810.fantasy.auth

import io.github.mralex1810.fantasy.config.TelegramProperties
import io.github.mralex1810.fantasy.service.UserService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Registered only via [io.github.mralex1810.fantasy.config.SecurityConfig] user API chain.
 * Not a Spring `@Component` — otherwise Spring Boot registers it as a global servlet filter and breaks Basic Auth on admin API paths.
 */
class TelegramAuthFilter(
    private val telegramProperties: TelegramProperties,
    private val telegramInitDataValidator: TelegramInitDataValidator,
    private val userService: UserService,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val header = request.getHeader("Authorization")
        if (header == null || !header.startsWith(PREFIX)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing or invalid Authorization header")
            return
        }
        val initDataRaw = header.substring(PREFIX.length).trim()
        if (initDataRaw.isEmpty()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Empty initData")
            return
        }
        val token = telegramProperties.token
        if (token.isBlank()) {
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Telegram bot token is not configured")
            return
        }
        val parsed = try {
            telegramInitDataValidator.validateAndParse(initDataRaw, token)
        } catch (e: Exception) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, e.message ?: "Invalid initData")
            return
        }
        val user = userService.getOrCreateAndUpdateProfile(
            parsed.telegramUserId,
            parsed.username,
            parsed.firstName,
        )
        val auth = TelegramAuthentication(user)
        val context = SecurityContextHolder.createEmptyContext()
        context.authentication = auth
        SecurityContextHolder.setContext(context)
        try {
            filterChain.doFilter(request, response)
        } finally {
            SecurityContextHolder.clearContext()
        }
    }

    companion object {
        private const val PREFIX = "tma "
    }
}
