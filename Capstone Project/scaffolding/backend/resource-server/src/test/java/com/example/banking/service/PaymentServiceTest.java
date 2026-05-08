package com.example.banking.service;

import com.example.banking.config.PaymentProcessorProperties;
import com.example.banking.exception.PaymentProcessorException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PaymentService using a mocked RestTemplate.
 *
 * Covers: API key header forwarded, 5xx error → exception, network timeout → exception.
 */
@SuppressWarnings("unchecked")
class PaymentServiceTest {

    private RestTemplate http;
    private PaymentService svc;

    @BeforeEach
    void setUp() {
        http = mock(RestTemplate.class);
        PaymentProcessorProperties props =
                new PaymentProcessorProperties("http://localhost:8089", "test-secret-key", 3000, 3000);
        svc = new PaymentService(http, props);
    }

    // ------------------------------------------------------------------ happy path

    @Test
    void successful_call_does_not_throw() {
        when(http.exchange(eq("/payments"), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(org.springframework.http.ResponseEntity.ok("{}"));

        svc.submitExternalTransfer("acc_1", "ext_acc", new BigDecimal("100.00"), "USD", "idem-123");

        verify(http).exchange(eq("/payments"), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
    }

    // ------------------------------------------------------------------ API key forwarded in header

    @Test
    void api_key_is_sent_in_x_processor_key_header() {
        ArgumentCaptor<HttpEntity<Object>> captor = ArgumentCaptor.forClass((Class) HttpEntity.class);
        when(http.exchange(eq("/payments"), eq(HttpMethod.POST), captor.capture(), eq(String.class)))
                .thenReturn(org.springframework.http.ResponseEntity.ok("{}"));

        svc.submitExternalTransfer("acc_1", "ext_acc", new BigDecimal("50.00"), "USD", "idem-456");

        assertThat(captor.getValue().getHeaders().getFirst("X-Processor-Key"))
                .isEqualTo("test-secret-key");
    }

    // ------------------------------------------------------------------ 5xx → PaymentProcessorException

    @Test
    void http_5xx_from_processor_throws_payment_processor_exception() {
        when(http.exchange(eq("/payments"), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() ->
                svc.submitExternalTransfer("acc_1", "ext_acc", new BigDecimal("75.00"), "USD", "idem-789"))
                .isInstanceOf(PaymentProcessorException.class);
    }

    // ------------------------------------------------------------------ timeout → PaymentProcessorException

    @Test
    void network_timeout_throws_payment_processor_exception() {
        when(http.exchange(eq("/payments"), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new ResourceAccessException("Read timed out",
                        new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() ->
                svc.submitExternalTransfer("acc_1", "ext_acc", new BigDecimal("200.00"), "USD", "idem-000"))
                .isInstanceOf(PaymentProcessorException.class);
    }

    // ------------------------------------------------------------------ idempotency header forwarded

    @Test
    void submit_external_transfer_calls_processor_with_idempotency_header() {
        /*
         * TODO (Chapter 06 Part 2 — Step 1): Verify that the idempotency key is forwarded
         * to the payment processor in the X-Idempotency-Key request header.
         *
         * Setup:
         *   ArgumentCaptor<HttpEntity<Object>> captor = ArgumentCaptor.forClass((Class) HttpEntity.class);
         *   stub http.exchange("/payments", POST, captor.capture(), String.class) to return 200 OK
         *
         * Exercise:
         *   svc.submitExternalTransfer("acc_1", "ext_acc", new BigDecimal("50.00"), "USD", "idem-key-1");
         *
         * Verify:
         *   assertThat(captor.getValue().getHeaders().getFirst("X-Idempotency-Key"))
         *       .isEqualTo("idem-key-1");
         */
        // TODO: implement this test
        throw new UnsupportedOperationException("test not yet implemented");
    }

    // ------------------------------------------------------------------ 5xx → PaymentProcessorException (part 2)

    @Test
    void submit_external_transfer_5xx_throws_payment_processor_exception() {
        /*
         * TODO (Chapter 06 Part 2 — Step 2): Verify that any 5xx response from the
         * payment processor is wrapped in a PaymentProcessorException.
         *
         * Setup:
         *   stub http.exchange("/payments", POST, any(), String.class) to throw
         *   new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR)
         *
         * Verify:
         *   assertThatThrownBy(() -> svc.submitExternalTransfer(...))
         *       .isInstanceOf(PaymentProcessorException.class);
         */
        // TODO: implement this test
        throw new UnsupportedOperationException("test not yet implemented");
    }
}
