package com.example.mockauth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Mock Authorization Server — port 9000.
 *
 * Stand-in for a production IdP (Okta, Auth0, Azure AD, Keycloak).
 * Uses Spring Authorization Server to issue JWTs, expose JWKS,
 * and handle the Authorization Code + PKCE flow.
 *
 * Pre-registered users: alice/alice (CUSTOMER), admin/admin (ADMIN).
 * Pre-registered client: spa-client / spa-secret (confidential).
 *
 * This is the same pattern students built in Module 3 / Lab 5.
 */
@SpringBootApplication
public class MockAuthApplication {
    public static void main(String[] args) {
        SpringApplication.run(MockAuthApplication.class, args);
    }
}
