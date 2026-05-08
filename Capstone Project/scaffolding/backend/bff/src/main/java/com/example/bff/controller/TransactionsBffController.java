package com.example.bff.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Proxies transaction endpoints to the Resource Server.
 */
@RestController
@RequestMapping("/api/v1")
public class TransactionsBffController {

    private final WebClient rs;

    public TransactionsBffController(WebClient resourceServerWebClient) {
        this.rs = resourceServerWebClient;
    }

    @PostMapping("/transactions")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<String> submitTransaction(@RequestBody String body) {
        return rs.post().uri("/api/v1/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class);
    }

    @GetMapping("/transactions/{transactionId}")
    public Mono<String> getTransaction(@PathVariable String transactionId) {
        return rs.get().uri("/api/v1/transactions/{id}", transactionId)
                .retrieve().bodyToMono(String.class);
    }
}
