package com.example.bff;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Backend-for-Frontend — port 8080.
 *
 * This is the OAuth2 client. It drives the Authorization Code + PKCE flow
 * against the mock auth server (or Google), stores tokens server-side in
 * the HTTP session, and proxies API calls to the Resource Server using
 * WebClient with an OAuth2 filter that attaches the Bearer token.
 *
 * The browser only ever talks to this service, via session cookies.
 */
@SpringBootApplication
public class BffApplication {
    public static void main(String[] args) {
        SpringApplication.run(BffApplication.class, args);
    }
}
