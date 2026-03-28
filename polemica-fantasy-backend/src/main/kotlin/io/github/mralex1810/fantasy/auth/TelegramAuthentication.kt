package io.github.mralex1810.fantasy.auth

import io.github.mralex1810.fantasy.entity.TelegramUser
import org.springframework.security.core.Authentication
import org.springframework.security.core.GrantedAuthority

class TelegramAuthentication(
    private val telegramUser: TelegramUser,
) : Authentication {
    override fun getAuthorities(): Collection<GrantedAuthority> = emptyList()
    override fun getCredentials(): Any? = null
    override fun getDetails(): Any? = null
    override fun getPrincipal(): TelegramUser = telegramUser
    override fun isAuthenticated(): Boolean = true
    override fun setAuthenticated(isAuthenticated: Boolean) {
        require(isAuthenticated) { "Cannot set authenticated to false" }
    }
    override fun getName(): String = telegramUser.telegramId.toString()
}
