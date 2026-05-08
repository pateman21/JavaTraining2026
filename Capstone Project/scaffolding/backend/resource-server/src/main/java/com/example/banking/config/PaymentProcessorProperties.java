package com.example.banking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bound to the bank.payment-processor.* tree in application.yml.
 *
 * Read by PaymentService. The api-key MUST come from an env var in any
 * non-dev environment — never commit a real key.
 */
@ConfigurationProperties(prefix = "bank.payment-processor")
public record PaymentProcessorProperties(
        String baseUrl,
        String apiKey,
        int connectTimeoutMs,
        int readTimeoutMs
) { }
