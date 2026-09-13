package com.erykszczesniak.synchub.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.security.web.SecurityFilterChain

/**
 * Security model of the hub.
 *
 * - Read-only status endpoints (GET under `/api/status`) are open: the React dashboard consumes them
 *   without credentials and they expose no secrets.
 * - Everything that changes state (trigger sync, backfill, resolve drift, replay quarantine) requires
 *   HTTP Basic with the single operator account configured in [SecurityProperties].
 * - Sessions are stateless and CSRF is disabled because the API is called by scripts and the dashboard,
 *   never by browser forms.
 */
@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val properties: SecurityProperties,
) {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it
                    .requestMatchers("/actuator/health/**", "/actuator/info")
                    .permitAll()
                    .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/status/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated()
            }.httpBasic(Customizer.withDefaults())
            .build()

    @Bean
    fun passwordEncoder(): PasswordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder()

    @Bean
    fun userDetailsService(passwordEncoder: PasswordEncoder): UserDetailsService =
        InMemoryUserDetailsManager(
            User
                .withUsername(properties.admin.username)
                .password(passwordEncoder.encode(properties.admin.password))
                .roles("OPERATOR")
                .build(),
        )
}
