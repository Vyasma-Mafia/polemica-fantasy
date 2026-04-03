package io.github.mralex1810.fantasy.auth

import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.web.util.matcher.RequestMatcher

/**
 * Matches user API paths under `/api/v1/` excluding `/api/v1/admin/`
 * and Telegram Bot API callbacks under `/api/v1/telegram/` (webhook checks secret header).
 */
class UserApiRequestMatcher : RequestMatcher {
    override fun matches(request: HttpServletRequest): Boolean {
        val path = request.requestURI
        return path.startsWith("/api/v1/") &&
            !path.startsWith("/api/v1/admin/") &&
            !path.startsWith("/api/v1/telegram/")
    }
}
