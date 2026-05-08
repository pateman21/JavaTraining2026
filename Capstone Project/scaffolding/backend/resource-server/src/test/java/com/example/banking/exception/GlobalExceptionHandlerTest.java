package com.example.banking.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for GlobalExceptionHandler.
 *
 * Verifies every exception mapping produces the right HTTP status and
 * the RFC 7807-style envelope keys (timestamp, status, title, code, detail).
 * No Spring context needed — instantiate the handler directly.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/test");

    private void assertEnvelope(Map<String, Object> body, HttpStatus expected, String expectedCode) {
        assertThat(body).containsKey("timestamp");
        assertThat(body.get("status")).isEqualTo(expected.value());
        assertThat(body.get("title")).isEqualTo(expected.getReasonPhrase());
        assertThat(body.get("code")).isEqualTo(expectedCode);
        assertThat(body).containsKey("detail");
    }

    // ------------------------------------------------------------------ ResourceNotFoundException → 404

    @Test
    void resource_not_found_returns_404_not_found() {
        var res = handler.onNotFound(new ResourceNotFoundException("account", "acc_1"), req);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertEnvelope(res.getBody(), HttpStatus.NOT_FOUND, "NOT_FOUND");
    }

    // ------------------------------------------------------------------ InsufficientFundsException → 422

    @Test
    void insufficient_funds_returns_422() {
        var ex = new InsufficientFundsException("acc_1", new java.math.BigDecimal("10"), new java.math.BigDecimal("50"));
        var res = handler.onInsufficientFunds(ex, req);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertEnvelope(res.getBody(), HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_FUNDS");
    }

    // ------------------------------------------------------------------ BusinessRuleException → 422

    @Test
    void business_rule_violation_returns_422() {
        var res = handler.onBusinessRule(new BusinessRuleException("TRANSFER_IN is system-only"), req);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertEnvelope(res.getBody(), HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE_VIOLATION");
    }

    // ------------------------------------------------------------------ PaymentProcessorException → 502

    @Test
    void payment_processor_error_returns_502_without_leaking_upstream_details() {
        var res = handler.onPaymentProcessor(new PaymentProcessorException("upstream 503"), req);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertEnvelope(res.getBody(), HttpStatus.BAD_GATEWAY, "PAYMENT_PROCESSOR_ERROR");
        // Must not echo the upstream error message to the caller
        assertThat(res.getBody().get("detail").toString())
                .doesNotContain("upstream 503");
    }

    // ------------------------------------------------------------------ AccessDeniedException → 403

    @Test
    void access_denied_returns_403() {
        var res = handler.onAccessDenied(
                new org.springframework.security.access.AccessDeniedException("Forbidden"), req);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertEnvelope(res.getBody(), HttpStatus.FORBIDDEN, "FORBIDDEN");
    }

    // ------------------------------------------------------------------ Unhandled exception → 500

    @Test
    void unhandled_exception_returns_500_without_stack_trace() {
        var res = handler.onUnhandled(new RuntimeException("NullPointerException in service"), req);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertEnvelope(res.getBody(), HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR");
        // Must not expose internal detail to caller
        assertThat(res.getBody().get("detail").toString())
                .doesNotContain("NullPointerException");
    }
}
