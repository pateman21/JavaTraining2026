package com.example.bff.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Proxies account endpoints to the Resource Server.
 * The WebClient automatically attaches the user's Bearer token.
 */
@RestController
@RequestMapping("/api/v1")
public class AccountsBffController {

    private final WebClient rs;

    public AccountsBffController(WebClient resourceServerWebClient) {
        this.rs = resourceServerWebClient;
    }

    @GetMapping("/accounts")
    public Mono<String> listAccounts() {
        return rs.get().uri("/api/v1/accounts")
                .retrieve().bodyToMono(String.class);
    }

    @GetMapping("/accounts/{accountId}")
    public Mono<String> getAccount(@PathVariable String accountId) {
        return rs.get().uri("/api/v1/accounts/{id}", accountId)
                .retrieve().bodyToMono(String.class);
    }

    @GetMapping("/accounts/{accountId}/transactions")
    public Mono<String> getTransactions(@PathVariable String accountId) {
        return rs.get().uri("/api/v1/accounts/{id}/transactions", accountId)
                .retrieve().bodyToMono(String.class);
    }
}
