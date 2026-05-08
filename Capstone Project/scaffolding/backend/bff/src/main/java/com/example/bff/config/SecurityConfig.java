package com.example.bff.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

/**
 * BFF Security — OAuth2 client, session-based, CSRF enabled.
 *
 * The BFF is the only service the browser talks to. It authenticates
 * users via oauth2Login (redirecting to the auth server), stores the
 * resulting tokens in the HTTP session, and gates all /api/** requests
 * behind an authenticated session.
 *
 * For unauthenticated /api/** requests, we return 401 instead of 302.
 * This prevents CORS errors on cross-origin redirects and lets the SPA
 * handle the redirect to the login page manually.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${app.frontend-base-url:http://localhost:5173}")
    private String frontendBaseUrl;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                // Static SPA assets (prod: served from resources/static/)
                .requestMatchers("/", "/index.html", "/assets/**", "/favicon.ico").permitAll()
                // OAuth2 login/logout endpoints must be open
                .requestMatchers("/login/**", "/oauth2/**").permitAll()
                // Health check and error page must be public (avoids /error?continue redirect loop)
                .requestMatchers("/health", "/error").permitAll()
                // Everything else requires an authenticated session
                .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                // Return 401 Unauthorized for REST API requests instead of redirecting to login
                .defaultAuthenticationEntryPointFor(
                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                    new AntPathRequestMatcher("/api/**")
                )
            )
            // OAuth2 login — Spring auto-exposes /oauth2/authorization/{registrationId}
            // and /login/oauth2/code/{registrationId}. Supports both mock-auth and google.
            .oauth2Login(oauth2 -> oauth2
                .defaultSuccessUrl(frontendBaseUrl + "/", true)
                // Store the OAuth2 authorization request (state + PKCE) in a cookie instead
                // of the server-side session. This prevents state-mismatch errors caused by
                // concurrent anonymous API calls creating multiple sessions at page load.
                .authorizationEndpoint(authz -> authz
                    .authorizationRequestRepository(new CookieOAuth2AuthorizationRequestRepository())
                )
            )
            // Logout invalidates the session and clears the cookie.
            .logout(logout -> logout
                .logoutSuccessUrl(frontendBaseUrl + "/")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
            )
            // CSRF protection with cookie-based token repository.
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
            )
            // Spring Security 6 defers CSRF token loading; the XSRF-TOKEN cookie is only
            // written when something calls getToken() on the deferred token. Without this
            // filter the cookie never appears on GET requests and logout always gets 403.
            .addFilterAfter(new OncePerRequestFilter() {
                @Override
                protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
                                                FilterChain chain) throws ServletException, IOException {
                    CsrfToken token = (CsrfToken) req.getAttribute(CsrfToken.class.getName());
                    if (token != null) {
                        token.getToken(); // subscribe → forces cookie to be written
                    }
                    chain.doFilter(req, res);
                }
            }, CsrfFilter.class);

        return http.build();
    }
}
