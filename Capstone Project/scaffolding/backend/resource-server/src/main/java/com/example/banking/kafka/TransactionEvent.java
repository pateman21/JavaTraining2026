package com.example.banking.kafka;

import com.example.banking.model.TransactionEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Payload published to Kafka when a transaction completes (or fails).
 *
 * eventId is generated at publish time and acts as the consumer-side
 * idempotency key.
 */
public record TransactionEvent(
        String eventId,
        String transactionId,
        String accountId,
        String ownerId,
        String type,
        BigDecimal amount,
        String currency,
        String status,
        String counterparty,
        String transferGroupId,
        Instant occurredAt
) {
    public static TransactionEvent from(TransactionEntity tx, String ownerId, String currency) {
        return new TransactionEvent(
                "evt_" + UUID.randomUUID(),
                tx.getTransactionId(),
                tx.getAccountId(),
                ownerId,
                tx.getType().name(),
                tx.getAmount(),
                currency,
                tx.getStatus().name(),
                tx.getCounterparty(),
                tx.getTransferGroupId(),
                Instant.now()
        );
    }
}
