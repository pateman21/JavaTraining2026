package com.example.banking.config;

import com.example.banking.security.JwtAuthConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Resource Server security — stateless JWT validation.
 *
 * No sessions, no CSRF, no CORS. The Resource Server only receives
 * requests from the BFF (server-to-server), with a Bearer JWT attached
 * by the BFF's WebClient OAuth2 filter.
 *
 * Two-layer RBAC:
 *   1. URL filter: /api/v1/admin/** requires ROLE_ADMIN
 *   2. Method security: @PreAuthorize on controller methods (enabled)
 *
 * JwtAuthConverter maps the JWT sub claim to a local BANK_USERS row
 * and assigns the appropriate role from the token's custom "role" claim.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthConverter jwtAuthConverter) throws Exception {
        http
            // No CORS needed — only the BFF talks to us, server-to-server.
            .cors(cors -> cors.disable())
            // No CSRF needed — stateless JWT, no cookies.
            .csrf(csrf -> csrf.disable())
            // Stateless — no HTTP session, no JSESSIONID cookie.
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/health").permitAll()
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter))
            );

        return http.build();
    }
}
