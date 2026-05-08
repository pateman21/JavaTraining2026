package com.example.banking.service;

import com.example.banking.config.PaymentProcessorProperties;
import com.example.banking.exception.PaymentProcessorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Calls the downstream Payment Processor for external transfers.
 *
 * Treat the API key as a secret: it is loaded via PaymentProcessorProperties
 * from an env var. Never log it. Never put it in an exception message.
 *
 * On any failure the caller should mark the transaction FAILED and NOT
 * debit the source account. The exception → 502 mapping lives in
 * GlobalExceptionHandler.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final RestTemplate http;
    private final PaymentProcessorProperties props;

    public PaymentService(RestTemplate paymentProcessorRestTemplate,
                          PaymentProcessorProperties props) {
        this.http = paymentProcessorRestTemplate;
        this.props = props;
    }

    /**
     * Submits a payment to the external processor. Returns when the call
     * succeeds; throws PaymentProcessorException on any non-2xx, timeout,
     * or network error.
     */
    public void submitExternalTransfer(String sourceAccountId,
                                       String counterparty,
                                       BigDecimal amount,
                                       String currency,
                                       String idempotencyKey) {

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Processor-Key", props.apiKey());
        headers.set("Idempotency-Key", idempotencyKey);

        Map<String, Object> body = Map.of(
                "sourceAccount", sourceAccountId,
                "destinationAccount", counterparty,
                "amount", amount,
                "currency", currency
        );

        try {
            http.exchange("/payments",
                    org.springframework.http.HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    String.class);
        } catch (RestClientException e) {
            // Log message only — never log the API key or the request body
            // (which contains account numbers).
            log.warn("Payment processor call failed: {}", e.getClass().getSimpleName());
            throw new PaymentProcessorException("Payment processor unavailable", e);
        }
    }
}
