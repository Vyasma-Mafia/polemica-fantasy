package io.github.mralex1810.fantasy.config

import io.github.mralex1810.fantasy.auth.TelegramAuthFilter
import io.github.mralex1810.fantasy.auth.TelegramInitDataValidator
import io.github.mralex1810.fantasy.auth.UserApiRequestMatcher
import io.github.mralex1810.fantasy.service.UserService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
@EnableWebSecurity
class SecurityConfig {

    @Bean
    fun adminUserDetailsService(adminProperties: AdminProperties): UserDetailsService {
        val user = User.builder()
            .username(adminProperties.username)
            .password("{noop}${adminProperties.password}")
            .roles("ADMIN")
            .build()
        return InMemoryUserDetailsManager(user)
    }

    @Bean
    @Order(1)
    fun adminSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .securityMatcher("/api/v1/admin/**")
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth -> auth.anyRequest().authenticated() }
            .httpBasic { }
        return http.build()
    }

    @Bean
    @Order(2)
    fun userApiSecurityFilterChain(
        http: HttpSecurity,
        telegramProperties: TelegramProperties,
        telegramInitDataValidator: TelegramInitDataValidator,
        userService: UserService,
    ): SecurityFilterChain {
        // Not a @Bean — Spring Boot auto-registers Filter beans as global servlet filters (breaks admin Basic Auth).
        val telegramAuthFilter = TelegramAuthFilter(telegramProperties, telegramInitDataValidator, userService)
        http
            .securityMatcher(UserApiRequestMatcher())
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .addFilterBefore(telegramAuthFilter, UsernamePasswordAuthenticationFilter::class.java)
            .authorizeHttpRequests { auth -> auth.anyRequest().authenticated() }
        return http.build()
    }

    @Bean
    @Order(3)
    fun defaultSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .authorizeHttpRequests { auth -> auth.anyRequest().permitAll() }
        return http.build()
    }
}
