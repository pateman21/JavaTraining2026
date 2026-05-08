package com.example.banking.controller;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plain unit test — HealthController has no dependencies, so we don't
 * need a Spring context to test it.
 *
 * The actual security behavior of /health (that it's permitAll and
 * reachable without a token) is covered by an integration test you'll
 * add as part of the capstone.
 */
class HealthControllerTest {

    @Test
    void health_returns_up() {
        Map<String, String> body = new HealthController().health();
        assertThat(body).containsEntry("status", "UP");
    }
}
