package io.github.mralex1810.fantasy.config

import io.github.mralex1810.fantasy.auth.BearerAuthFilter
import io.github.mralex1810.fantasy.auth.BearerClientAddressResolver
import io.github.mralex1810.fantasy.auth.TelegramAuthFilter
import io.github.mralex1810.fantasy.auth.TelegramInitDataValidator
import io.github.mralex1810.fantasy.auth.UserApiRequestMatcher
import io.github.mralex1810.fantasy.service.UserService
import io.github.mralex1810.fantasy.service.ApiCredentialService
import io.micrometer.core.instrument.MeterRegistry
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
        val admin = User.builder()
            .username(adminProperties.username)
            .password("{noop}${adminProperties.password}")
            .roles("ADMIN")
            .build()
        val users = mutableListOf(admin)
        if (adminProperties.moderatorUsername.isNotBlank() && adminProperties.moderatorPassword.isNotBlank()) {
            users += User.builder()
                .username(adminProperties.moderatorUsername)
                .password("{noop}${adminProperties.moderatorPassword}")
                .roles("MODERATOR")
                .build()
        }
        return InMemoryUserDetailsManager(users)
    }

    @Bean
    @Order(1)
    fun adminSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .securityMatcher("/api/v1/admin/**")
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers(
                        "/api/v1/admin/me",
                        "/api/v1/admin/tournaments",
                        "/api/v1/admin/tournaments/**",
                        "/api/v1/admin/series/**",
                        "/api/v1/admin/leagues",
                        "/api/v1/admin/leagues/**",
                        "/api/v1/admin/polemica/**",
                    ).hasAnyAuthority("ROLE_ADMIN", "ROLE_MODERATOR")
                    .anyRequest().hasAuthority("ROLE_ADMIN")
            }
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
        apiCredentialService: ApiCredentialService,
        meterRegistry: MeterRegistry,
        apiCredentialProperties: ApiCredentialProperties,
    ): SecurityFilterChain {
        // Not a @Bean — Spring Boot auto-registers Filter beans as global servlet filters (breaks admin Basic Auth).
        val telegramAuthFilter = TelegramAuthFilter(telegramProperties, telegramInitDataValidator, userService)
        val bearerAuthFilter = BearerAuthFilter(
            apiCredentialService,
            meterRegistry,
            clientAddressResolver = BearerClientAddressResolver(apiCredentialProperties.trustXRealIp),
        )
        http
            .securityMatcher(UserApiRequestMatcher())
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .addFilterBefore(bearerAuthFilter, UsernamePasswordAuthenticationFilter::class.java)
            .addFilterAfter(telegramAuthFilter, BearerAuthFilter::class.java)
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
