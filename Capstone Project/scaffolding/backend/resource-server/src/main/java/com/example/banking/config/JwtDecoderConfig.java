package com.example.banking.config;

import com.nimbusds.jwt.JWTParser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.*;

import java.util.List;

/**
 * Multi-issuer JwtDecoder for the Resource Server.
 *
 * Accepts signed JWTs from two issuers:
 *   1. mock-auth (http://localhost:9000) — OIDC id_token forwarded by the BFF for
 *      users who logged in via mock-auth. Audience is validated to "bank-client-bff".
 *      The token includes a custom "role" claim set by mock-auth's TokenCustomizer.
 *   2. Google (https://accounts.google.com) — OIDC id_token forwarded by the BFF
 *      for users who logged in via Google. No audience enforcement (Google's audience
 *      is the OAuth2 client ID, not "spa-client"). Google users get ROLE_CUSTOMER.
 *
 * The "iss" claim is read without signature verification only to select the right
 * decoder; the selected decoder then fully validates the JWT (signature, expiry, issuer).
 */
@Configuration
public class JwtDecoderConfig {

    private static final String GOOGLE_ISSUER = "https://accounts.google.com";

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String mockAuthIssuerUri;

    @Bean
    public JwtDecoder jwtDecoder() {
        // mock-auth decoder: validates signature + issuer + timestamps + audience
        NimbusJwtDecoder mockAuthDecoder = JwtDecoders.fromIssuerLocation(mockAuthIssuerUri);
        OAuth2TokenValidator<Jwt> audienceValidator = token -> {
            List<String> audience = token.getAudience();
            if (audience != null && audience.contains("bank-client-bff")) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token",
                            "Token audience does not include bank-client-bff", null));
        };
        mockAuthDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(mockAuthIssuerUri), audienceValidator));

        // Google decoder: validates signature + issuer + timestamps (no spa-client audience)
        JwtDecoder googleDecoder = JwtDecoders.fromIssuerLocation(GOOGLE_ISSUER);

        return token -> {
            try {
                String issuer = JWTParser.parse(token).getJWTClaimsSet().getIssuer();
                if (GOOGLE_ISSUER.equals(issuer)) {
                    return googleDecoder.decode(token);
                }
            } catch (Exception ignored) {
                // fall through to mock-auth decoder
            }
            return mockAuthDecoder.decode(token);
        };
    }
}

