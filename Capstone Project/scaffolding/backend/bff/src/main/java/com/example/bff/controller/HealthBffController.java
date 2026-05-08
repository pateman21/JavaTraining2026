package com.example.bff.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Proxies health check to the Resource Server.
 * Also returns the BFF's own health.
 */
@RestController
public class HealthBffController {

    private final WebClient rs;

    public HealthBffController(WebClient resourceServerWebClient) {
        this.rs = resourceServerWebClient;
    }

    @GetMapping("/health")
    public Mono<String> health() {
        return rs.get().uri("/health")
                .retrieve().bodyToMono(String.class);
    }
}
