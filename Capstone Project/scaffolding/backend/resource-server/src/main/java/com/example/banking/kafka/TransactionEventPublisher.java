package com.example.banking.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Publishes TransactionEvents to Kafka.
 *
 * The Kafka send is fire-and-forget with logging — call this AFTER the
 * DB transaction has committed. If the publish fails, log the failure
 * but do NOT throw: the user's transaction has already completed in the
 * database and they shouldn't see a 500 because of a downstream issue.
 *
 * The message key is the accountId so all events for the same account
 * land on the same partition (preserves per-account ordering).
 */
@Service
public class TransactionEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(TransactionEventPublisher.class);

    private final KafkaTemplate<String, TransactionEvent> kafka;
    private final String topic;

    public TransactionEventPublisher(KafkaTemplate<String, TransactionEvent> kafka,
                                     @Value("${bank.kafka.transactions-topic}") String topic) {
        this.kafka = kafka;
        this.topic = topic;
    }

    public void publish(TransactionEvent event) {
        kafka.send(topic, event.accountId(), event)
             .whenComplete((result, err) -> {
                 if (err != null) {
                     log.error("Failed to publish transactionEvent {} for account {}",
                               event.eventId(), event.accountId(), err);
                 } else {
                     log.debug("Published transactionEvent {} to {}-{}@{}",
                               event.eventId(),
                               result.getRecordMetadata().topic(),
                               result.getRecordMetadata().partition(),
                               result.getRecordMetadata().offset());
                 }
             });
    }
}
