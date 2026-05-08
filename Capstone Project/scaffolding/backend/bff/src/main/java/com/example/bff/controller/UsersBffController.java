package com.example.bff.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Proxies user endpoints to the Resource Server.
 */
@RestController
@RequestMapping("/api/v1")
public class UsersBffController {

    private final WebClient rs;

    public UsersBffController(WebClient resourceServerWebClient) {
        this.rs = resourceServerWebClient;
    }

    @GetMapping("/users/me")
    public Mono<String> me() {
        return rs.get().uri("/api/v1/users/me")
                .retrieve().bodyToMono(String.class);
    }

    @GetMapping("/admin/users")
    public Mono<String> listAllUsers() {
        return rs.get().uri("/api/v1/admin/users")
                .retrieve().bodyToMono(String.class);
    }
}
