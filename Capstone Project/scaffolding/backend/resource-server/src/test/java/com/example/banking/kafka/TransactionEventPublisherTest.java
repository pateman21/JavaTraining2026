package com.example.banking.kafka;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
class TransactionEventPublisherTest {

    private final KafkaTemplate<String, TransactionEvent> kafka = mock(KafkaTemplate.class);
    private static final String TOPIC = "transactions.completed";
    private final TransactionEventPublisher publisher = new TransactionEventPublisher(kafka, TOPIC);

    private TransactionEvent event(String accountId) {
        return new TransactionEvent(
                "evt_test", "txn_1", accountId, "usr_1",
                "DEPOSIT", new BigDecimal("50.00"), "USD",
                "COMPLETED", null, null, Instant.now());
    }

    // ------------------------------------------------------------------ correct topic and key

    @Test
    void publish_sends_to_configured_topic_with_account_id_as_key() {
        CompletableFuture<SendResult<String, TransactionEvent>> future = new CompletableFuture<>();
        future.complete(mock(SendResult.class));
        when(kafka.send(eq(TOPIC), eq("acc_1"), any(TransactionEvent.class)))
                .thenReturn(future);

        publisher.publish(event("acc_1"));

        verify(kafka).send(eq(TOPIC), eq("acc_1"), any(TransactionEvent.class));
    }

    // ------------------------------------------------------------------ correct payload

    @Test
    void publish_sends_full_event_payload() {
        ArgumentCaptor<TransactionEvent> captor = ArgumentCaptor.forClass(TransactionEvent.class);
        CompletableFuture<SendResult<String, TransactionEvent>> future = new CompletableFuture<>();
        future.complete(mock(SendResult.class));
        when(kafka.send(eq(TOPIC), any(), captor.capture())).thenReturn(future);

        TransactionEvent original = event("acc_2");
        publisher.publish(original);

        TransactionEvent sent = captor.getValue();
        assertThat(sent.eventId()).isEqualTo("evt_test");
        assertThat(sent.accountId()).isEqualTo("acc_2");
        assertThat(sent.type()).isEqualTo("DEPOSIT");
        assertThat(sent.status()).isEqualTo("COMPLETED");
        assertThat(sent.amount()).isEqualByComparingTo("50.00");
    }

    // ------------------------------------------------------------------ Kafka failure does not throw

    @Test
    void kafka_send_failure_logs_and_does_not_throw() {
        CompletableFuture<SendResult<String, TransactionEvent>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Broker unavailable"));
        when(kafka.send(any(), any(), any())).thenReturn(failedFuture);

        // Must not throw — the DB transaction has already committed
        publisher.publish(event("acc_3"));
    }
}
